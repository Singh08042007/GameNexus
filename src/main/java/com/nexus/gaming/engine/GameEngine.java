package com.nexus.gaming.engine;

import com.nexus.gaming.model.GameType;
import com.nexus.gaming.model.MatchOutcome;
import com.nexus.gaming.model.Move;

import java.util.List;

/**
 * Contract for server-authoritative multiplayer board game engines on the NEXUS platform.
 * Demonstrates OOP Interfaces and Polymorphism across multiple game types.
 */
public interface GameEngine {

    GameType getGameType();

    int getRows();

    int getCols();

    int getWinLength();

    void initializeBoard();

    void validateMove(String playerId, char playerSymbol, int row, int col);

    Move applyMove(String matchId, String playerId, String playerUsername, char playerSymbol, int row, int col);

    MatchOutcome checkOutcome();

    boolean isGameOver();

    char getCurrentTurnSymbol();

    int getMoveCount();

    char getCell(int row, int col);

    String getBoardString();

    int[] getWinningLine();

    List<Move> getMoveHistory();

    void forfeit(char forfeitingSymbol);

    void reset(char startingSymbol);
}
