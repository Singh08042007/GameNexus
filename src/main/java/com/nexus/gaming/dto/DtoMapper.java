package com.nexus.gaming.dto;

import com.nexus.gaming.engine.GameAnalyzer;
import com.nexus.gaming.engine.GameEngine;
import com.nexus.gaming.model.ChatMessage;
import com.nexus.gaming.model.GameType;
import com.nexus.gaming.model.Match;
import com.nexus.gaming.model.Move;
import com.nexus.gaming.model.Player;
import com.nexus.gaming.model.Room;
import com.nexus.gaming.model.User;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object (DTO) builder and mapper layer.
 * Ensures internal domain objects and sensitive fields (such as passwordHash)
 * are never exposed directly in HTTP or WebSocket responses.
 */
public final class DtoMapper {

    private DtoMapper() {
    }

    public static Map<String, Object> successResponse(String message, Object data) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("message", message);
        res.put("data", data);
        res.put("timestamp", Instant.now().toString());
        return res;
    }

    public static Map<String, Object> errorResponse(String message, String errorCode) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", false);
        res.put("message", message);
        res.put("errorCode", errorCode);
        res.put("timestamp", Instant.now().toString());
        return res;
    }

    public static List<Map<String, Object>> toGameCatalogDto() {
        List<Map<String, Object>> catalog = new ArrayList<>();
        for (GameType gt : GameType.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", gt.name());
            item.put("displayName", gt.getDisplayName());
            item.put("category", gt.getCategory());
            item.put("description", gt.getDescription());
            item.put("rows", gt.getRows());
            item.put("cols", gt.getCols());
            item.put("winLength", gt.getWinLength());
            item.put("maxPlayers", gt.getMaxPlayers());
            catalog.add(item);
        }
        return catalog;
    }

    public static Map<String, Object> toUserDto(User user) {
        if (user == null) return null;
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", user.getId());
        dto.put("email", user.getEmail());
        dto.put("username", user.getUsername());
        dto.put("wins", user.getWins());
        dto.put("losses", user.getLosses());
        dto.put("draws", user.getDraws());
        dto.put("totalMatches", user.getTotalMatches());
        dto.put("createdAt", user.getCreatedAt());
        return dto;
    }

    public static Map<String, Object> toPlayerDto(Player player) {
        if (player == null) return null;
        Map<String, Object> dto = toUserDto(player);
        dto.put("symbol", String.valueOf(player.getSymbol()));
        dto.put("connected", player.isConnected());
        dto.put("readyForRematch", player.isReadyForRematch());
        return dto;
    }

    public static Map<String, Object> toMoveDto(Move move) {
        if (move == null) return null;
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("matchId", move.getMatchId());
        dto.put("playerId", move.getPlayerId());
        dto.put("playerUsername", move.getPlayerUsername());
        dto.put("symbol", String.valueOf(move.getSymbol()));
        dto.put("row", move.getRow());
        dto.put("col", move.getCol());
        dto.put("moveNumber", move.getMoveNumber());
        dto.put("boardStateAfter", move.getBoardStateAfter());
        dto.put("createdAt", move.getCreatedAt());
        return dto;
    }

    public static Map<String, Object> toChatMessageDto(ChatMessage chat) {
        if (chat == null) return null;
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", chat.getId());
        dto.put("roomCode", chat.getRoomCode());
        dto.put("senderId", chat.getSenderId());
        dto.put("senderUsername", chat.getSenderUsername());
        dto.put("senderSymbol", chat.getSenderSymbol());
        dto.put("message", chat.getMessage());
        dto.put("messageType", chat.getMessageType());
        dto.put("createdAt", chat.getCreatedAt());
        return dto;
    }

    public static Map<String, Object> toRoomDto(Room room, Match activeMatch) {
        if (room == null) return null;
        GameType gt = room.getGameType() != null ? room.getGameType() : GameType.TIC_TAC_TOE;
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", room.getId());
        dto.put("roomCode", room.getRoomCode());
        dto.put("roomName", room.getRoomName());
        dto.put("gameType", gt.name());
        dto.put("gameDisplayName", gt.getDisplayName());
        dto.put("gameCategory", gt.getCategory());
        dto.put("gameDescription", gt.getDescription());
        dto.put("rows", gt.getRows());
        dto.put("cols", gt.getCols());
        dto.put("winLength", gt.getWinLength());
        dto.put("status", room.getStatus().name());
        dto.put("playerCount", room.getCurrentPlayerCount());
        dto.put("maxPlayers", room.getMaxPlayers());
        dto.put("playerX", toPlayerDto(room.getPlayerX()));
        dto.put("playerO", toPlayerDto(room.getPlayerO()));
        dto.put("currentMatchId", room.getCurrentMatchId());
        dto.put("createdAt", room.getCreatedAt());
        dto.put("updatedAt", room.getUpdatedAt());

        List<Map<String, Object>> chatDtos = new ArrayList<>();
        for (ChatMessage cm : room.getChatMessages()) {
            chatDtos.add(toChatMessageDto(cm));
        }
        dto.put("chatMessages", chatDtos);

        GameEngine engine = room.getGameEngine();
        if (engine != null) {
            Map<String, Object> gameState = new LinkedHashMap<>();
            gameState.put("board", engine.getBoardString());
            gameState.put("rows", engine.getRows());
            gameState.put("cols", engine.getCols());
            gameState.put("winLength", engine.getWinLength());
            gameState.put("currentTurn", String.valueOf(engine.getCurrentTurnSymbol()));
            gameState.put("moveCount", engine.getMoveCount());
            gameState.put("outcome", engine.checkOutcome().name());
            gameState.put("gameOver", engine.isGameOver());
            gameState.put("winningLine", engine.getWinningLine());
            List<Move> history = engine.getMoveHistory();
            List<Map<String, Object>> moveDtos = new ArrayList<>();
            for (Move m : history) {
                moveDtos.add(toMoveDto(m));
            }
            gameState.put("moves", moveDtos);
            if (activeMatch != null) {
                gameState.put("matchId", activeMatch.getId());
                gameState.put("winnerId", activeMatch.getWinnerId());
                gameState.put("winnerName", activeMatch.getWinnerName());
                gameState.put("startedAt", activeMatch.getStartedAt());
                gameState.put("endedAt", activeMatch.getEndedAt());
            }

            String pxName = room.getPlayerX() != null ? room.getPlayerX().getUsername() : "Player X";
            String poName = room.getPlayerO() != null ? room.getPlayerO().getUsername() : "Player O";
            gameState.put("analysis", GameAnalyzer.analyze(gt, history, engine.checkOutcome(), pxName, poName));
            dto.put("gameState", gameState);
        }
        return dto;
    }

    public static Map<String, Object> toMatchDto(Match match) {
        if (match == null) return null;
        GameType gt = match.getGameType() != null ? match.getGameType() : GameType.TIC_TAC_TOE;
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", match.getId());
        dto.put("roomId", match.getRoomId());
        dto.put("roomCode", match.getRoomCode());
        dto.put("gameType", gt.name());
        dto.put("gameDisplayName", gt.getDisplayName());
        dto.put("rows", gt.getRows());
        dto.put("cols", gt.getCols());
        dto.put("winLength", gt.getWinLength());
        dto.put("playerXId", match.getPlayerXId());
        dto.put("playerXName", match.getPlayerXName());
        dto.put("playerOId", match.getPlayerOId());
        dto.put("playerOName", match.getPlayerOName());
        dto.put("winnerId", match.getWinnerId());
        dto.put("winnerName", match.getWinnerName());
        dto.put("outcome", match.getOutcome().name());
        dto.put("finalBoard", match.getFinalBoard());
        dto.put("moveCount", match.getMoveCount());
        dto.put("startedAt", match.getStartedAt());
        dto.put("endedAt", match.getEndedAt());
        List<Move> moves = match.getMoves();
        List<Map<String, Object>> moveDtos = new ArrayList<>();
        for (Move m : moves) {
            moveDtos.add(toMoveDto(m));
        }
        dto.put("moves", moveDtos);
        dto.put("analysis", GameAnalyzer.analyze(
                gt,
                moves,
                match.getOutcome(),
                match.getPlayerXName(),
                match.getPlayerOName()
        ));
        return dto;
    }
}
