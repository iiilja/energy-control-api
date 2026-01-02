package com.ilja.smarthome.energycontrol.exception;

/**
 * Exception thrown when a requested configuration key is not found.
 */
public class ConfigurationNotFoundException extends RuntimeException {

    public ConfigurationNotFoundException(String key) {
        super("Configuration not found for key: " + key);
    }
}
