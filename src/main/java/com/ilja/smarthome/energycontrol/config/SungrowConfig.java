package com.ilja.smarthome.energycontrol.config;

import com.ilja.smarthome.energycontrol.sungrow.config.SungrowProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SungrowProperties.class)
public class SungrowConfig {
}
