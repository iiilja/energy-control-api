package com.ilja.smarthome.energycontrol.repository;

import com.ilja.smarthome.energycontrol.domain.model.DefaultWeeklySchedule;
import com.ilja.smarthome.energycontrol.domain.model.HeatingControlState;
import com.ilja.smarthome.energycontrol.domain.model.HeatingManualOverride;
import com.ilja.smarthome.energycontrol.domain.model.HeatingSetpointSchedule;
import com.ilja.smarthome.energycontrol.domain.model.NordpoolPrice;
import com.ilja.smarthome.energycontrol.domain.model.SystemConfiguration;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Migrations, entity mappings and repository queries against a real PostgreSQL (the version on the
 * H96 box), started in Docker by Testcontainers.
 *
 * <ul>
 *   <li>Flyway runs every migration from scratch, so a migration that fails on PostgreSQL is caught
 *       here instead of on the box (where the desired-state migration was one-way).</li>
 *   <li>{@code ddl-auto=validate}, as in production: the context does not start if an entity and its
 *       table disagree.</li>
 *   <li>JPQL queries run on PostgreSQL, with timestamps stored the way production stores them.</li>
 * </ul>
 * Skipped (not failed) when Docker is not running. Each test runs in a transaction that is rolled
 * back, so the seed data from the migrations is all a test can rely on.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostgresRepositoryTest {

    /** Same major version as production. One container for all nested classes. */
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

    @Autowired EntityManager em;
    @Autowired NordpoolPriceRepository prices;
    @Autowired HeatingSetpointScheduleRepository slotOverrides;
    @Autowired HeatingManualOverrideRepository manualOverrides;
    @Autowired HeatingControlStateRepository controlState;
    @Autowired DefaultWeeklyScheduleRepository weekly;
    @Autowired SystemConfigurationRepository config;

    // ---------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("migrations")
    class Migrations {

        @Test
        @DisplayName("all migrations applied successfully")
        void allApplied() {
            List<?> failed = em.createNativeQuery(
                    "SELECT version FROM flyway_schema_history WHERE NOT success").getResultList();
            Number applied = (Number) em.createNativeQuery(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE success AND version IS NOT NULL")
                    .getSingleResult();

            assertThat(failed).isEmpty();
            assertThat(applied.intValue()).isGreaterThanOrEqualTo(8);
        }

        @Test
        @DisplayName("every configuration key the code reads is seeded with a usable value")
        void configurationSeeded() {
            Map<String, String> values = config.findAll().stream()
                    .collect(Collectors.toMap(SystemConfiguration::getConfigKey, SystemConfiguration::getConfigValue));

            assertThat(values).containsKeys(
                    "heating.setpoint.default",
                    "heating.setpoint.adjustment.enabled",
                    "heating.setpoint.manual_override.hours",
                    "nordpool.fetch.enabled",
                    "nordpool.cleanup.enabled",
                    "nordpool.cleanup.days_to_keep",
                    "sungrow.feed_in_limit.enabled",
                    "heating.price_optimization.enabled",
                    "heating.price_optimization.peak_threshold",
                    "heating.price_optimization.raise",
                    "heating.price_optimization.lower",
                    "heating.price_optimization.compensation_minutes");
            assertThat(new BigDecimal(values.get("heating.setpoint.default"))).isBetween(
                    new BigDecimal("15"), new BigDecimal("30"));
            assertThat(Double.parseDouble(values.get("heating.setpoint.manual_override.hours"))).isPositive();
            assertThat(Integer.parseInt(values.get("nordpool.cleanup.days_to_keep"))).isPositive();
            assertThat(values.get("heating.price_optimization.enabled")).isEqualTo("false");
        }

        @Test
        @DisplayName("weekly template is seeded for every day, starting at midnight")
        void weeklyTemplateSeeded() {
            for (short day = 1; day <= 7; day++) {
                List<DefaultWeeklySchedule> entries = weekly.findByDayOfWeekOrderByStartTimeAsc(day);
                assertThat(entries).as("day %d", day).isNotEmpty();
                assertThat(entries.get(0).getStartTime()).as("day %d", day).isEqualTo(LocalTime.MIDNIGHT);
            }
        }

        @Test
        @DisplayName("heating_setpoint_schedule no longer has the 'applied' column")
        void appliedColumnDropped() {
            Number columns = (Number) em.createNativeQuery(
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_name = 'heating_setpoint_schedule' AND column_name = 'applied'")
                    .getSingleResult();

            assertThat(columns.intValue()).isZero();
        }
    }

    // ---------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Nordpool prices")
    class Prices {

        @Test
        @DisplayName("a day query is half-open: the next day's 00:00 slot is not included")
        void dayIsHalfOpen() {
            LocalDate day = LocalDate.of(2026, 3, 10);
            ZonedDateTime start = day.atStartOfDay(TALLINN);
            ZonedDateTime end = day.plusDays(1).atStartOfDay(TALLINN);
            saveQuarters(start.minusMinutes(15), end.plusMinutes(15)); // a slot either side

            List<NordpoolPrice> found = prices.findInPeriod(start, end);

            assertThat(found).hasSize(96);
            assertThat(found.get(0).getPriceTimestamp().toInstant()).isEqualTo(start.toInstant());
            assertThat(found.get(95).getPriceTimestamp().toInstant()).isEqualTo(end.minusMinutes(15).toInstant());
            assertThat(prices.countInPeriod(start, end)).isEqualTo(96);
        }

        @Test
        @DisplayName("DST days: 92 slots in spring, 100 in autumn")
        void dstDays() {
            LocalDate spring = LocalDate.of(2026, 3, 29);
            LocalDate autumn = LocalDate.of(2026, 10, 25);
            saveQuarters(spring.atStartOfDay(TALLINN), spring.plusDays(1).atStartOfDay(TALLINN));
            saveQuarters(autumn.atStartOfDay(TALLINN), autumn.plusDays(1).atStartOfDay(TALLINN));

            assertThat(prices.countInPeriod(spring.atStartOfDay(TALLINN), spring.plusDays(1).atStartOfDay(TALLINN)))
                    .isEqualTo(92);
            assertThat(prices.findInPeriod(autumn.atStartOfDay(TALLINN), autumn.plusDays(1).atStartOfDay(TALLINN)))
                    .hasSize(100);
        }

        @Test
        @DisplayName("a slot is found by the same instant written in another zone")
        void lookupIsByInstant() {
            ZonedDateTime slot = ZonedDateTime.of(2026, 1, 15, 10, 0, 0, 0, TALLINN);
            prices.saveAndFlush(new NordpoolPrice(slot, new BigDecimal("0.1234")));
            em.clear();

            assertThat(prices.findByPriceTimestamp(slot.withZoneSameInstant(ZoneOffset.UTC)))
                    .hasValueSatisfying(p -> assertThat(p.getPrice()).isEqualByComparingTo("0.1234"));
        }

        @Test
        @DisplayName("the price at a moment is the slot that started last before it")
        void priceAtTimestamp() {
            ZonedDateTime ten = ZonedDateTime.of(2026, 1, 15, 10, 0, 0, 0, TALLINN);
            saveQuarters(ten.minusMinutes(15), ten.plusMinutes(30));

            assertThat(prices.findPriceAtTimestamp(ten.plusMinutes(7)))
                    .hasValueSatisfying(p -> assertThat(p.getPriceTimestamp().toInstant()).isEqualTo(ten.toInstant()));
            assertThat(prices.findPriceAtTimestamp(ten))
                    .hasValueSatisfying(p -> assertThat(p.getPriceTimestamp().toInstant()).isEqualTo(ten.toInstant()));
        }

        @Test
        @DisplayName("average over a period and cleanup of old prices")
        void averageAndCleanup() {
            ZonedDateTime ten = ZonedDateTime.of(2026, 1, 15, 10, 0, 0, 0, TALLINN);
            prices.save(new NordpoolPrice(ten, new BigDecimal("0.10")));
            prices.save(new NordpoolPrice(ten.plusMinutes(15), new BigDecimal("0.20")));
            prices.save(new NordpoolPrice(ten.plusMinutes(30), new BigDecimal("0.30")));
            prices.flush();

            assertThat(prices.findAveragePriceBetween(ten, ten.plusMinutes(30)))
                    .hasValueSatisfying(avg -> assertThat(avg).isEqualByComparingTo("0.15"));

            prices.deleteByPriceTimestampBefore(ten.plusMinutes(15));
            prices.flush();
            em.clear();

            assertThat(prices.findAll(Sort.by("priceTimestamp"))).extracting(p -> p.getPriceTimestamp().toInstant())
                    .containsExactly(ten.plusMinutes(15).toInstant(), ten.plusMinutes(30).toInstant());
        }

        @Test
        @DisplayName("one price per slot")
        void uniqueSlot() {
            ZonedDateTime slot = ZonedDateTime.of(2026, 1, 15, 10, 0, 0, 0, TALLINN);
            prices.saveAndFlush(new NordpoolPrice(slot, BigDecimal.ONE));

            assertThatThrownBy(() -> prices.saveAndFlush(new NordpoolPrice(slot, BigDecimal.TEN)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    // ---------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("price-slot setpoint overrides")
    class SlotOverrides {

        @Test
        @DisplayName("found by the slot's time (half-open) and by the price row")
        void findInPeriodAndByPrice() {
            ZonedDateTime start = ZonedDateTime.of(2026, 1, 15, 0, 0, 0, 0, TALLINN);
            List<NordpoolPrice> slots = saveQuarters(start.minusMinutes(15), start.plusMinutes(45));
            for (NordpoolPrice slot : slots) {
                slotOverrides.save(new HeatingSetpointSchedule(slot, new BigDecimal("22.50")));
            }
            slotOverrides.flush();
            em.clear();

            List<HeatingSetpointSchedule> found = slotOverrides.findInPeriod(start, start.plusMinutes(30));
            assertThat(found).hasSize(2);
            assertThat(found).allSatisfy(s -> assertThat(s.getTargetSetpoint()).isEqualByComparingTo("22.5"));

            NordpoolPrice first = prices.findByPriceTimestamp(start).orElseThrow();
            assertThat(slotOverrides.findByNordpoolPrice(first)).isPresent();

            slotOverrides.deleteByNordpoolPrice(first);
            slotOverrides.flush();
            assertThat(slotOverrides.findByNordpoolPrice(first)).isEmpty();
            assertThat(slotOverrides.count()).isEqualTo(slots.size() - 1);
        }

        @Test
        @DisplayName("source is stored, defaults to MANUAL, and the price comes loaded")
        void sourceAndFetchedPrice() {
            ZonedDateTime start = ZonedDateTime.of(2026, 1, 15, 0, 0, 0, 0, TALLINN);
            List<NordpoolPrice> slots = saveQuarters(start, start.plusMinutes(30));
            slotOverrides.save(new HeatingSetpointSchedule(slots.get(0), new BigDecimal("22.0")));
            slotOverrides.save(new HeatingSetpointSchedule(slots.get(1), new BigDecimal("20.0"),
                    HeatingSetpointSchedule.Source.PRICE));
            slotOverrides.flush();
            em.clear();

            List<HeatingSetpointSchedule> found = slotOverrides.findInPeriod(start, start.plusMinutes(30));

            assertThat(found).allSatisfy(s -> assertThat(Hibernate.isInitialized(s.getNordpoolPrice())).isTrue());
            assertThat(found).extracting(s -> s.getNordpoolPrice().getPriceTimestamp().toInstant(),
                            HeatingSetpointSchedule::getSource)
                    .containsExactlyInAnyOrder(
                            tuple(start.toInstant(), HeatingSetpointSchedule.Source.MANUAL),
                            tuple(start.plusMinutes(15).toInstant(), HeatingSetpointSchedule.Source.PRICE));
        }

        @Test
        @DisplayName("deleteByIdInAndSource keeps a row that is no longer PRICE")
        void guardedDelete() {
            ZonedDateTime start = ZonedDateTime.of(2026, 1, 15, 0, 0, 0, 0, TALLINN);
            List<NordpoolPrice> slots = saveQuarters(start, start.plusMinutes(30));
            HeatingSetpointSchedule planned = slotOverrides.save(new HeatingSetpointSchedule(slots.get(0),
                    new BigDecimal("20.0"), HeatingSetpointSchedule.Source.PRICE));
            HeatingSetpointSchedule manual = slotOverrides.save(new HeatingSetpointSchedule(slots.get(1),
                    new BigDecimal("23.0"), HeatingSetpointSchedule.Source.MANUAL));
            slotOverrides.flush();

            int deleted = slotOverrides.deleteByIdInAndSource(List.of(planned.getId(), manual.getId()),
                    HeatingSetpointSchedule.Source.PRICE);
            em.clear();

            assertThat(deleted).isEqualTo(1);
            assertThat(slotOverrides.findAll()).extracting(HeatingSetpointSchedule::getId)
                    .containsExactly(manual.getId());
        }

        @Test
        @DisplayName("source is MANUAL or PRICE")
        void sourceChecked() {
            NordpoolPrice slot = prices.saveAndFlush(new NordpoolPrice(
                    ZonedDateTime.of(2026, 1, 15, 0, 0, 0, 0, TALLINN), BigDecimal.ONE));

            assertThatThrownBy(() -> em.createNativeQuery("INSERT INTO heating_setpoint_schedule "
                    + "(nordpool_price_id, target_setpoint, source, created_at) VALUES ("
                    + slot.getId() + ", 21, 'OTHER', now())")
                    .executeUpdate())
                    .rootCause().hasMessageContaining("valid_slot_override_source");
        }
    }

    // ---------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("manual overrides and control state")
    class DesiredState {

        private final Instant now = Instant.parse("2026-11-13T18:00:00Z");

        @Test
        @DisplayName("findOpen returns the newest override that has not ended")
        void findOpenReturnsNewestOpen() {
            HeatingManualOverride older = override("21.0", now.minus(3, ChronoUnit.HOURS));
            HeatingManualOverride ended = override("23.0", now.minus(30, ChronoUnit.MINUTES));
            ended.setEndedAt(now.minus(10, ChronoUnit.MINUTES));
            HeatingManualOverride newest = override("22.5", now.minus(1, ChronoUnit.HOURS));
            manualOverrides.saveAll(List.of(older, ended, newest));
            manualOverrides.flush();
            em.clear();

            assertThat(manualOverrides.findOpen())
                    .hasValueSatisfying(o -> {
                        assertThat(o.getId()).isEqualTo(newest.getId());
                        assertThat(o.getSetpoint()).isEqualByComparingTo("22.5");
                        assertThat(o.getSource()).isEqualTo(HeatingManualOverride.Source.HEAT_PUMP);
                        assertThat(o.getValidUntil()).isEqualTo(newest.getValidUntil());
                    });
        }

        @Test
        @DisplayName("findOpen is empty when every override has ended")
        void findOpenEmpty() {
            HeatingManualOverride ended = override("22.0", now.minus(5, ChronoUnit.HOURS));
            ended.setEndedAt(now.minus(1, ChronoUnit.HOURS));
            manualOverrides.saveAndFlush(ended);

            assertThat(manualOverrides.findOpen()).isEmpty();
        }

        @Test
        @DisplayName("an override must end after it starts")
        void overridePeriodChecked() {
            HeatingManualOverride backwards = new HeatingManualOverride(new BigDecimal("22.0"),
                    HeatingManualOverride.Source.API, now, now);

            assertThatThrownBy(() -> manualOverrides.saveAndFlush(backwards))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("control state round-trips, including a cleared pending setpoint")
        void controlStateRoundTrip() {
            HeatingControlState state = new HeatingControlState();
            state.setLastDesiredSetpoint(new BigDecimal("21.5"));
            state.setLastReadBackSetpoint(new BigDecimal("21.5"));
            state.setPendingSetpoint(new BigDecimal("22.0"));
            state.setUpdatedAt(now);
            controlState.saveAndFlush(state);
            em.clear();

            HeatingControlState loaded = controlState.findById(HeatingControlState.SINGLETON_ID).orElseThrow();
            assertThat(loaded.getLastDesiredSetpoint()).isEqualByComparingTo("21.5");
            assertThat(loaded.getPendingSetpoint()).isEqualByComparingTo("22.0");
            assertThat(loaded.getUpdatedAt()).isEqualTo(now);

            loaded.setPendingSetpoint(null);
            controlState.saveAndFlush(loaded);
            em.clear();
            assertThat(controlState.findById(HeatingControlState.SINGLETON_ID).orElseThrow().getPendingSetpoint())
                    .isNull();
        }

        @Test
        @DisplayName("the control state table holds a single row")
        void controlStateSingleRow() {
            assertThatThrownBy(() -> em.createNativeQuery("INSERT INTO heating_control_state "
                    + "(id, last_desired_setpoint, last_read_back_setpoint, updated_at) "
                    + "VALUES (2, 21, 21, now())").executeUpdate())
                    .rootCause().hasMessageContaining("single_row");
        }

        private HeatingManualOverride override(String setpoint, Instant startedAt) {
            return new HeatingManualOverride(new BigDecimal(setpoint), HeatingManualOverride.Source.HEAT_PUMP,
                    startedAt, startedAt.plus(4, ChronoUnit.HOURS));
        }
    }

    // ---------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("weekly template")
    class WeeklyTemplate {

        @Test
        @DisplayName("entries come back in day and time order")
        void ordered() {
            weekly.deleteAllInBatch();
            weekly.save(new DefaultWeeklySchedule((short) 2, LocalTime.of(8, 0), new BigDecimal("21.0")));
            weekly.save(new DefaultWeeklySchedule((short) 1, LocalTime.of(17, 30), new BigDecimal("22.0")));
            weekly.save(new DefaultWeeklySchedule((short) 1, LocalTime.MIDNIGHT, new BigDecimal("20.0")));
            weekly.flush();
            em.clear();

            assertThat(weekly.findAllByOrderByDayOfWeekAscStartTimeAsc())
                    .extracting(e -> e.getDayOfWeek() + " " + e.getStartTime())
                    .containsExactly("1 00:00", "1 17:30", "2 08:00");
            assertThat(weekly.findByDayOfWeekOrderByStartTimeAsc((short) 1))
                    .extracting(DefaultWeeklySchedule::getStartTime)
                    .containsExactly(LocalTime.MIDNIGHT, LocalTime.of(17, 30));
        }

        @Test
        @DisplayName("one entry per day and start time")
        void uniqueDayAndTime() {
            assertThatThrownBy(() -> weekly.saveAndFlush(
                    new DefaultWeeklySchedule((short) 1, LocalTime.MIDNIGHT, new BigDecimal("19.0"))))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    // ---------------------------------------------------------------------------------------------

    /** Saves one price per 15 minutes in [from, to) and returns them in order. */
    private List<NordpoolPrice> saveQuarters(ZonedDateTime from, ZonedDateTime to) {
        List<NordpoolPrice> saved = new ArrayList<>();
        int i = 0;
        // Step in UTC so DST days get their real number of slots
        for (Instant t = from.toInstant(); t.isBefore(to.toInstant()); t = t.plus(15, ChronoUnit.MINUTES)) {
            saved.add(prices.save(new NordpoolPrice(t.atZone(TALLINN), BigDecimal.valueOf(i++, 2))));
        }
        prices.flush();
        return saved;
    }
}
