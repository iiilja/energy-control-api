package com.ilja.smarthome.energycontrol.exception;

import com.ilja.smarthome.energycontrol.sungrow.exception.SungrowCommunicationException;
import com.ilja.smarthome.energycontrol.thermia.exception.ThermiaCommException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain exceptions to HTTP status codes (RFC 7807 problem details).
 * Bean Validation errors on request bodies are already turned into 400 by Spring MVC.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Invalid input that passed DTO validation but was rejected by a service (range checks, unknown ids). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Rejected request: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** Heat pump or inverter not reachable over Modbus. */
    @ExceptionHandler({ThermiaCommException.class, SungrowCommunicationException.class})
    public ProblemDetail handleDeviceUnavailable(RuntimeException e) {
        log.warn("Device communication failed: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    /** Elering price API failed. */
    @ExceptionHandler(EleringApiException.class)
    public ProblemDetail handleEleringFailure(EleringApiException e) {
        log.warn("Elering API failed: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
    }
}
