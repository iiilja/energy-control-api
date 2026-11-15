package com.ilja.smarthome.energycontrol.service;

import com.ilja.smarthome.energycontrol.domain.model.HeatingSetpointSchedule;
import com.ilja.smarthome.energycontrol.domain.model.HeatingSetpointSchedule.Source;
import com.ilja.smarthome.energycontrol.domain.model.NordpoolPrice;
import com.ilja.smarthome.energycontrol.dto.heating.PricePlanResponse;
import com.ilja.smarthome.energycontrol.repository.HeatingSetpointScheduleRepository;
import com.ilja.smarthome.energycontrol.repository.NordpoolPriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.ilja.smarthome.energycontrol.service.PriceOptimizationService.PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link PriceOptimizationService}: which slots get a planned setpoint, relative to the weekly
 * template, that manual and already started slots are left alone, and the view for the UI.
 */
@ExtendWith(MockitoExtension.class)
class PriceOptimizationServiceTest {

    private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
    /** Monday 16 Nov 2026; Tallinn is UTC+2. */
    private static final LocalDate DAY = LocalDate.of(2026, 11, 16);
    /** The evening before DAY: every slot of DAY is in the future. */
    private static final Instant EVENING_BEFORE = Instant.parse("2026-11-15T18:00:00Z");

    @Mock private NordpoolPriceRepository priceRepository;
    @Mock private HeatingSetpointScheduleRepository scheduleRepository;
    @Mock private HeatingSetpointService setpointService;
    @Mock private ConfigurationService configService;

    @Captor private ArgumentCaptor<List<HeatingSetpointSchedule>> saved;
    @Captor private ArgumentCaptor<java.util.Collection<Long>> deletedIds;
    private long nextRowId = 1000;

    private final Map<String, String> config = new HashMap<>();
    /** 50 EUR/MWh all day, 100 from 18:00 to 18:45: a peak, compensated 17:00-17:45 (tie -> before). */
    private final List<NordpoolPrice> prices = dayWithEveningPeak();
    private final List<HeatingSetpointSchedule> stored = new ArrayList<>();
    private Function<Integer, String> template = hour -> "21.0";

    @BeforeEach
    void setUp() {
        config.put("enabled", "true");
        config.put("peak_threshold", "10");
        config.put("lower", "1.5");
        config.put("raise", "1.0");
        config.put("compensation_minutes", "60");
        lenient().when(configService.getConfigValue(anyString(), anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            return key.startsWith(PREFIX) ? config.getOrDefault(key.substring(PREFIX.length()), inv.getArgument(1))
                    : inv.getArgument(1);
        });
        lenient().when(setpointService.templateSetpointsAt(anyList())).thenAnswer(inv -> {
            List<Instant> instants = inv.getArgument(0);
            return instants.stream()
                    .map(t -> new BigDecimal(template.apply(t.atZone(TALLINN).getHour())))
                    .toList();
        });
        lenient().when(priceRepository.findInPeriod(any(), any())).thenReturn(prices);
        lenient().when(scheduleRepository.findInPeriod(any(), any())).thenReturn(stored);
    }

