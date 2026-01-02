package com.ilja.smarthome.energycontrol.exception;

public class EleringApiException extends RuntimeException {
    public EleringApiException(String message) {
        super(message);
    }

    public EleringApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
