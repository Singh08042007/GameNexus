package com.nexus.gaming.exception;

/**
 * Base application exception for the NEXUS platform.
 * Demonstrates OOP Inheritance in the Exception hierarchy.
 */
public class NexusException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int httpStatus;
    private final String errorCode;

    public NexusException(String message, int httpStatus, String errorCode) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public NexusException(String message, int httpStatus, String errorCode, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
