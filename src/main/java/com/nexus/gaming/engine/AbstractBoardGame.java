package com.nexus.gaming.engine;

import com.nexus.gaming.exception.InvalidMoveException;
import com.nexus.gaming.model.GameType;
import com.nexus.gaming.model.MatchOutcome;
import com.nexus.gaming.model.Move;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Abstract base class for grid-based turn-based board games.
 * Demonstrates OOP Abstraction, Inheritance, Polymorphism, and Encapsulation.
 */
public abstract class AbstractBoardGame implements GameEngine {

    public static final char EMPTY_CELL = '-';

    protected final GameType gameType;
    protected final int rows;
    protected final int cols;
    protected final int winLength;
    protected final char[][] board;
    protected char currentTurnSymbol;
    protected int moveCount;
    protected MatchOutcome outcome;
    protected int[] winningLine;
    protected final List<Move> moveHistory;

    protected AbstractBoardGame(GameType gameType, int rows, int cols, int winLength, char startingSymbol) {
        this.gameType = gameType;
        this.rows = rows;
        this.cols = cols;
        this.winLength = winLength;
        this.board = new char[rows][cols];
        this.moveHistory = new ArrayList<>();
        initBoardState(startingSymbol);
    }

    protected AbstractBoardGame(int rows, int cols, char startingSymbol) {
        this(GameType.TIC_TAC_TOE, rows, cols, 3, startingSymbol);
    }

    private void initBoardState(char startingSymbol) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                board[r][c] = EMPTY_CELL;
            }
        }
        this.currentTurnSymbol = (startingSymbol == 'O') ? 'O' : 'X';
        this.moveCount = 0;
        this.outcome = MatchOutcome.IN_PROGRESS;
        this.winningLine = null;
        this.moveHistory.clear();
    }

    @Override
    public GameType getGameType() {
        return gameType;
    }

    @Override
    public int getRows() {
        return rows;
    }

    @Override
    public int getCols() {
        return cols;
    }

    @Override
    public int getWinLength() {
        return winLength;
    }

    @Override
    public void initializeBoard() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                board[r][c] = EMPTY_CELL;
            }
        }
    }

    @Override
    public void reset(char startingSymbol) {
        initBoardState(startingSymbol);
    }

    protected void validateTurnAndPlayer(String playerId, char playerSymbol) {
        if (playerId == null || playerId.trim().isEmpty()) {
            throw new InvalidMoveException("Player identity is required.", "MISSING_PLAYER_ID");
        }
        if (isGameOver()) {
            throw new InvalidMoveException("Cannot make a move: the match has already ended (" + outcome + ").", "GAME_ALREADY_OVER");
        }
        if (playerSymbol != 'X' && playerSymbol != 'O') {
            throw new InvalidMoveException("Invalid player symbol: " + playerSymbol, "INVALID_SYMBOL");
        }
        if (playerSymbol != currentTurnSymbol) {
            throw new InvalidMoveException(
                    "Not your turn! Waiting for Player " + currentTurnSymbol + " to move.",
                    "OUT_OF_TURN"
            );
        }
    }

    @Override
    public void validateMove(String playerId, char playerSymbol, int row, int col) {
        validateTurnAndPlayer(playerId, playerSymbol);
        if (row < 0 || row >= rows || col < 0 || col >= cols) {
            throw new InvalidMoveException(
                    "Coordinates (" + row + ", " + col + ") are outside the " + rows + "x" + cols + " board.",
                    "OUT_OF_BOUNDS"
            );
        }
        if (board[row][col] != EMPTY_CELL) {
            throw new InvalidMoveException(
                    "Cell (" + row + ", " + col + ") is already occupied by '" + board[row][col] + "'.",
                    "CELL_OCCUPIED"
            );
        }
    }

    @Override
    public Move applyMove(String matchId, String playerId, String playerUsername, char playerSymbol, int row, int col) {
        validateMove(playerId, playerSymbol, row, col);

        board[row][col] = playerSymbol;
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
                row,
                col,
                moveCount,
                getBoardString()
        );
        moveHistory.add(recordedMove);
        return recordedMove;
    }

    @Override
    public MatchOutcome checkOutcome() {
        if (outcome == MatchOutcome.FORFEIT) {
            return MatchOutcome.FORFEIT;
        }

        int[][] directions = new int[][]{
                {0, 1},  // Horizontal
                {1, 0},  // Vertical
                {1, 1},  // Main Diagonal
                {1, -1}  // Anti-Diagonal
        };

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                char symbol = board[r][c];
                if (symbol == EMPTY_CELL) {
                    continue;
                }
                for (int[] dir : directions) {
                    int dr = dir[0];
                    int dc = dir[1];
                    int endR = r + (winLength - 1) * dr;
                    int endC = c + (winLength - 1) * dc;
                    if (endR < 0 || endR >= rows || endC < 0 || endC >= cols) {
                        continue;
                    }
                    boolean match = true;
                    int[] line = new int[winLength];
                    for (int step = 0; step < winLength; step++) {
                        int nr = r + step * dr;
                        int nc = c + step * dc;
                        if (board[nr][nc] != symbol) {
                            match = false;
                            break;
                        }
                        line[step] = nr * cols + nc;
                    }
                    if (match) {
                        this.winningLine = line;
                        return (symbol == 'X') ? MatchOutcome.X_WON : MatchOutcome.O_WON;
                    }
                }
            }
        }

        if (moveCount >= rows * cols) {
            this.winningLine = null;
            return MatchOutcome.DRAW;
        }

        return MatchOutcome.IN_PROGRESS;
    }

    @Override
    public boolean isGameOver() {
        return outcome != MatchOutcome.IN_PROGRESS;
    }

    @Override
    public char getCurrentTurnSymbol() {
        return currentTurnSymbol;
    }

    @Override
    public int getMoveCount() {
        return moveCount;
    }

    @Override
    public char getCell(int row, int col) {
        if (row < 0 || row >= rows || col < 0 || col >= cols) {
            throw new IndexOutOfBoundsException("Invalid cell coordinates");
        }
        return board[row][col];
    }

    @Override
    public String getBoardString() {
        StringBuilder sb = new StringBuilder(rows * cols);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                sb.append(board[r][c]);
            }
        }
        return sb.toString();
    }

    @Override
    public int[] getWinningLine() {
        return winningLine == null ? null : winningLine.clone();
    }

    @Override
    public List<Move> getMoveHistory() {
        return Collections.unmodifiableList(new ArrayList<>(moveHistory));
    }

    @Override
    public void forfeit(char forfeitingSymbol) {
        if (!isGameOver()) {
            this.outcome = MatchOutcome.FORFEIT;
        }
    }
}
