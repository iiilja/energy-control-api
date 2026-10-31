package com.ilja.smarthome.energycontrol.service;

import com.ilja.smarthome.energycontrol.domain.model.DefaultWeeklySchedule;
import com.ilja.smarthome.energycontrol.domain.model.HeatingSetpointSchedule;
import com.ilja.smarthome.energycontrol.domain.model.NordpoolPrice;
import com.ilja.smarthome.energycontrol.dto.heating.SetpointScheduleItemRequest;
import com.ilja.smarthome.energycontrol.dto.heating.SetpointScheduleItemResponse;
import com.ilja.smarthome.energycontrol.dto.heating.WeeklyScheduleEntryRequest;
import com.ilja.smarthome.energycontrol.exception.InvalidValueException;
import com.ilja.smarthome.energycontrol.repository.DefaultWeeklyScheduleRepository;
import com.ilja.smarthome.energycontrol.repository.HeatingSetpointScheduleRepository;
import com.ilja.smarthome.energycontrol.repository.NordpoolPriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link HeatingSetpointService}: the scheduled setpoint (slot override, otherwise weekly template,
 * otherwise default) in Tallinn wall-clock time, including DST days, and weekly template editing.
 */
@ExtendWith(MockitoExtension.class)
class HeatingSetpointServiceTest {

    private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

    @Mock private HeatingSetpointScheduleRepository scheduleRepository;
    @Mock private NordpoolPriceRepository nordpoolPriceRepository;
    @Mock private DefaultWeeklyScheduleRepository weeklyScheduleRepository;
    @Mock private ConfigurationService configService;

    private HeatingSetpointService service;

    @BeforeEach
    void setUp() {
        service = new HeatingSetpointService(
                scheduleRepository, nordpoolPriceRepository, weeklyScheduleRepository, configService);
        lenient().when(configService.getConfigValue("heating.setpoint.default", "21.0")).thenReturn("21.0");
    }

    @Nested
    @DisplayName("scheduledSetpointAt (used by the control loop every 15 minutes)")
    class ScheduledSetpointAt {

        @ParameterizedTest(name = "{0} -> {1} °C")
        @CsvSource({
                // Monday 26 Oct 2026, Tallinn = UTC+2 (EET). Template: 00:00 20, 05:00 22, 08:00 21
                "2026-10-25T22:00:00Z, 20.0",   // 00:00 Tallinn
                "2026-10-26T02:59:59Z, 20.0",   // 04:59:59 Tallinn
                "2026-10-26T03:00:00Z, 22.0",   // 05:00 Tallinn: entry applies from its start, inclusive
                "2026-10-26T05:59:59Z, 22.0",   // 07:59:59 Tallinn
                "2026-10-26T06:00:00Z, 21.0",   // 08:00 Tallinn
                "2026-10-26T21:59:59Z, 21.0",   // 23:59:59 Tallinn
        })
        void usesWeeklyTemplateInTallinnTime(Instant instant, String expected) {
            givenTemplate(1, "00:00", "20.0", "05:00", "22.0", "08:00", "21.0");

            assertThat(service.scheduledSetpointAt(instant)).isEqualByComparingTo(expected);
        }

        @Test
        @DisplayName("works without any prices or schedule rows (heating no longer depends on Elering)")
        void worksWithoutPrices() {
            givenTemplate(1, "00:00", "20.0", "05:00", "22.0");
            // scheduleRepository.findInPeriod returns an empty list by default

            assertThat(service.scheduledSetpointAt(Instant.parse("2026-10-26T04:00:00Z"))).isEqualByComparingTo("22.0");
        }

