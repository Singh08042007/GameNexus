package com.nexus.gaming.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a multiplayer match record, including participating players,
 * move audit trail, final board configuration, and authoritative outcome.
 */
public class Match extends BaseEntity {

    private String roomId;
    private String roomCode;
    private GameType gameType;
    private String playerXId;
    private String playerXName;
    private String playerOId;
    private String playerOName;
    private String winnerId;
    private String winnerName;
    private MatchOutcome outcome;
    private String finalBoard;
    private int moveCount;
    private String startedAt;
    private String endedAt;
    private final List<Move> moves = new ArrayList<>();

    public Match() {
        super();
        this.gameType = GameType.TIC_TAC_TOE;
        this.outcome = MatchOutcome.IN_PROGRESS;
        this.finalBoard = "---------";
        this.startedAt = Instant.now().toString();
    }

    public Match(String roomId, String roomCode, Player playerX, Player playerO) {
        this(roomId, roomCode, GameType.TIC_TAC_TOE, playerX, playerO);
    }

    public Match(String roomId, String roomCode, GameType gameType, Player playerX, Player playerO) {
        super();
        this.roomId = roomId;
        this.roomCode = roomCode;
        this.gameType = (gameType != null) ? gameType : GameType.TIC_TAC_TOE;
        this.playerXId = playerX.getId();
        this.playerXName = playerX.getUsername();
        this.playerOId = playerO.getId();
        this.playerOName = playerO.getUsername();
        this.outcome = MatchOutcome.IN_PROGRESS;
        int cells = this.gameType.getRows() * this.gameType.getCols();
        StringBuilder empty = new StringBuilder(cells);
        for (int i = 0; i < cells; i++) {
            empty.append('-');
        }
        this.finalBoard = empty.toString();
        this.moveCount = 0;
        this.startedAt = Instant.now().toString();
    }

    @Override
    public String getEntityType() {
        return "MATCH";
    }

    public synchronized void addMove(Move move) {
        this.moves.add(move);
        this.moveCount = this.moves.size();
        this.finalBoard = move.getBoardStateAfter();
        touch();
    }

    public synchronized void setMoves(List<Move> loadedMoves) {
        this.moves.clear();
        if (loadedMoves != null) {
            this.moves.addAll(loadedMoves);
        }
    }

    public synchronized void completeMatch(MatchOutcome outcome, String winnerId, String winnerName, String finalBoard) {
        this.outcome = outcome;
        this.winnerId = winnerId;
        this.winnerName = winnerName;
        this.finalBoard = finalBoard;
        this.endedAt = Instant.now().toString();
        touch();
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public void setRoomCode(String roomCode) {
        this.roomCode = roomCode;
    }

    public GameType getGameType() {
        return gameType;
    }

    public void setGameType(GameType gameType) {
        this.gameType = gameType;
    }

    public String getPlayerXId() {
        return playerXId;
    }

    public void setPlayerXId(String playerXId) {
        this.playerXId = playerXId;
    }

    public String getPlayerXName() {
        return playerXName;
    }

    public void setPlayerXName(String playerXName) {
        this.playerXName = playerXName;
    }

    public String getPlayerOId() {
        return playerOId;
    }

    public void setPlayerOId(String playerOId) {
        this.playerOId = playerOId;
    }

    public String getPlayerOName() {
        return playerOName;
    }

    public void setPlayerOName(String playerOName) {
        this.playerOName = playerOName;
    }

    public String getWinnerId() {
        return winnerId;
    }

    public void setWinnerId(String winnerId) {
        this.winnerId = winnerId;
    }

    public String getWinnerName() {
        return winnerName;
    }

    public void setWinnerName(String winnerName) {
        this.winnerName = winnerName;
    }

    public MatchOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(MatchOutcome outcome) {
        this.outcome = outcome;
    }

    public String getFinalBoard() {
        return finalBoard;
    }

    public void setFinalBoard(String finalBoard) {
        this.finalBoard = finalBoard;
    }

    public int getMoveCount() {
        return moveCount;
    }

    public void setMoveCount(int moveCount) {
        this.moveCount = moveCount;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(String startedAt) {
        this.startedAt = startedAt;
    }

    public String getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(String endedAt) {
        this.endedAt = endedAt;
    }

    public synchronized List<Move> getMoves() {
        return Collections.unmodifiableList(new ArrayList<>(moves));
    }
}
