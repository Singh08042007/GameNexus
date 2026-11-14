package com.nexus.gaming.engine;

import com.nexus.gaming.model.GameType;

/**
 * Server-Authoritative 4x4 Quad-Tac-Toe Engine.
 * Players battle on a 16-cell grid to align 4 consecutive markers horizontally, vertically, or diagonally.
 */
public class QuadTacToeEngine extends AbstractBoardGame {

    private static final int BOARD_SIZE = 4;
    private static final int WIN_LENGTH = 4;

    public QuadTacToeEngine() {
        this('X');
    }

    public QuadTacToeEngine(char startingSymbol) {
        super(GameType.QUAD_TAC_TOE, BOARD_SIZE, BOARD_SIZE, WIN_LENGTH, startingSymbol);
    }
}

