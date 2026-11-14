package com.nexus.gaming.exception;

/**
 * Thrown by the Server-Authoritative Game Engine when a player submits an invalid move.
 */
public class InvalidMoveException extends NexusException {

    private static final long serialVersionUID = 1L;

    public InvalidMoveException(String message) {
        super(message, 400, "INVALID_MOVE");
    }

    public InvalidMoveException(String message, String errorCode) {
        super(message, 400, errorCode);
    }
}