        @Test
        @DisplayName("a price-slot override wins over the template")
        void slotOverrideWins() {
            Instant at = Instant.parse("2026-10-26T03:07:00Z"); // 05:07 Tallinn, slot 05:00-05:15
            HeatingSetpointSchedule override = new HeatingSetpointSchedule(
                    price("2026-10-26T05:00+02:00[Europe/Tallinn]"), new BigDecimal("23.5"));
            when(scheduleRepository.findInPeriod(any(), any())).thenReturn(List.of(override));

            assertThat(service.scheduledSetpointAt(at)).isEqualByComparingTo("23.5");
            verify(weeklyScheduleRepository, never()).findByDayOfWeekOrderByStartTimeAsc(any());
        }

        @Test
        @DisplayName("the slot override is looked up for the 15-minute slot containing the instant")
        void looksUpTheContainingSlot() {
            givenTemplate(1, "00:00", "20.0");
            ArgumentCaptor<ZonedDateTime> start = ArgumentCaptor.forClass(ZonedDateTime.class);
            ArgumentCaptor<ZonedDateTime> end = ArgumentCaptor.forClass(ZonedDateTime.class);
            when(scheduleRepository.findInPeriod(start.capture(), end.capture())).thenReturn(List.of());

            service.scheduledSetpointAt(Instant.parse("2026-10-26T03:29:59Z")); // 05:29:59 Tallinn

            assertThat(start.getValue().toInstant()).isEqualTo(Instant.parse("2026-10-26T03:15:00Z"));
            assertThat(end.getValue().toInstant()).isEqualTo(Instant.parse("2026-10-26T03:30:00Z"));
        }

        @Test
        @DisplayName("DST end: both 03:xx hours of 25 Oct use the Sunday night setpoint")
        void dstEndRepeatedHour() {
            givenTemplate(7, "00:00", "20.0", "05:00", "22.0");

            // 03:30 EEST (00:30Z) and 03:30 EET (01:30Z) are both 03:30 wall-clock time
            assertThat(service.scheduledSetpointAt(Instant.parse("2026-10-25T00:30:00Z"))).isEqualByComparingTo("20.0");
            assertThat(service.scheduledSetpointAt(Instant.parse("2026-10-25T01:30:00Z"))).isEqualByComparingTo("20.0");
            // 05:00 EET
            assertThat(service.scheduledSetpointAt(Instant.parse("2026-10-25T03:00:00Z"))).isEqualByComparingTo("22.0");
        }

        @Test
        @DisplayName("before the first template entry of the day the configured default applies")
        void defaultBeforeFirstEntry() {
            givenTemplate(3, "06:00", "22.0");

            // Wednesday 28 Oct 05:00 Tallinn
            assertThat(service.scheduledSetpointAt(Instant.parse("2026-10-28T03:00:00Z"))).isEqualByComparingTo("21.0");
        }
    }

    @Nested
    @DisplayName("getScheduleForDate (what the UI shows)")
    class ScheduleForDate {

        @Test
        @DisplayName("template times are Tallinn local time even when DB timestamps come back in UTC")
        void templateUsesTallinnTimeForUtcTimestamps() {
            LocalDate monday = LocalDate.of(2026, 10, 19); // EEST, UTC+3
            givenPricesFor(monday, ZoneOffset.UTC);
            givenTemplate(1, "00:00", "20.0", "05:00", "22.0", "08:00", "21.0");

            List<SetpointScheduleItemResponse> items = service.getScheduleForDate(monday);

            assertThat(items).hasSize(96);
            assertThat(setpointAt(items, "00:00")).isEqualByComparingTo("20.0");
            assertThat(setpointAt(items, "04:45")).isEqualByComparingTo("20.0");
            assertThat(setpointAt(items, "05:00")).isEqualByComparingTo("22.0");
            assertThat(setpointAt(items, "07:45")).isEqualByComparingTo("22.0");
            assertThat(setpointAt(items, "08:00")).isEqualByComparingTo("21.0");
            assertThat(setpointAt(items, "23:45")).isEqualByComparingTo("21.0");
        }

