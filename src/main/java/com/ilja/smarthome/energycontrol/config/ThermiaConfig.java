package com.ilja.smarthome.energycontrol.config;

import com.ilja.smarthome.energycontrol.thermia.config.ThermiaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ThermiaProperties.class)
public class ThermiaConfig {
}
