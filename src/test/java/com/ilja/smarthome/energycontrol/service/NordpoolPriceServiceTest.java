package com.ilja.smarthome.energycontrol.service;

import com.ilja.smarthome.energycontrol.domain.model.NordpoolPrice;
import com.ilja.smarthome.energycontrol.repository.NordpoolPriceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Date/time behaviour of {@link NordpoolPriceService}: Tallinn "today", DST-aware slot counts
 * and rejection of stale prices.
 */
@ExtendWith(MockitoExtension.class)
class NordpoolPriceServiceTest {

    private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

    @Mock private EleringApiClientService eleringClient;
    @Mock private NordpoolPriceRepository priceRepository;

    private NordpoolPriceService serviceAt(String instant) {
        return new NordpoolPriceService(eleringClient, priceRepository,
                Clock.fixed(Instant.parse(instant), TALLINN));
    }

    @Nested
    @DisplayName("hasPricesForDate")
    class HasPricesForDate {

        @ParameterizedTest(name = "{0}: {1} stored slots -> {2}")
        @CsvSource({
                "2026-10-23,  96, true",   // normal day
                "2026-10-23,  95, false",
                "2026-10-25, 100, true",   // DST end: 25 hours
                "2026-10-25,  96, false",  // a normal day's worth is not enough on DST end
                "2026-03-29,  92, true",   // DST start: 23 hours
                "2026-03-29,  91, false",
        })
        void comparesWithSlotsInThatTallinnDay(LocalDate date, long stored, boolean expected) {
            when(priceRepository.countByPriceTimestampBetween(any(), any())).thenReturn(stored);

            assertThat(serviceAt("2026-10-23T09:00:00Z").hasPricesForDate(date)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("getCurrentPrice")
    class GetCurrentPrice {

        @Test
        @DisplayName("returns the slot that contains now")
        void returnsCurrentSlot() {
            // 12:07 Tallinn (EEST) -> slot 12:00
            NordpoolPrice slot = price("2026-10-23T12:00+03:00[Europe/Tallinn]");
            when(priceRepository.findPriceAtTimestamp(any())).thenReturn(Optional.of(slot));

            assertThat(serviceAt("2026-10-23T09:07:00Z").getCurrentPrice()).contains(slot);
        }

        @Test
        @DisplayName("a slot starting exactly now is current")
        void slotStartingNowIsCurrent() {
            NordpoolPrice slot = price("2026-10-23T12:15+03:00[Europe/Tallinn]");
            when(priceRepository.findPriceAtTimestamp(any())).thenReturn(Optional.of(slot));

            assertThat(serviceAt("2026-10-23T09:15:00Z").getCurrentPrice()).contains(slot);
        }

        @Test
        @DisplayName("ignores a price older than one slot (e.g. Elering fetch failed for days)")
        void ignoresStalePrice() {
            NordpoolPrice stale = price("2026-10-21T23:45+03:00[Europe/Tallinn]");
            when(priceRepository.findPriceAtTimestamp(any())).thenReturn(Optional.of(stale));

            assertThat(serviceAt("2026-10-23T09:07:00Z").getCurrentPrice()).isEmpty();
        }

        @Test
        @DisplayName("ignores the previous slot once the current one is missing")
        void ignoresPreviousSlot() {
            NordpoolPrice previous = price("2026-10-23T11:45+03:00[Europe/Tallinn]");
            when(priceRepository.findPriceAtTimestamp(any())).thenReturn(Optional.of(previous));

            assertThat(serviceAt("2026-10-23T09:07:00Z").getCurrentPrice()).isEmpty();
        }
    }

    @Nested
    @DisplayName("default date is today in Tallinn")
    class DefaultDate {

        @Test
        @DisplayName("just after Tallinn midnight (still yesterday in UTC) uses the Tallinn date")
        void usesTallinnDateNotUtc() {
            ArgumentCaptor<ZonedDateTime> start = ArgumentCaptor.forClass(ZonedDateTime.class);
            when(priceRepository.findByPriceTimestampBetweenOrderByPriceTimestampAsc(start.capture(), any()))
                    .thenReturn(List.of());

            // 2026-10-23T22:30Z = 2026-10-24 01:30 in Tallinn
            serviceAt("2026-10-23T22:30:00Z").getPricesForDate(null);

            assertThat(start.getValue().toLocalDate()).isEqualTo(LocalDate.of(2026, 10, 24));
            assertThat(start.getValue().toLocalTime()).hasToString("00:00");
        }
    }

    private static NordpoolPrice price(String zonedDateTime) {
        return new NordpoolPrice(ZonedDateTime.parse(zonedDateTime), new BigDecimal("42.0"));
    }
}
