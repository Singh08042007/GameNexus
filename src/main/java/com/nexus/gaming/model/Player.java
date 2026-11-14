package com.nexus.gaming.model;

/**
 * Represents an active player participating in a multiplayer room and match.
 * Extends User to demonstrate OOP Inheritance and Method Overriding.
 */
public class Player extends User {

    private char symbol; // 'X' or 'O'
    private boolean connected;
    private boolean readyForRematch;

    public Player(User user, char symbol) {
        super(user.getId(), user.getEmail(), user.getUsername(),
              user.getWins(), user.getLosses(), user.getDraws(),
              user.getCreatedAt(), user.getUpdatedAt());
        this.symbol = symbol;
        this.connected = true;
        this.readyForRematch = false;
    }

    @Override
    public String getEntityType() {
        return "PLAYER";
    }

    public char getSymbol() {
        return symbol;
    }

    public void setSymbol(char symbol) {
        this.symbol = symbol;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
        touch();
    }

    public boolean isReadyForRematch() {
        return readyForRematch;
    }

    public void setReadyForRematch(boolean readyForRematch) {
        this.readyForRematch = readyForRematch;
    }
}

