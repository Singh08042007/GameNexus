package com.nexus.gaming.engine;

import com.nexus.gaming.model.GameType;
import com.nexus.gaming.model.MatchOutcome;
import com.nexus.gaming.model.Move;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tactical Game Analysis Engine for NEXUS Multiplayer Games.
 * Evaluates move quality, threat creation, defensive blocks, board control share,
 * opening classification, and critical turning points across all supported GameTypes.
 */
public final class GameAnalyzer {

    private static final int[][] DIRECTIONS = new int[][]{
            {0, 1},  // Horizontal
            {1, 0},  // Vertical
            {1, 1},  // Main Diagonal
            {1, -1}  // Anti-Diagonal
    };

    private GameAnalyzer() {
    }

    public static Map<String, Object> analyze(GameType gameType,
                                              List<Move> moves,
                                              MatchOutcome outcome,
                                              String playerXName,
                                              String playerOName) {
        GameType type = (gameType != null) ? gameType : GameType.TIC_TAC_TOE;
        int rows = type.getRows();
        int cols = type.getCols();
        int winLength = type.getWinLength();
        String xName = (playerXName != null && !playerXName.trim().isEmpty()) ? playerXName : "Player X";
        String oName = (playerOName != null && !playerOName.trim().isEmpty()) ? playerOName : "Player O";

        char[][] board = new char[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                board[r][c] = AbstractBoardGame.EMPTY_CELL;
            }
        }

        List<Move> safeMoves = (moves != null) ? moves : new ArrayList<Move>();
        List<Map<String, Object>> moveEvaluations = new ArrayList<>();

        int xScoreSum = 0;
        int xMoveTotal = 0;
        int oScoreSum = 0;
        int oMoveTotal = 0;

        int threatsCreatedX = 0;
        int threatsCreatedO = 0;
        int blocksMadeX = 0;
        int blocksMadeO = 0;
        int centerMovesX = 0;
        int centerMovesO = 0;
        int mistakesX = 0;
        int mistakesO = 0;

        String turningPoint = "No decisive tactical swing recorded yet.";
        boolean turningPointLocked = false;