        @Test
        @DisplayName("DST end day: 100 slots, both repeated 03:xx hours get the night setpoint")
        void dstEndDay() {
            LocalDate dstEnd = LocalDate.of(2026, 10, 25);
            givenPricesFor(dstEnd, ZoneOffset.UTC);
            givenTemplate(7, "00:00", "20.0", "05:00", "22.0");

            List<SetpointScheduleItemResponse> items = service.getScheduleForDate(dstEnd);

            assertThat(items).hasSize(100);
            List<BigDecimal> night = items.stream()
                    .filter(i -> tallinnTime(i).isBefore(LocalTime.of(5, 0)))
                    .map(SetpointScheduleItemResponse::getSetpoint)
                    .toList();
            assertThat(night).hasSize(24).allSatisfy(s -> assertThat(s).isEqualByComparingTo("20.0"));
            assertThat(setpointAt(items, "05:00")).isEqualByComparingTo("22.0");
        }

        @Test
        @DisplayName("DST start day: 92 slots, the skipped 03:xx hour does not break the mapping")
        void dstStartDay() {
            LocalDate dstStart = LocalDate.of(2026, 3, 29);
            givenPricesFor(dstStart, ZoneOffset.UTC);
            givenTemplate(7, "00:00", "20.0", "05:00", "22.0");

            List<SetpointScheduleItemResponse> items = service.getScheduleForDate(dstStart);

            assertThat(items).hasSize(92);
            assertThat(setpointAt(items, "02:45")).isEqualByComparingTo("20.0");
            assertThat(setpointAt(items, "04:00")).isEqualByComparingTo("20.0");
            assertThat(setpointAt(items, "05:00")).isEqualByComparingTo("22.0");
        }

        @Test
        @DisplayName("a slot override replaces the template value for that slot only")
        void slotOverrideShown() {
            LocalDate monday = LocalDate.of(2026, 10, 19);
            List<NordpoolPrice> prices = givenPricesFor(monday, TALLINN);
            givenTemplate(1, "00:00", "20.0");
            NordpoolPrice slot0300 = prices.get(12); // 03:00
            when(scheduleRepository.findInPeriod(any(), any()))
                    .thenReturn(List.of(new HeatingSetpointSchedule(slot0300, new BigDecimal("23.0"))));

            List<SetpointScheduleItemResponse> items = service.getScheduleForDate(monday);

            assertThat(setpointAt(items, "02:45")).isEqualByComparingTo("20.0");
            assertThat(setpointAt(items, "03:00")).isEqualByComparingTo("23.0");
            assertThat(setpointAt(items, "03:15")).isEqualByComparingTo("20.0");
        }

        @Test
        @DisplayName("prices are queried for the Tallinn day [00:00, next 00:00)")
        void queriesTallinnDayBounds() {
            ArgumentCaptor<ZonedDateTime> start = ArgumentCaptor.forClass(ZonedDateTime.class);
            ArgumentCaptor<ZonedDateTime> end = ArgumentCaptor.forClass(ZonedDateTime.class);
            when(nordpoolPriceRepository.findInPeriod(start.capture(), end.capture())).thenReturn(List.of());

            service.getScheduleForDate(LocalDate.of(2026, 10, 25));

            assertThat(start.getValue().toInstant()).isEqualTo(Instant.parse("2026-10-24T21:00:00Z"));
            assertThat(end.getValue().toInstant()).isEqualTo(Instant.parse("2026-10-25T22:00:00Z"));
        }
    }

    @Nested
    @DisplayName("weekly template and slot overrides")
    class Editing {

