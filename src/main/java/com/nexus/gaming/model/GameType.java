package com.nexus.gaming.model;

/**
 * Supported multiplayer game types on the NEXUS Gaming Hub.
 * Encapsulates board dimensions, win conditions, and catalog metadata.
 */
public enum GameType {
    TIC_TAC_TOE(
            "Tic-Tac-Toe",
            2,
            3,
            3,
            3,
            "Classic 3x3 Grid",
            "Fast-paced 3x3 tactical duel. Align 3 markers horizontally, vertically, or diagonally."
    ),
    CONNECT_FOUR(
            "Connect Four",
            2,
            6,
            7,
            4,
            "Gravity 6x7 Drop",
            "Drop discs into a 6x7 vertical rack. Pieces fall to the lowest available row in that column—connect 4 to win."
    ),
    GOMOKU(
            "Gomoku (Five-in-a-Row)",
            2,
            9,
            9,
            5,
            "Strategy 9x9 Board",
            "Positional territory battle on a 9x9 grid. Build intersecting threats and align 5 consecutive stones."
    ),
    QUAD_TAC_TOE(
            "Quad-Tac-Toe 4x4",
            2,
            4,
            4,
            4,
            "Extended 4x4 Arena",
            "Expanded 16-cell tactical grid. Control the central 2x2 core and align 4 markers in any line."
    );

    private final String displayName;
    private final int maxPlayers;
    private final int rows;
    private final int cols;
    private final int winLength;
    private final String category;
    private final String description;

    GameType(String displayName, int maxPlayers, int rows, int cols, int winLength, String category, String description) {
        this.displayName = displayName;
        this.maxPlayers = maxPlayers;
        this.rows = rows;
        this.cols = cols;
        this.winLength = winLength;
        this.category = category;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public int getRows() {
        return rows;
    }

    public int getCols() {
        return cols;
    }

    public int getWinLength() {
        return winLength;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public static GameType fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return TIC_TAC_TOE;
        }
        String normalized = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        for (GameType type : values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        return TIC_TAC_TOE;
    }
}
