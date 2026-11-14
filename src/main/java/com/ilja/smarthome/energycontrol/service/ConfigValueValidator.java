package com.ilja.smarthome.energycontrol.service;

import com.ilja.smarthome.energycontrol.exception.InvalidValueException;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Checks a new {@code system_configuration} value before it is saved through the API, so a typo in the UI
 * cannot silently switch a feature off (the services fall back or disable themselves on bad values, which
 * is safe but easy to miss). Keys without a rule accept any non-blank text.
 */
final class ConfigValueValidator {

    private interface Rule {
        /** @return null if valid, otherwise what is expected */
        String check(String value);
    }

    private static final Rule BOOLEAN = v -> v.equals("true") || v.equals("false") ? null : "true or false";

    private static final Map<String, Rule> RULES = Map.of(
            "heating.setpoint.default", decimal("15", "30"),
            "heating.setpoint.manual_override.hours", decimal("0.25", "24"),
            "heating.price_optimization.peak_threshold", positiveDecimal("1000"),
            "heating.price_optimization.lower", decimal("0", "3"),
            "heating.price_optimization.raise", decimal("0", "3"),
            "heating.price_optimization.compensation_minutes", v -> {
                String range = integer(0, 240).check(v);
                return range != null || Integer.parseInt(v) % 15 == 0 ? range : "a multiple of 15";
            },
            "nordpool.cleanup.days_to_keep", integer(1, 3650)
    );

    private static final int MAX_LENGTH = 500;

    private ConfigValueValidator() {
    }

    /** @throws InvalidValueException naming the key and what it expects */
    static void validate(String key, String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidValueException(key + " must not be empty");
        }
        if (value.length() > MAX_LENGTH) {
            throw new InvalidValueException(key + " is longer than " + MAX_LENGTH + " characters");
        }
        Rule rule = key.endsWith(".enabled") ? BOOLEAN : RULES.get(key);
        String expected = rule == null ? null : rule.check(value);
        if (expected != null) {
            throw new InvalidValueException(key + " must be " + expected + ", got '" + value + "'");
        }
    }

    private static Rule decimal(String min, String max) {
        BigDecimal lo = new BigDecimal(min);
        BigDecimal hi = new BigDecimal(max);
        String expected = "a number from " + min + " to " + max;
        return v -> {
            BigDecimal d = parseDecimal(v);
            return d == null || d.compareTo(lo) < 0 || d.compareTo(hi) > 0 ? expected : null;
        };
    }

    private static Rule positiveDecimal(String max) {
        BigDecimal hi = new BigDecimal(max);
        String expected = "a number above 0, at most " + max;
        return v -> {
            BigDecimal d = parseDecimal(v);
            return d == null || d.signum() <= 0 || d.compareTo(hi) > 0 ? expected : null;
        };
    }

    private static Rule integer(int min, int max) {
        String expected = "a whole number from " + min + " to " + max;
        return v -> {
            try {
                int i = Integer.parseInt(v);
                return i < min || i > max ? expected : null;
            } catch (NumberFormatException e) {
                return expected;
            }
        };
    }

    private static BigDecimal parseDecimal(String v) {
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