        @Test
        @DisplayName("duplicate day + time ('05:00' vs '05:00:00') is rejected before anything is deleted")
        void weeklyTemplateRejectsDuplicates() {
            List<WeeklyScheduleEntryRequest> entries = List.of(
                    new WeeklyScheduleEntryRequest((short) 1, "05:00", new BigDecimal("22.0")),
                    new WeeklyScheduleEntryRequest((short) 1, "05:00:00", new BigDecimal("21.0")));

            assertThatThrownBy(() -> service.saveWeeklySchedule(entries))
                    .isInstanceOf(InvalidValueException.class)
                    .hasMessageContaining("day 1 at 05:00");

            verify(weeklyScheduleRepository, never()).deleteAllInBatch();
            verify(weeklyScheduleRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("old rows are bulk-deleted before the new ones are inserted")
        void weeklyTemplateDeletesInBatchThenSaves() {
            service.saveWeeklySchedule(List.of(
                    new WeeklyScheduleEntryRequest((short) 1, "05:00", new BigDecimal("22.0")),
                    new WeeklyScheduleEntryRequest((short) 2, "05:00", new BigDecimal("22.0"))));

            InOrder order = inOrder(weeklyScheduleRepository);
            order.verify(weeklyScheduleRepository).deleteAllInBatch();
            order.verify(weeklyScheduleRepository).saveAll(anyList());
            verify(weeklyScheduleRepository, never()).deleteAll();
        }

        @Test
        @DisplayName("a slot override for an unknown price id is an InvalidValueException (HTTP 400)")
        void unknownPriceIdIsInvalidValue() {
            when(nordpoolPriceRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.saveSchedules(
                    List.of(new SetpointScheduleItemRequest(999L, new BigDecimal("21.0")))))
                    .isInstanceOf(InvalidValueException.class)
                    .hasMessageContaining("999");
        }
    }

    // ------------------------------------------------------------------------

    /** All 15-minute slots of a Tallinn day (ids 1..n), with timestamps expressed in {@code storageZone}. */
    private List<NordpoolPrice> givenPricesFor(LocalDate day, ZoneId storageZone) {
        ZonedDateTime slot = day.atStartOfDay(TALLINN);
        ZonedDateTime end = day.plusDays(1).atStartOfDay(TALLINN);
        List<NordpoolPrice> prices = new ArrayList<>();
        long id = 1;
        while (slot.isBefore(end)) {
            NordpoolPrice p = new NordpoolPrice(slot.withZoneSameInstant(storageZone), new BigDecimal("50.0"));
            p.setId(id++);
            prices.add(p);
            slot = slot.plusMinutes(15);
        }
        when(nordpoolPriceRepository.findInPeriod(any(), any())).thenReturn(prices);
        return prices;
    }

    /** Template for one day of week: pairs of "HH:mm", "setpoint". */
    private void givenTemplate(int dayOfWeek, String... timeSetpointPairs) {
        List<DefaultWeeklySchedule> entries = new ArrayList<>();
        for (int i = 0; i < timeSetpointPairs.length; i += 2) {
            entries.add(new DefaultWeeklySchedule((short) dayOfWeek,
                    LocalTime.parse(timeSetpointPairs[i]), new BigDecimal(timeSetpointPairs[i + 1])));
        }
        lenient().when(weeklyScheduleRepository.findByDayOfWeekOrderByStartTimeAsc((short) dayOfWeek)).thenReturn(entries);
    }

    /** Setpoint of the first slot whose Tallinn wall-clock time is {@code hhmm}. */
    private static BigDecimal setpointAt(List<SetpointScheduleItemResponse> items, String hhmm) {
        LocalTime time = LocalTime.parse(hhmm);
        return items.stream()
                .filter(i -> tallinnTime(i).equals(time))
                .findFirst()
                .map(SetpointScheduleItemResponse::getSetpoint)
                .orElseThrow(() -> new AssertionError("No slot at " + hhmm));
    }

    private static LocalTime tallinnTime(SetpointScheduleItemResponse item) {
        return item.getTimestamp().withZoneSameInstant(TALLINN).toLocalTime();
    }

    private static NordpoolPrice price(String zonedDateTime) {
        return new NordpoolPrice(ZonedDateTime.parse(zonedDateTime), new BigDecimal("42.0"));
    }
}
