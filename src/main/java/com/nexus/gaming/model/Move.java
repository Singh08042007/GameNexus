package com.nexus.gaming.model;

import java.time.Instant;

/**
 * Immutable value object representing a single server-validated move on the game board.
 */
public final class Move {

    private final String matchId;
    private final String playerId;
    private final String playerUsername;
    private final char symbol;
    private final int row;
    private final int col;
    private final int moveNumber;
    private final String boardStateAfter;
    private final String createdAt;

    public Move(String matchId, String playerId, String playerUsername, char symbol,
                int row, int col, int moveNumber, String boardStateAfter) {
        this(matchId, playerId, playerUsername, symbol, row, col,
             moveNumber, boardStateAfter, Instant.now().toString());
    }

    public Move(String matchId, String playerId, String playerUsername, char symbol,
                int row, int col, int moveNumber, String boardStateAfter, String createdAt) {
        this.matchId = matchId;
        this.playerId = playerId;
        this.playerUsername = playerUsername;
        this.symbol = symbol;
        this.row = row;
        this.col = col;
        this.moveNumber = moveNumber;
        this.boardStateAfter = boardStateAfter;
        this.createdAt = createdAt;
    }

    public String getMatchId() {
        return matchId;
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getPlayerUsername() {
        return playerUsername;
    }

    public char getSymbol() {
        return symbol;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public int getMoveNumber() {
        return moveNumber;
    }

    public String getBoardStateAfter() {
        return boardStateAfter;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}

