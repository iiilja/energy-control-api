package com.ilja.smarthome.energycontrol.exception;

import com.ilja.smarthome.energycontrol.sungrow.exception.SungrowCommunicationException;
import com.ilja.smarthome.energycontrol.thermia.exception.ThermiaCommException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Maps domain exceptions to HTTP status codes (RFC 7807 problem details).
 * Single-object {@code @Valid} bodies are turned into 400 by Spring MVC itself; list bodies
 * validated through {@code @Validated} controllers raise {@link ConstraintViolationException}.
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

    /** Bean Validation failure from a {@code @Validated} controller, e.g. one bad item in a list body. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException e) {
        String detail = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        log.warn("Rejected request: {}", detail);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
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
