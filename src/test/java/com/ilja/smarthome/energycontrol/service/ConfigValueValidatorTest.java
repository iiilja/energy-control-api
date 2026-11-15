package com.ilja.smarthome.energycontrol.service;

import com.ilja.smarthome.energycontrol.exception.InvalidValueException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link ConfigValueValidator}: values the API accepts and rejects for known keys. */
class ConfigValueValidatorTest {

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource({
            "heating.price_optimization.enabled, true",
            "nordpool.fetch.enabled, false",
            "heating.price_optimization.peak_threshold, 50",
            "heating.price_optimization.peak_threshold, 12.5",
            "heating.price_optimization.lower, 0",
            "heating.price_optimization.raise, 3",
            "heating.price_optimization.compensation_minutes, 0",
            "heating.price_optimization.compensation_minutes, 45",
            "heating.setpoint.default, 21.5",
            "heating.setpoint.manual_override.hours, 0.25",
            "nordpool.cleanup.days_to_keep, 30",
            "esp32.base_url, http://192.168.8.125",   // no rule: any text
    })
    void accepted(String key, String value) {
        assertThatCode(() -> ConfigValueValidator.validate(key, value)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} = ''{1}''")
    @CsvSource({
            "heating.price_optimization.enabled, yes",
            "heating.price_optimization.enabled, TRUE",
            "heating.price_optimization.peak_threshold, 0",
            "heating.price_optimization.peak_threshold, -5",
            "heating.price_optimization.peak_threshold, abc",
            "heating.price_optimization.lower, 3.5",
            "heating.price_optimization.compensation_minutes, 50",
            "heating.price_optimization.compensation_minutes, 255",
            "heating.price_optimization.compensation_minutes, 1.5",
            "heating.setpoint.default, 14",
            "nordpool.cleanup.days_to_keep, 0",
            "esp32.base_url, ' '",
    })
    void rejected(String key, String value) {
        assertThatThrownBy(() -> ConfigValueValidator.validate(key, value))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageStartingWith(key);
    }
}
