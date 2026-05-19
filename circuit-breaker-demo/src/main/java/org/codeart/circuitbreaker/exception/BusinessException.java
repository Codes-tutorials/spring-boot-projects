package org.codeart.circuitbreaker.exception;

/**
 * Business exception that should not trigger circuit breaker.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
