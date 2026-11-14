package com.nexus.gaming.exception;

/**
 * Thrown when room creation, joining, or state transitions violate room business rules.
 */
public class RoomException extends NexusException {

    private static final long serialVersionUID = 1L;

    public RoomException(String message) {
        super(message, 400, "ROOM_ERROR");
    }

    public RoomException(String message, int httpStatus, String errorCode) {
        super(message, httpStatus, errorCode);
    }
}
