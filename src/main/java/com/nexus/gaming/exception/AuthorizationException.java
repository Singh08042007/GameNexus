package com.nexus.gaming.exception;

/**
 * Thrown when an authenticated player attempts an unauthorized action
 * (e.g., making a move in a room they do not belong to or impersonating another player).
 */
public class AuthorizationException extends NexusException {

    private static final long serialVersionUID = 1L;

    public AuthorizationException(String message) {
        super(message, 403, "FORBIDDEN");
    }
}
