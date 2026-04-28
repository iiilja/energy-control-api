package com.ilja.smarthome.energycontrol.thermia.exception;

public class ThermiaCommException extends RuntimeException {

    public ThermiaCommException(String message) {
        super(message);
    }

    public ThermiaCommException(String message, Throwable cause) {
        super(message, cause);
    }
}
