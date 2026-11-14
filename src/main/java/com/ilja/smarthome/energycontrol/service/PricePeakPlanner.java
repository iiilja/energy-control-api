package com.ilja.smarthome.energycontrol.service;

import com.ilja.smarthome.energycontrol.domain.model.NordpoolPrice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Finds the price peaks of a day and where to compensate for them. No I/O.
 *
 * <ol>
 *   <li><b>Reference price:</b> the median of the day's 15-minute prices, i.e. the day's typical price.
 *       Unlike the mean it is not pulled up by the peaks themselves (or down by negative prices).</li>
 *   <li><b>Peak:</b> every slot whose price is at least median + {@code peakThreshold} (EUR/MWh). An absolute
 *       margin rather than a percentage, because the saving is in euros, and percentages break down when
 *       the median is near zero.</li>
 *   <li>Two peaks separated by a single cheaper slot are joined, so the setpoint does not saw up and down
 *       every 15 minutes.</li>
 *   <li><b>Compensation:</b> for every block of peak slots, {@code compensationSlots} slots right before or
 *       right after it, whichever side is cheaper on average (before on a tie: the house stores heat ahead
 *       of the peak). Peak slots are never compensation slots; a window stops at the next peak and at the
 *       end of the day.</li>
 * </ol>
 */
final class PricePeakPlanner {

    enum Kind { NORMAL, PEAK, COMPENSATION }

    /** {@code kinds} is aligned with the prices passed in; median and threshold are null for no prices. */
    record Result(BigDecimal median, BigDecimal threshold, List<Kind> kinds) {
    }

    private PricePeakPlanner() {
    }

    /** @param prices the day's prices in time order */
    static Result plan(List<NordpoolPrice> prices, BigDecimal peakThreshold, int compensationSlots) {
        int n = prices.size();
        List<Kind> kinds = new ArrayList<>(Collections.nCopies(n, Kind.NORMAL));
        if (n == 0) {
            return new Result(null, null, kinds);
        }
        BigDecimal median = median(prices);
        BigDecimal threshold = median.add(peakThreshold);

        boolean[] peak = new boolean[n];
        for (int i = 0; i < n; i++) {
            peak[i] = prices.get(i).getPrice().compareTo(threshold) >= 0;
        }
        boolean[] joined = peak.clone();
        for (int i = 1; i < n - 1; i++) {
            if (!peak[i] && peak[i - 1] && peak[i + 1]) {
                joined[i] = true;
            }
        }
        for (int i = 0; i < n; i++) {
            if (joined[i]) {
                kinds.set(i, Kind.PEAK);
            }
        }

        if (compensationSlots > 0) {
            int i = 0;
            while (i < n) {
                if (!joined[i]) {
                    i++;
                    continue;
                }
                int first = i;
                while (i < n && joined[i]) {
                    i++;
                }
                compensate(prices, joined, kinds, first, i - 1, compensationSlots);
            }
        }
        return new Result(median, threshold, kinds);
    }

    private static void compensate(List<NordpoolPrice> prices, boolean[] peak, List<Kind> kinds,
                                   int first, int last, int slots) {
        List<Integer> before = new ArrayList<>();
        for (int j = first - 1; j >= 0 && before.size() < slots && !peak[j]; j--) {
            before.add(j);
        }
        List<Integer> after = new ArrayList<>();
        for (int j = last + 1; j < prices.size() && after.size() < slots && !peak[j]; j++) {
            after.add(j);
        }
        List<Integer> chosen;
        if (before.isEmpty()) {
            chosen = after;
        } else if (after.isEmpty()) {
            chosen = before;
        } else {
            chosen = average(prices, after).compareTo(average(prices, before)) < 0 ? after : before;
        }
        for (int j : chosen) {
            kinds.set(j, Kind.COMPENSATION);
        }
    }

    static BigDecimal median(List<NordpoolPrice> prices) {
        BigDecimal[] sorted = prices.stream().map(NordpoolPrice::getPrice).toArray(BigDecimal[]::new);
        Arrays.sort(sorted);
        int mid = sorted.length / 2;
        BigDecimal median = sorted.length % 2 == 1
                ? sorted[mid]
                : sorted[mid - 1].add(sorted[mid]).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
        return median.setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal average(List<NordpoolPrice> prices, List<Integer> indexes) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int j : indexes) {
            sum = sum.add(prices.get(j).getPrice());
        }
        return sum.divide(BigDecimal.valueOf(indexes.size()), 6, RoundingMode.HALF_UP);
    }
}
