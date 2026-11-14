package com.nexus.gaming.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable domain model representing an in-room chat or system message.
 */
public final class ChatMessage {

    private final String id;
    private final String roomCode;
    private final String senderId;
    private final String senderUsername;
    private final String senderSymbol;
    private final String message;
    private final String messageType;
    private final String createdAt;

    public ChatMessage(String roomCode,
                       String senderId,
                       String senderUsername,
                       String senderSymbol,
                       String message,
                       String messageType) {
        this(
                UUID.randomUUID().toString(),
                roomCode,
                senderId,
                senderUsername,
                senderSymbol,
                message,
                messageType,
                Instant.now().toString()
        );
    }

    public ChatMessage(String id,
                       String roomCode,
                       String senderId,
                       String senderUsername,
                       String senderSymbol,
                       String message,
                       String messageType,
                       String createdAt) {
        this.id = id;
        this.roomCode = roomCode;
        this.senderId = senderId;
        this.senderUsername = senderUsername;
        this.senderSymbol = senderSymbol;
        this.message = message;
        this.messageType = messageType;
        this.createdAt = createdAt;
    }

    public static ChatMessage systemMessage(String roomCode, String text) {
        return new ChatMessage(roomCode, "SYSTEM", "Arena Director", "SYS", text, "SYSTEM");
    }

    public String getId() {
        return id;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getSenderUsername() {
        return senderUsername;
    }

    public String getSenderSymbol() {
        return senderSymbol;
    }

    public String getMessage() {
        return message;
    }

    public String getMessageType() {
        return messageType;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}

