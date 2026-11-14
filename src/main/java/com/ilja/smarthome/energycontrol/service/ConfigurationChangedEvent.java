package com.ilja.smarthome.energycontrol.service;

/** Published when a {@code system_configuration} value was changed through the API; listeners run after the commit. */
public record ConfigurationChangedEvent(String key) {
}
