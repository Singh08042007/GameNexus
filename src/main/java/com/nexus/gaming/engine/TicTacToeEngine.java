package com.nexus.gaming.engine;

import com.nexus.gaming.model.GameType;

/**
 * Server-Authoritative 3x3 Tic-Tac-Toe Engine.
 * Extends AbstractBoardGame and enforces 3-in-a-row win conditions on a 3x3 grid.
 */
public class TicTacToeEngine extends AbstractBoardGame {

    private static final int BOARD_SIZE = 3;
    private static final int WIN_LENGTH = 3;

    public TicTacToeEngine() {
        this('X');
    }

    public TicTacToeEngine(char startingSymbol) {
        super(GameType.TIC_TAC_TOE, BOARD_SIZE, BOARD_SIZE, WIN_LENGTH, startingSymbol);
    }
}
