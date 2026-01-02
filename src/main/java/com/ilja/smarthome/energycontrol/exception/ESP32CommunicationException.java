package com.ilja.smarthome.energycontrol.exception;

/**
 * Exception thrown when communication with the ESP32 device fails.
 * This can occur due to network issues, device unavailability, or invalid responses.
 */
public class ESP32CommunicationException extends RuntimeException {

    public ESP32CommunicationException(String message) {
        super(message);
    }

    public ESP32CommunicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
