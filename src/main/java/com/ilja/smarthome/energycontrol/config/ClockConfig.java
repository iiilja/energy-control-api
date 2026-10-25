package com.ilja.smarthome.energycontrol.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Single source of "now" for business logic. Injecting a {@link Clock}
 * lets tests pin the current time (including DST transition days).
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Europe/Tallinn"));
    }
}
