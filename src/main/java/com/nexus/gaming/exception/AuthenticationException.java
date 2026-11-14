package com.nexus.gaming.exception;

/**
 * Thrown when authentication fails (invalid credentials, missing/expired token).
 */
public class AuthenticationException extends NexusException {

    private static final long serialVersionUID = 1L;

    public AuthenticationException(String message) {
        super(message, 401, "AUTH_ERROR");
    }

    public AuthenticationException(String message, String errorCode) {
        super(message, 401, errorCode);
    }
}
