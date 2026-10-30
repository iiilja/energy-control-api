package com.ilja.smarthome.energycontrol.exception;

/**
 * A value supplied by a client (or stored by one earlier) is not acceptable:
 * out of range, unknown id, duplicate entry. Mapped to HTTP 400 by {@link GlobalExceptionHandler}.
 *
 * <p>Only this type is mapped to 400, not every {@link IllegalArgumentException}: library and
 * configuration errors such as {@link NumberFormatException} are server problems and stay 500.
 * It still extends {@code IllegalArgumentException}, so existing callers that catch that keep working.
 */
public class InvalidValueException extends IllegalArgumentException {

    public InvalidValueException(String message) {
        super(message);
    }
}
