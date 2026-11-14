package com.nexus.gaming.engine;

import com.nexus.gaming.exception.InvalidMoveException;
import com.nexus.gaming.model.GameType;
import com.nexus.gaming.model.Move;

/**
 * Server-Authoritative 6x7 Connect Four Gravity Engine.
 * Demonstrates OOP Method Overriding and Polymorphism: players select any column (0..6)
 * and the disc drops to the lowest unoccupied row in that column.
 */
public class ConnectFourEngine extends AbstractBoardGame {

    private static final int ROWS = 6;
    private static final int COLS = 7;
    private static final int WIN_LENGTH = 4;

    public ConnectFourEngine() {
        this('X');
    }

    public ConnectFourEngine(char startingSymbol) {
        super(GameType.CONNECT_FOUR, ROWS, COLS, WIN_LENGTH, startingSymbol);
    }

    /**
     * Finds the lowest available row in the given column, or -1 if the column is full.
     */
    public int findLowestAvailableRow(int col) {
        if (col < 0 || col >= cols) {
            return -1;
        }
        for (int r = rows - 1; r >= 0; r--) {
            if (board[r][col] == EMPTY_CELL) {
                return r;
            }
        }
        return -1;
    }

    @Override
    public void validateMove(String playerId, char playerSymbol, int row, int col) {
        validateTurnAndPlayer(playerId, playerSymbol);
        if (col < 0 || col >= cols) {
            throw new InvalidMoveException(
                    "Column " + (col + 1) + " is outside the 7-column Connect Four rack.",
                    "OUT_OF_BOUNDS"
            );
        }
        if (findLowestAvailableRow(col) < 0) {
            throw new InvalidMoveException(
                    "Column " + (col + 1) + " is already full. Choose another column.",
                    "COLUMN_FULL"
            );
        }
    }

    @Override
    public Move applyMove(String matchId, String playerId, String playerUsername, char playerSymbol, int row, int col) {
        validateMove(playerId, playerSymbol, row, col);
        int landingRow = findLowestAvailableRow(col);

        board[landingRow][col] = playerSymbol;
        moveCount++;

        this.outcome = checkOutcome();

        if (!isGameOver()) {
            currentTurnSymbol = (currentTurnSymbol == 'X') ? 'O' : 'X';
        }

        Move recordedMove = new Move(
                matchId,
                playerId,
                playerUsername,
                playerSymbol,
                landingRow,
                col,
                moveCount,
                getBoardString()
        );
        moveHistory.add(recordedMove);
        return recordedMove;
    }
}

