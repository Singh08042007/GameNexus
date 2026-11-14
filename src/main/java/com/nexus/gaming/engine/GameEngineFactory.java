package com.nexus.gaming.engine;

import com.nexus.gaming.model.GameType;

/**
 * Factory for instantiating polymorphic GameEngine implementations by GameType.
 */
public final class GameEngineFactory {

    private GameEngineFactory() {
    }

    public static GameEngine createEngine(GameType gameType, char startingSymbol) {
        if (gameType == null) {
            return new TicTacToeEngine(startingSymbol);
        }
        switch (gameType) {
            case CONNECT_FOUR:
                return new ConnectFourEngine(startingSymbol);
            case GOMOKU:
                return new GomokuEngine(startingSymbol);
            case QUAD_TAC_TOE:
                return new QuadTacToeEngine(startingSymbol);
            case TIC_TAC_TOE:
            default:
                return new TicTacToeEngine(startingSymbol);
        }
    }
}

