package com.ilja.smarthome.energycontrol.service;

import com.ilja.smarthome.energycontrol.domain.model.NordpoolPrice;
import com.ilja.smarthome.energycontrol.service.PricePeakPlanner.Kind;
import com.ilja.smarthome.energycontrol.service.PricePeakPlanner.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link PricePeakPlanner}: median, peaks, joining, and where the compensation goes. */
class PricePeakPlannerTest {

    private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
    private static final BigDecimal TEN = BigDecimal.TEN;

    @Test
    @DisplayName("median of an odd and an even number of prices")
    void median() {
        assertThat(PricePeakPlanner.median(prices(1, 3, 2))).isEqualByComparingTo("2");
        assertThat(PricePeakPlanner.median(prices(1, 2, 3, 4))).isEqualByComparingTo("2.5");
        assertThat(PricePeakPlanner.median(prices(-5, -1, 20))).isEqualByComparingTo("-1");
    }

    @Test
    @DisplayName("slots at or above median + threshold are peaks; compensation goes before on a tie")
    void peakWithCompensationBefore() {
        double[] day = flatDay(50);
        fill(day, 72, 76, 100); // 18:00-18:45

        Result result = PricePeakPlanner.plan(prices(day), TEN, 4);

        assertThat(result.median()).isEqualByComparingTo("50");
        assertThat(result.threshold()).isEqualByComparingTo("60");
        assertThat(indexes(result, Kind.PEAK)).containsExactly(72, 73, 74, 75);
        assertThat(indexes(result, Kind.COMPENSATION)).containsExactly(68, 69, 70, 71);
    }

    @Test
    @DisplayName("a price exactly at the threshold is a peak, just below is not")
    void thresholdInclusive() {
        double[] day = flatDay(50);
        day[10] = 60;
        day[20] = 59.99;

        assertThat(indexes(PricePeakPlanner.plan(prices(day), TEN, 0), Kind.PEAK)).containsExactly(10);
    }

    @Test
    @DisplayName("compensation goes after the peak when that side is cheaper")
    void compensationAfterWhenCheaper() {
        double[] day = flatDay(50);
        fill(day, 72, 76, 100);
        fill(day, 76, 80, 40);

        Result result = PricePeakPlanner.plan(prices(day), TEN, 4);

        assertThat(indexes(result, Kind.COMPENSATION)).containsExactly(76, 77, 78, 79);
    }

    @Test
    @DisplayName("two peaks with one cheaper slot between are joined; with two slots between they are not")
    void joining() {
        double[] day = flatDay(50);
        day[30] = 100;
        day[31] = 55; // single gap -> joined
        day[32] = 100;
        day[60] = 100;
        day[61] = 55; // two-slot gap -> separate
        day[62] = 55;
        day[63] = 100;

        Result result = PricePeakPlanner.plan(prices(day), TEN, 0);

        assertThat(indexes(result, Kind.PEAK)).containsExactly(30, 31, 32, 60, 63);
    }

    @Test
    @DisplayName("a compensation window stops at the next peak")
    void windowStopsAtNextPeak() {
        double[] day = flatDay(50);
        fill(day, 40, 42, 100);
        fill(day, 42, 44, 30); // cheap gap of two slots between two peaks
        fill(day, 44, 46, 100);

        Result result = PricePeakPlanner.plan(prices(day), TEN, 4);

        // first block: after (42-43, avg 30) is cheaper than before (36-39, avg 50);
        // second block: before is the same 42-43, cheaper than after (46-49)
        assertThat(indexes(result, Kind.COMPENSATION)).containsExactly(42, 43);
        assertThat(indexes(result, Kind.PEAK)).containsExactly(40, 41, 44, 45);
    }

    @Test
    @DisplayName("at the start and end of the day the compensation goes to the side that exists")
    void dayEdges() {
        double[] day = flatDay(50);
        fill(day, 0, 2, 100);
        fill(day, 94, 96, 100);

        Result result = PricePeakPlanner.plan(prices(day), TEN, 4);

        assertThat(indexes(result, Kind.COMPENSATION)).containsExactly(2, 3, 4, 5, 90, 91, 92, 93);
    }

    @Test
    @DisplayName("a flat day has no peaks; no compensation when it is set to 0 minutes")
    void flatDayAndNoCompensation() {
        assertThat(indexes(PricePeakPlanner.plan(prices(flatDay(50)), TEN, 4), Kind.PEAK)).isEmpty();

        double[] day = flatDay(50);
        fill(day, 72, 76, 100);
        assertThat(indexes(PricePeakPlanner.plan(prices(day), TEN, 0), Kind.COMPENSATION)).isEmpty();
    }

    @Test
    @DisplayName("no prices: nothing to plan")
    void empty() {
        Result result = PricePeakPlanner.plan(List.of(), TEN, 4);

        assertThat(result.median()).isNull();
        assertThat(result.kinds()).isEmpty();
    }

    // ---------------------------------------------------------------------------------------------

    static double[] flatDay(double price) {
        double[] day = new double[96];
        Arrays.fill(day, price);
        return day;
    }

    static void fill(double[] day, int fromInclusive, int toExclusive, double price) {
        Arrays.fill(day, fromInclusive, toExclusive, price);
    }

    /** One price per 15 minutes from midnight of 16 Nov 2026 (Tallinn), ids 1, 2, ... */
    static List<NordpoolPrice> prices(double... values) {
        Instant start = LocalDate.of(2026, 11, 16).atStartOfDay(TALLINN).toInstant();
        List<NordpoolPrice> prices = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            NordpoolPrice p = new NordpoolPrice(start.plus(15L * i, ChronoUnit.MINUTES).atZone(TALLINN),
                    BigDecimal.valueOf(values[i]));
            p.setId(i + 1L);
            prices.add(p);
        }
        return prices;
    }

    private static List<Integer> indexes(Result result, Kind kind) {
        return IntStream.range(0, result.kinds().size()).filter(i -> result.kinds().get(i) == kind).boxed().toList();
    }
}
