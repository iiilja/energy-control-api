package com.ilja.smarthome.energycontrol.service;

import com.ilja.smarthome.energycontrol.domain.model.DefaultWeeklySchedule;
import com.ilja.smarthome.energycontrol.domain.model.HeatingSetpointSchedule;
import com.ilja.smarthome.energycontrol.domain.model.NordpoolPrice;
import com.ilja.smarthome.energycontrol.repository.DefaultWeeklyScheduleRepository;
import com.ilja.smarthome.energycontrol.repository.HeatingSetpointScheduleRepository;
import com.ilja.smarthome.energycontrol.repository.NordpoolPriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Date/time behaviour of {@link HeatingSetpointService#applyWeeklyTemplateToDate(LocalDate)}:
 * template times are Tallinn wall-clock times, entries apply from their start time inclusive,
 * and DST transition days are handled slot by slot.
 */
@ExtendWith(MockitoExtension.class)
class HeatingSetpointServiceTest {

    private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

    @Mock private HeatingSetpointScheduleRepository scheduleRepository;
    @Mock private NordpoolPriceRepository nordpoolPriceRepository;
    @Mock private DefaultWeeklyScheduleRepository weeklyScheduleRepository;
    @Mock private ConfigurationService configService;

    @Captor private ArgumentCaptor<List<HeatingSetpointSchedule>> savedCaptor;

    private HeatingSetpointService service;

    @BeforeEach
    void setUp() {
        service = new HeatingSetpointService(
                scheduleRepository, nordpoolPriceRepository, weeklyScheduleRepository, configService);

        lenient().when(configService.getConfigValue("heating.setpoint.default", "21.0")).thenReturn("21.0");
        lenient().when(scheduleRepository.findByNordpoolPrice(any())).thenReturn(Optional.empty());
        lenient().when(scheduleRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Template times are Tallinn local time even when DB timestamps come back in UTC")
    void templateUsesTallinnTimeForUtcTimestamps() {
        LocalDate monday = LocalDate.of(2026, 10, 19); // EEST, UTC+3
        givenPricesFor(monday, ZoneOffset.UTC);
        givenTemplate(1, "00:00", "20.0", "05:00", "22.0", "08:00", "21.0");

        int created = service.applyWeeklyTemplateToDate(monday);

        assertThat(created).isEqualTo(96);
        List<HeatingSetpointSchedule> saved = savedSchedules();
        assertThat(setpointAt(saved, monday, "00:00")).isEqualByComparingTo("20.0");
        assertThat(setpointAt(saved, monday, "04:45")).isEqualByComparingTo("20.0");
        assertThat(setpointAt(saved, monday, "05:00")).isEqualByComparingTo("22.0");
        assertThat(setpointAt(saved, monday, "07:45")).isEqualByComparingTo("22.0");
        assertThat(setpointAt(saved, monday, "08:00")).isEqualByComparingTo("21.0");
        assertThat(setpointAt(saved, monday, "23:45")).isEqualByComparingTo("21.0");
    }

    @Test
    @DisplayName("An entry applies from its start time inclusive (slot 05:00 gets the 05:00 setpoint)")
    void entryAppliesFromItsStartSlot() {
        LocalDate tuesday = LocalDate.of(2026, 10, 20);
        givenPricesFor(tuesday, TALLINN);
        givenTemplate(2, "00:00", "20.0", "05:00", "22.0");

        service.applyWeeklyTemplateToDate(tuesday);

        List<HeatingSetpointSchedule> saved = savedSchedules();
        assertThat(setpointAt(saved, tuesday, "00:00")).isEqualByComparingTo("20.0");
        assertThat(setpointAt(saved, tuesday, "04:45")).isEqualByComparingTo("20.0");
        assertThat(setpointAt(saved, tuesday, "05:00")).isEqualByComparingTo("22.0");
    }

    @Test
    @DisplayName("Slots before the first template entry fall back to the configured default")
    void slotsBeforeFirstEntryUseDefault() {
        LocalDate wednesday = LocalDate.of(2026, 10, 21);
        givenPricesFor(wednesday, TALLINN);
        givenTemplate(3, "06:00", "22.0");

        service.applyWeeklyTemplateToDate(wednesday);

        List<HeatingSetpointSchedule> saved = savedSchedules();
        assertThat(setpointAt(saved, wednesday, "00:00")).isEqualByComparingTo("21.0");
        assertThat(setpointAt(saved, wednesday, "05:45")).isEqualByComparingTo("21.0");
        assertThat(setpointAt(saved, wednesday, "06:00")).isEqualByComparingTo("22.0");
    }

    @Test
    @DisplayName("DST end (25 h, 100 slots): both repeated 03:xx hours get the night setpoint")
    void dstEndDayHas100SlotsMappedCorrectly() {
        LocalDate dstEnd = LocalDate.of(2026, 10, 25); // Sunday, clocks go 04:00 EEST -> 03:00 EET
        givenPricesFor(dstEnd, ZoneOffset.UTC);
        givenTemplate(7, "00:00", "20.0", "05:00", "22.0");

        int created = service.applyWeeklyTemplateToDate(dstEnd);

        assertThat(created).isEqualTo(100);
        List<HeatingSetpointSchedule> saved = savedSchedules();
        List<BigDecimal> nightSetpoints = saved.stream()
                .filter(s -> tallinnTime(s).isBefore(LocalTime.of(5, 0)))
                .map(HeatingSetpointSchedule::getTargetSetpoint)
                .toList();
        assertThat(nightSetpoints).hasSize(24); // 00:00-05:00 is 5 wall-clock hours but 6 real hours
        assertThat(nightSetpoints).allSatisfy(s -> assertThat(s).isEqualByComparingTo("20.0"));
        assertThat(setpointAt(saved, dstEnd, "05:00")).isEqualByComparingTo("22.0");
    }

    @Test
    @DisplayName("DST start (23 h, 92 slots): the skipped 03:xx hour does not break the mapping")
    void dstStartDayHas92SlotsMappedCorrectly() {
        LocalDate dstStart = LocalDate.of(2026, 3, 29); // Sunday, clocks go 03:00 EET -> 04:00 EEST
        givenPricesFor(dstStart, ZoneOffset.UTC);
        givenTemplate(7, "00:00", "20.0", "05:00", "22.0");

        int created = service.applyWeeklyTemplateToDate(dstStart);

        assertThat(created).isEqualTo(92);
        List<HeatingSetpointSchedule> saved = savedSchedules();
        assertThat(setpointAt(saved, dstStart, "02:45")).isEqualByComparingTo("20.0");
        assertThat(setpointAt(saved, dstStart, "04:00")).isEqualByComparingTo("20.0");
        assertThat(setpointAt(saved, dstStart, "05:00")).isEqualByComparingTo("22.0");
    }

    @Test
    @DisplayName("Prices are queried for the Tallinn day [00:00, next 00:00)")
    void queriesTallinnDayBounds() {
        LocalDate day = LocalDate.of(2026, 10, 25);
        ArgumentCaptor<ZonedDateTime> start = ArgumentCaptor.forClass(ZonedDateTime.class);
        ArgumentCaptor<ZonedDateTime> end = ArgumentCaptor.forClass(ZonedDateTime.class);
        when(nordpoolPriceRepository.findByPriceTimestampBetweenOrderByPriceTimestampAsc(start.capture(), end.capture()))
                .thenReturn(List.of());

        service.applyWeeklyTemplateToDate(day);

        assertThat(start.getValue().toInstant()).isEqualTo(ZonedDateTime.parse("2026-10-24T21:00:00Z").toInstant());
        assertThat(end.getValue().toInstant()).isEqualTo(ZonedDateTime.parse("2026-10-25T22:00:00Z").toInstant());
    }

    // ------------------------------------------------------------------------

    /** All 15-minute slots of a Tallinn day, with timestamps expressed in {@code storageZone}. */
    private void givenPricesFor(LocalDate day, ZoneId storageZone) {
        ZonedDateTime slot = day.atStartOfDay(TALLINN);
        ZonedDateTime end = day.plusDays(1).atStartOfDay(TALLINN);
        List<NordpoolPrice> prices = new ArrayList<>();
        while (slot.isBefore(end)) {
            prices.add(new NordpoolPrice(slot.withZoneSameInstant(storageZone), new BigDecimal("50.0")));
            slot = slot.plusMinutes(15);
        }
        when(nordpoolPriceRepository.findByPriceTimestampBetweenOrderByPriceTimestampAsc(any(), any()))
                .thenReturn(prices);
    }

    /** Template for one day of week: pairs of "HH:mm", "setpoint". */
    private void givenTemplate(int dayOfWeek, String... timeSetpointPairs) {
        List<DefaultWeeklySchedule> entries = new ArrayList<>();
        for (int i = 0; i < timeSetpointPairs.length; i += 2) {
            entries.add(new DefaultWeeklySchedule((short) dayOfWeek,
                    LocalTime.parse(timeSetpointPairs[i]), new BigDecimal(timeSetpointPairs[i + 1])));
        }
        when(weeklyScheduleRepository.findByDayOfWeekOrderByStartTimeAsc((short) dayOfWeek)).thenReturn(entries);
    }

    private List<HeatingSetpointSchedule> savedSchedules() {
        verify(scheduleRepository).saveAll(savedCaptor.capture());
        return savedCaptor.getValue();
    }

    /** Setpoint of the first slot on {@code day} whose Tallinn wall-clock time is {@code hhmm}. */
    private static BigDecimal setpointAt(List<HeatingSetpointSchedule> saved, LocalDate day, String hhmm) {
        LocalTime time = LocalTime.parse(hhmm);
        return saved.stream()
                .filter(s -> s.getNordpoolPrice().getPriceTimestamp().withZoneSameInstant(TALLINN).toLocalDate().equals(day))
                .filter(s -> tallinnTime(s).equals(time))
                .findFirst()
                .map(HeatingSetpointSchedule::getTargetSetpoint)
                .orElseThrow(() -> new AssertionError("No slot at " + hhmm + " on " + day));
    }

    private static LocalTime tallinnTime(HeatingSetpointSchedule s) {
        return s.getNordpoolPrice().getPriceTimestamp().withZoneSameInstant(TALLINN).toLocalTime();
    }
}