        for (int i = 0; i < safeMoves.size(); i++) {
            Move m = safeMoves.get(i);
            int r = m.getRow();
            int c = m.getCol();
            char sym = m.getSymbol();
            char opp = (sym == 'X') ? 'O' : 'X';
            String actorName = (sym == 'X') ? xName : oName;

            if (r < 0 || r >= rows || c < 0 || c >= cols) {
                continue;
            }

            // 1. Check immediate winning cells before this move
            List<int[]> oppWinningCellsBefore = findImmediateWinningCells(board, rows, cols, winLength, opp, type);
            boolean oppHadWinThreat = !oppWinningCellsBefore.isEmpty();
            boolean blockedOpponentWin = containsCoord(oppWinningCellsBefore, r, c);

            // Apply move to virtual board
            board[r][c] = sym;
            boolean wonImmediately = hasWinningLineAt(board, rows, cols, winLength, sym);

            // 2. Check threats created after this move
            List<int[]> myWinningCellsAfter = findImmediateWinningCells(board, rows, cols, winLength, sym, type);
            int threatsCreatedCount = myWinningCellsAfter.size();
            boolean isCenter = isCentralCell(r, c, rows, cols);

            String classification;
            String badgeLabel;
            int moveQuality;
            String commentary;

            if (wonImmediately) {
                classification = "WINNING_STRIKE";
                badgeLabel = "Winning Strike";
                moveQuality = 100;
                commentary = actorName + " completed the " + winLength + "-in-a-row line to seal the match.";
                if (!turningPointLocked) {
                    turningPoint = "Move #" + m.getMoveNumber() + " (" + formatCoord(type, r, c) + "): "
                            + actorName + " executed the winning alignment.";
                }
            } else if (oppHadWinThreat && !blockedOpponentWin) {
                classification = "MISSED_DEFENSE";
                badgeLabel = "Missed Block";
                moveQuality = 45;
                commentary = actorName + " left an immediate winning threat open for the opponent.";
                if (sym == 'X') {
                    mistakesX++;
                } else {
                    mistakesO++;
                }
                turningPoint = "Move #" + m.getMoveNumber() + " (" + formatCoord(type, r, c) + "): "
                        + actorName + " overlooked an urgent defensive block.";
                turningPointLocked = true;
            } else if (blockedOpponentWin) {
                classification = "CRITICAL_BLOCK";
                badgeLabel = "Defensive Save";
                moveQuality = 96;
                commentary = actorName + " neutralized the opponent's immediate match-point threat.";
                if (sym == 'X') {
                    blocksMadeX++;
                } else {
                    blocksMadeO++;
                }
                if (!turningPointLocked && threatsCreatedCount > 0) {
                    turningPoint = "Move #" + m.getMoveNumber() + " (" + formatCoord(type, r, c) + "): "
                            + actorName + " parried a lethal threat and counter-attacked.";
                }
            } else if (threatsCreatedCount >= 2) {
                classification = "DOUBLE_THREAT_FORK";
                badgeLabel = "Brilliant Fork";
                moveQuality = 100;
                commentary = actorName + " engineered a double-threat fork with two simultaneous winning paths.";
                if (sym == 'X') {
                    threatsCreatedX += threatsCreatedCount;
                } else {
                    threatsCreatedO += threatsCreatedCount;
                }
                turningPoint = "Move #" + m.getMoveNumber() + " (" + formatCoord(type, r, c) + "): "
                        + actorName + " created an unstoppable double-threat fork.";
                turningPointLocked = true;
            } else if (threatsCreatedCount == 1) {
                classification = "TACTICAL_THREAT";
                badgeLabel = "Attacking Threat";
                moveQuality = 92;
                commentary = actorName + " forced the opponent onto the defensive with a direct winning threat.";
                if (sym == 'X') {
                    threatsCreatedX++;
                } else {
                    threatsCreatedO++;
                }
            } else if (isCenter) {
                classification = "CENTER_CONTROL";
                badgeLabel = "Center Control";
                moveQuality = 90;
                commentary = actorName + " anchored a high-connectivity central coordinate.";
            } else {
                classification = "POSITIONAL_PLAY";
                badgeLabel = "Solid Develop";
                moveQuality = 82;
                commentary = actorName + " developed board geometry and opened new alignment lanes.";
            }

            if (isCenter) {
                if (sym == 'X') {
                    centerMovesX++;
                } else {
                    centerMovesO++;
                }
            }

            if (sym == 'X') {
                xScoreSum += moveQuality;
                xMoveTotal++;
            } else {
                oScoreSum += moveQuality;
                oMoveTotal++;
            }

            Map<String, Object> eval = new LinkedHashMap<>();
            eval.put("moveNumber", m.getMoveNumber());
            eval.put("playerUsername", m.getPlayerUsername() != null ? m.getPlayerUsername() : actorName);
            eval.put("symbol", String.valueOf(sym));
            eval.put("row", r);
            eval.put("col", c);
            eval.put("coordLabel", formatCoord(type, r, c));
            eval.put("classification", classification);
            eval.put("badgeLabel", badgeLabel);
            eval.put("qualityScore", moveQuality);
            eval.put("commentary", commentary);
            moveEvaluations.add(eval);
        }

        int accuracyX = (xMoveTotal > 0) ? Math.round((float) xScoreSum / xMoveTotal) : 100;
        int accuracyO = (oMoveTotal > 0) ? Math.round((float) oScoreSum / oMoveTotal) : 100;

        int[] controlShares = computeControlShares(
                outcome,
                accuracyX,
                accuracyO,
                threatsCreatedX,
                threatsCreatedO,
                centerMovesX,
                centerMovesO,
                safeMoves.size()
        );

