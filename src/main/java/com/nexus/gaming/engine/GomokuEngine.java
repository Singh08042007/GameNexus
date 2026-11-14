package com.nexus.gaming.engine;

import com.nexus.gaming.model.GameType;

/**
 * Server-Authoritative 9x9 Gomoku (Five-in-a-Row) Strategy Engine.
 * Players place stones on a 9x9 board; first to align 5 consecutive stones wins.
 */
public class GomokuEngine extends AbstractBoardGame {

    private static final int BOARD_SIZE = 9;
    private static final int WIN_LENGTH = 5;

    public GomokuEngine() {
        this('X');
    }

    public GomokuEngine(char startingSymbol) {
        super(GameType.GOMOKU, BOARD_SIZE, BOARD_SIZE, WIN_LENGTH, startingSymbol);
    }
}