    private PriceOptimizationService serviceAt(Instant now) {
        return new PriceOptimizationService(priceRepository, scheduleRepository, setpointService, configService,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @Nested
    @DisplayName("planning")
    class Planning {

        @Test
        @DisplayName("peak slots get template - lower, the compensation slots template + raise")
        void peakAndCompensation() {
            int changed = serviceAt(EVENING_BEFORE).optimize(DAY);

            verify(scheduleRepository).saveAll(saved.capture());
            Map<String, BigDecimal> targets = byTime(saved.getValue());
            assertThat(targets).containsOnlyKeys("17:00", "17:15", "17:30", "17:45",
                    "18:00", "18:15", "18:30", "18:45");
            assertThat(targets.get("17:00")).isEqualByComparingTo("22.0");
            assertThat(targets.get("18:45")).isEqualByComparingTo("19.5");
            assertThat(saved.getValue()).allMatch(s -> s.getSource() == Source.PRICE);
            assertThat(changed).isEqualTo(8);
        }

        @Test
        @DisplayName("shifts follow each slot's template and stay within 15-30 °C")
        void clamped() {
            template = hour -> hour == 17 ? "29.5" : hour == 18 ? "15.5" : "21.0";

            serviceAt(EVENING_BEFORE).optimize(DAY);

            verify(scheduleRepository).saveAll(saved.capture());
            Map<String, BigDecimal> targets = byTime(saved.getValue());
            assertThat(targets.get("17:15")).isEqualByComparingTo("30");
            assertThat(targets.get("18:15")).isEqualByComparingTo("15");
        }

        @Test
        @DisplayName("a template outside 15-30 °C is never moved the wrong way")
        void templateOutsideRange() {
            template = hour -> hour == 17 ? "31.0" : hour == 18 ? "14.0" : "21.0";

            serviceAt(EVENING_BEFORE).optimize(DAY);

            verify(scheduleRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("slots that have started and slots set by hand are left alone")
        void startedAndManualSlotsUntouched() {
            Instant at1720 = DAY.atTime(17, 20).atZone(TALLINN).toInstant();
            HeatingSetpointSchedule pastPlanned = row(slot(17, 0), "22.0", Source.PRICE);
            HeatingSetpointSchedule manual = row(slot(17, 30), "25.0", Source.MANUAL);
            HeatingSetpointSchedule stale = row(slot(20, 0), "19.0", Source.PRICE);
            stored.addAll(List.of(pastPlanned, manual, stale));

            serviceAt(at1720).optimize(DAY);

            verify(scheduleRepository).deleteByIdInAndSource(deletedIds.capture(), org.mockito.ArgumentMatchers.eq(Source.PRICE));
            assertThat(deletedIds.getValue()).containsExactly(stale.getId());
            verify(scheduleRepository).saveAll(saved.capture());
            // 17:00 and 17:15 have started, 17:30 is manual
            assertThat(byTime(saved.getValue())).containsOnlyKeys("17:45", "18:00", "18:15", "18:30", "18:45");
        }

        @Test
        @DisplayName("nothing is written when the stored plan is already right")
        void unchangedPlan() {
            PriceOptimizationService service = serviceAt(EVENING_BEFORE);
            service.optimize(DAY);
            verify(scheduleRepository).saveAll(saved.capture());
            stored.addAll(saved.getValue());

            int changed = service.optimize(DAY);

            assertThat(changed).isZero();
            verify(scheduleRepository, never()).deleteByIdInAndSource(any(), any()); // nothing stored before the first run
            verify(scheduleRepository).saveAll(any()); // still only once
        }

        @Test
        @DisplayName("an incomplete day is not planned: the median needs the whole day")
        void incompleteDay() {
            prices.subList(50, prices.size()).clear();

            assertThat(serviceAt(EVENING_BEFORE).optimize(DAY)).isZero();
            verify(scheduleRepository, never()).saveAll(any());
        }
    }

    @Nested
    @DisplayName("switched off or misconfigured")
    class Off {

        @Test
        @DisplayName("switching off removes its future slots, keeps started and manual ones")
        void offRemovesOwnFutureSlots() {
            config.put("enabled", "false");
            Instant at1720 = DAY.atTime(17, 20).atZone(TALLINN).toInstant();
            HeatingSetpointSchedule pastPlanned = row(slot(17, 0), "22.0", Source.PRICE);
            HeatingSetpointSchedule manual = row(slot(19, 0), "25.0", Source.MANUAL);
            HeatingSetpointSchedule futurePlanned = row(slot(18, 0), "19.5", Source.PRICE);
            stored.addAll(List.of(pastPlanned, manual, futurePlanned));

            int changed = serviceAt(at1720).optimize(DAY);

            verify(scheduleRepository).deleteByIdInAndSource(deletedIds.capture(), org.mockito.ArgumentMatchers.eq(Source.PRICE));
            assertThat(deletedIds.getValue()).containsExactly(futurePlanned.getId());
            verify(scheduleRepository).saveAll(saved.capture());
            assertThat(saved.getValue()).isEmpty();
            assertThat(changed).isEqualTo(1);
        }

        @Test
        @DisplayName("off with nothing planned: no writes at all")
        void offAndNothingStored() {
            config.put("enabled", "false");

            assertThat(serviceAt(EVENING_BEFORE).optimize(DAY)).isZero();
            verify(scheduleRepository, never()).saveAll(any());
            verify(scheduleRepository, never()).deleteByIdInAndSource(any(), any());
        }

        @Test
        @DisplayName("invalid or out-of-range settings switch it off instead of guessing")
        void invalidSettings() {
            PriceOptimizationService service = serviceAt(EVENING_BEFORE);

            config.put("enabled", "yes");
            assertThat(service.readSettings().enabled()).isFalse();
            config.put("enabled", "true");
            config.put("raise", "abc");
            assertThat(service.readSettings().enabled()).isFalse();
            config.put("raise", "5");
            assertThat(service.readSettings().enabled()).isFalse();
            config.put("raise", "1");
            config.put("compensation_minutes", "-15");
            assertThat(service.readSettings().enabled()).isFalse();
            config.put("compensation_minutes", "50"); // not a multiple of 15
            assertThat(service.readSettings().enabled()).isFalse();
            config.put("compensation_minutes", "60");
            config.put("peak_threshold", "0"); // would make half of every day a peak
            assertThat(service.readSettings()).satisfies(s -> {
                assertThat(s.enabled()).isFalse();
                assertThat(s.valid()).isFalse();
            });
            config.put("peak_threshold", "10");
            config.put("compensation_minutes", " 30 ");
            config.put("enabled", " TRUE ");
            assertThat(service.readSettings()).satisfies(s -> {
                assertThat(s.enabled()).isTrue();
                assertThat(s.compensationSlots()).isEqualTo(2);
            });
        }
    }

    @Nested
    @DisplayName("view for the UI")
    class View {

        @Test
        @DisplayName("median, threshold, kinds and the three setpoints of every slot")
        void describe() {
            config.put("enabled", "false"); // the view shows kinds while it is off, too
            stored.add(row(slot(18, 0), "19.5", Source.PRICE));
            stored.add(row(slot(9, 0), "23.0", Source.MANUAL));

            PricePlanResponse view = serviceAt(EVENING_BEFORE).describe(DAY);

            assertThat(view.enabled()).isFalse();
            assertThat(view.settingsValid()).isTrue();
            assertThat(view.complete()).isTrue();
            assertThat(view.median()).isEqualByComparingTo("50");
            assertThat(view.threshold()).isEqualByComparingTo("60");
            assertThat(view.slots()).hasSize(96);
            assertThat(view.slots().get(0).timestamp()).isEqualTo("2026-11-16T00:00+02:00");

            PricePlanResponse.Slot peak = slotAt(view, "18:00");
            assertThat(peak.kind()).isEqualTo("PEAK");
            assertThat(peak.templateSetpoint()).isEqualByComparingTo("21.0");
            assertThat(peak.optimizedSetpoint()).isEqualByComparingTo("19.5");
            assertThat(peak.scheduledSetpoint()).isEqualByComparingTo("19.5");
            assertThat(peak.source()).isEqualTo("PRICE");

            assertThat(slotAt(view, "17:30").kind()).isEqualTo("COMPENSATION");
            assertThat(slotAt(view, "17:30").optimizedSetpoint()).isEqualByComparingTo("22.0");
            assertThat(slotAt(view, "17:30").source()).isEqualTo("TEMPLATE");
            assertThat(slotAt(view, "09:00").scheduledSetpoint()).isEqualByComparingTo("23.0");
            assertThat(slotAt(view, "09:00").source()).isEqualTo("MANUAL");
            assertThat(slotAt(view, "12:00").kind()).isEqualTo("NORMAL");
        }

        @Test
        @DisplayName("an incomplete day shows its prices without median or peaks")
        void incompleteDay() {
            prices.subList(50, prices.size()).clear();

            PricePlanResponse view = serviceAt(EVENING_BEFORE).describe(DAY);

            assertThat(view.complete()).isFalse();
            assertThat(view.median()).isNull();
            assertThat(view.slots()).hasSize(50).allMatch(s -> s.kind().equals("NORMAL"));
        }
    }

    // ---------------------------------------------------------------------------------------------

    private static List<NordpoolPrice> dayWithEveningPeak() {
        double[] day = PricePeakPlannerTest.flatDay(50);
        PricePeakPlannerTest.fill(day, 72, 76, 100);
        return new ArrayList<>(PricePeakPlannerTest.prices(day));
    }

    private NordpoolPrice slot(int hour, int minute) {
        return prices.stream()
                .filter(p -> p.getPriceTimestamp().withZoneSameInstant(TALLINN).getHour() == hour
                        && p.getPriceTimestamp().withZoneSameInstant(TALLINN).getMinute() == minute)
                .findFirst().orElseThrow();
    }

    private HeatingSetpointSchedule row(NordpoolPrice price, String target, Source source) {
        HeatingSetpointSchedule row = new HeatingSetpointSchedule(price, new BigDecimal(target), source);
        row.setId(nextRowId++);
        return row;
    }

    private static Map<String, BigDecimal> byTime(List<HeatingSetpointSchedule> rows) {
        Map<String, BigDecimal> byTime = new HashMap<>();
        for (HeatingSetpointSchedule row : rows) {
            byTime.put(row.getNordpoolPrice().getPriceTimestamp().withZoneSameInstant(TALLINN).toLocalTime().toString(),
                    row.getTargetSetpoint());
        }
        return byTime;
    }

    private static PricePlanResponse.Slot slotAt(PricePlanResponse view, String time) {
        return view.slots().stream().filter(s -> s.timestamp().substring(11, 16).equals(time)).findFirst().orElseThrow();
    }
}