        String openingName = classifyOpening(type, safeMoves);
        String summary = buildExecutiveSummary(
                type,
                outcome,
                xName,
                oName,
                safeMoves.size(),
                accuracyX,
                accuracyO,
                threatsCreatedX,
                threatsCreatedO,
                blocksMadeX,
                blocksMadeO,
                openingName
        );

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("gameType", type.name());
        report.put("gameDisplayName", type.getDisplayName());
        report.put("openingName", openingName);
        report.put("totalMoves", safeMoves.size());
        report.put("accuracyX", accuracyX);
        report.put("accuracyO", accuracyO);
        report.put("threatsCreatedX", threatsCreatedX);
        report.put("threatsCreatedO", threatsCreatedO);
        report.put("blocksMadeX", blocksMadeX);
        report.put("blocksMadeO", blocksMadeO);
        report.put("centerMovesX", centerMovesX);
        report.put("centerMovesO", centerMovesO);
        report.put("mistakesX", mistakesX);
        report.put("mistakesO", mistakesO);
        report.put("controlShareX", controlShares[0]);
        report.put("controlShareO", controlShares[1]);
        report.put("turningPoint", turningPoint);
        report.put("summary", summary);
        report.put("moveEvaluations", moveEvaluations);
        return report;
    }

    private static List<int[]> findImmediateWinningCells(char[][] board,
                                                         int rows,
                                                         int cols,
                                                         int winLength,
                                                         char symbol,
                                                         GameType gameType) {
        List<int[]> winningCells = new ArrayList<>();
        if (gameType == GameType.CONNECT_FOUR) {
            for (int c = 0; c < cols; c++) {
                int landingRow = -1;
                for (int r = rows - 1; r >= 0; r--) {
                    if (board[r][c] == AbstractBoardGame.EMPTY_CELL) {
                        landingRow = r;
                        break;
                    }
                }
                if (landingRow >= 0) {
                    board[landingRow][c] = symbol;
                    if (hasWinningLineAt(board, rows, cols, winLength, symbol)) {
                        winningCells.add(new int[]{landingRow, c});
                    }
                    board[landingRow][c] = AbstractBoardGame.EMPTY_CELL;
                }
            }
        } else {
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    if (board[r][c] == AbstractBoardGame.EMPTY_CELL) {
                        board[r][c] = symbol;
                        if (hasWinningLineAt(board, rows, cols, winLength, symbol)) {
                            winningCells.add(new int[]{r, c});
                        }
                        board[r][c] = AbstractBoardGame.EMPTY_CELL;
                    }
                }
            }
        }
        return winningCells;
    }

    private static boolean hasWinningLineAt(char[][] board, int rows, int cols, int winLength, char symbol) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (board[r][c] != symbol) {
                    continue;
                }
                for (int[] dir : DIRECTIONS) {
                    int dr = dir[0];
                    int dc = dir[1];
                    int endR = r + (winLength - 1) * dr;
                    int endC = c + (winLength - 1) * dc;
                    if (endR < 0 || endR >= rows || endC < 0 || endC >= cols) {
                        continue;
                    }
                    boolean match = true;
                    for (int step = 1; step < winLength; step++) {
                        if (board[r + step * dr][c + step * dc] != symbol) {
                            match = false;
                            break;
                        }
                    }
                    if (match) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean containsCoord(List<int[]> coords, int r, int c) {
        for (int[] pt : coords) {
            if (pt[0] == r && pt[1] == c) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCentralCell(int r, int c, int rows, int cols) {
        int rMin = Math.max(0, (rows / 2) - 1);
        int rMax = Math.min(rows - 1, (rows / 2) + (rows % 2 == 0 ? 0 : 1));
        int cMin = Math.max(0, (cols / 2) - 1);
        int cMax = Math.min(cols - 1, (cols / 2) + (cols % 2 == 0 ? 0 : 1));
        if (rows == 3 && cols == 3) {
            return r == 1 && c == 1;
        }
        return r >= rMin && r <= rMax && c >= cMin && c <= cMax;
    }

    private static String formatCoord(GameType type, int r, int c) {
        if (type == GameType.CONNECT_FOUR) {
            return "Col " + (c + 1) + " · Row " + (r + 1);
        }
        return "R" + (r + 1) + "·C" + (c + 1);
    }

    private static String classifyOpening(GameType type, List<Move> moves) {
        if (moves == null || moves.isEmpty()) {
            return "Awaiting Opening Move";
        }
        Move first = moves.get(0);
        int r = first.getRow();
        int c = first.getCol();
        if (type == GameType.CONNECT_FOUR) {
            if (c == 3) return "Central Column Dominance (Col 4)";
            if (c == 2 || c == 4) return "Inner Flank Drop (Col " + (c + 1) + ")";
            return "Wing Column Opening (Col " + (c + 1) + ")";
        }
        if (type == GameType.TIC_TAC_TOE) {
            if (r == 1 && c == 1) return "Center Sovereign Opening";
            if ((r == 0 || r == 2) && (c == 0 || c == 2)) return "Corner Diagonal Gambit";
            return "Edge Orthogonal Opening";
        }
        if (isCentralCell(r, c, type.getRows(), type.getCols())) {
            return "Core Center Control Opening";
        }
        return "Perimeter Flank Development";
    }

    private static int[] computeControlShares(MatchOutcome outcome,
                                              int accuracyX,
                                              int accuracyO,
                                              int threatsX,
                                              int threatsO,
                                              int centerX,
                                              int centerO,
                                              int moveCount) {
        if (outcome == MatchOutcome.X_WON) {
            return new int[]{78, 22};
        }
        if (outcome == MatchOutcome.O_WON) {
            return new int[]{22, 78};
        }
        if (outcome == MatchOutcome.DRAW || moveCount == 0) {
            return new int[]{50, 50};
        }
        double scoreX = accuracyX + (threatsX * 14.0) + (centerX * 8.0);
        double scoreO = accuracyO + (threatsO * 14.0) + (centerO * 8.0);
        double total = scoreX + scoreO;
        if (total <= 0) {
            return new int[]{50, 50};
        }
        int shareX = (int) Math.round((scoreX / total) * 100.0);
        shareX = Math.max(18, Math.min(82, shareX));
        return new int[]{shareX, 100 - shareX};
    }

    private static String buildExecutiveSummary(GameType type,
                                                MatchOutcome outcome,
                                                String xName,
                                                String oName,
                                                int totalMoves,
                                                int accX,
                                                int accO,
                                                int threatsX,
                                                int threatsO,
                                                int blocksX,
                                                int blocksO,
                                                String openingName) {
        if (totalMoves == 0) {
            return "Match initialized in " + type.getDisplayName() + ". Make the first move to begin live tactical telemetry.";
        }
        String base = "Opened with " + openingName + " across " + totalMoves + " moves. ";
        if (outcome == MatchOutcome.X_WON) {
            return base + xName + " (X) dictated the tempo with " + accX + "% move accuracy, generating "
                    + threatsX + " winning threat(s) and converting the decisive line.";
        }
        if (outcome == MatchOutcome.O_WON) {
            return base + oName + " (O) seized control with " + accO + "% move accuracy, executing "
                    + blocksO + " defensive save(s) and " + threatsO + " counter-threat(s) to claim victory.";
        }
        if (outcome == MatchOutcome.DRAW) {
            return base + "Both players maintained high defensive discipline (" + accX + "% vs " + accO
                    + "% accuracy) with " + (blocksX + blocksO) + " combined blocks, resulting in a balanced stalemate.";
        }
        if (outcome == MatchOutcome.FORFEIT) {
            return base + "Match concluded by forfeit after " + totalMoves + " moves.";
        }
        return base + xName + " (" + accX + "% accuracy, " + threatsX + " threats) and "
                + oName + " (" + accO + "% accuracy, " + blocksO + " blocks) are locked in an active tactical battle.";
    }
}

