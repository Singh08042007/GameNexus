package com.nexus.gaming.exception;

/**
 * Thrown when a requested entity (User, Room, Match) is not found.
 */
public class ResourceNotFoundException extends NexusException {

    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String message) {
        super(message, 404, "NOT_FOUND");
    }
}
