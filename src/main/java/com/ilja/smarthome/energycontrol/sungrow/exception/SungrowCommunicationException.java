package com.ilja.smarthome.energycontrol.sungrow.exception;

public class SungrowCommunicationException extends RuntimeException {

    public SungrowCommunicationException(String message) {
        super(message);
    }

    public SungrowCommunicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
