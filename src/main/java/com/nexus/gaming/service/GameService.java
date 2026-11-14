package com.nexus.gaming.service;

import com.nexus.gaming.dto.DtoMapper;
import com.nexus.gaming.engine.GameEngine;
import com.nexus.gaming.exception.AuthorizationException;
import com.nexus.gaming.exception.InvalidMoveException;
import com.nexus.gaming.exception.ResourceNotFoundException;
import com.nexus.gaming.exception.RoomException;
import com.nexus.gaming.model.ChatMessage;
import com.nexus.gaming.model.Match;
import com.nexus.gaming.model.MatchOutcome;
import com.nexus.gaming.model.Move;
import com.nexus.gaming.model.Player;
import com.nexus.gaming.model.Room;
import com.nexus.gaming.model.RoomStatus;
import com.nexus.gaming.model.User;
import com.nexus.gaming.repository.DataRepository;
import com.nexus.gaming.websocket.WebSocketHub;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Server-Authoritative Game Service.
 * Validates player identity, room membership, room status, turn order, and cell availability
 * inside a per-room ReentrantLock critical section before applying any move.
 */
public class GameService {

    private final DataRepository repository;
    private final RoomService roomService;
    private final WebSocketHub webSocketHub;

    public GameService(DataRepository repository, RoomService roomService, WebSocketHub webSocketHub) {
        this.repository = repository;
        this.roomService = roomService;
        this.webSocketHub = webSocketHub;
    }

    public Map<String, Object> submitMove(User authenticatedUser, String rawRoomCode, int row, int col) {
        if (authenticatedUser == null) {
            throw new AuthorizationException("Authentication is required to make a move.");
        }
        if (rawRoomCode == null || rawRoomCode.trim().isEmpty()) {
            throw new RoomException("Room code is required.", 400, "MISSING_ROOM_CODE");
        }

        String roomCode = rawRoomCode.trim().toUpperCase();
        Room room = repository.findRoomByCode(roomCode)
                .orElseThrow(() -> new ResourceNotFoundException("Room '" + roomCode + "' not found."));

        ReentrantLock lock = room.getLock();
        lock.lock();
        try {
            // 1. Validate Room Membership (prevent unauthorized access or player impersonation)
            Player player = room.getPlayerByUserId(authenticatedUser.getId());
            if (player == null) {
                throw new AuthorizationException(
                        "You are not a registered player in room '" + roomCode + "'."
                );
            }

            // 2. Validate Game Status
            if (room.getStatus() == RoomStatus.WAITING) {
                throw new InvalidMoveException(
                        "Cannot make a move while waiting for a second player to join.",
                        "WAITING_FOR_OPPONENT"
                );
            }
            if (room.getStatus() != RoomStatus.IN_PROGRESS) {
                throw new InvalidMoveException(
                        "Cannot make a move: match is currently " + room.getStatus() + ".",
                        "MATCH_NOT_ACTIVE"
                );
            }

            GameEngine engine = room.getGameEngine();
            if (engine == null) {
                throw new InvalidMoveException("Game engine is not initialized for this room.", "ENGINE_MISSING");
            }

            Match activeMatch = room.getCurrentMatchId() != null
                    ? repository.findMatchById(room.getCurrentMatchId()).orElse(null)
                    : null;
            String matchId = activeMatch != null ? activeMatch.getId() : room.getId();

            // 3. Server-Authoritative Move Validation & Execution
            Move move = engine.applyMove(
                    matchId,
                    player.getId(),
                    player.getUsername(),
                    player.getSymbol(),
                    row,
                    col
            );

            player.setConnected(true);

            // 4. Persist move and evaluate match completion
            if (activeMatch != null) {
                activeMatch.addMove(move);
            }

            MatchOutcome outcome = engine.checkOutcome();
            if (outcome != MatchOutcome.IN_PROGRESS) {
                finalizeCompletedMatch(room, activeMatch, outcome, engine.getBoardString());
            } else {
                repository.recordMovePersistence(activeMatch, move);
                repository.saveRoom(room);
            }

            Map<String, Object> roomDto = roomService.buildRoomDto(room);
            String eventType = (outcome == MatchOutcome.IN_PROGRESS) ? "MOVE_MADE" : "MATCH_ENDED";
            webSocketHub.broadcastToRoom(roomCode, eventType, roomDto);
            if (outcome != MatchOutcome.IN_PROGRESS) {
                webSocketHub.broadcastLobbyUpdate("LOBBY_ROOMS_CHANGED", roomDto);
            }
            return roomDto;
        } finally {
            lock.unlock();
        }
    }

    public Map<String, Object> requestRematch(User authenticatedUser, String rawRoomCode) {
        if (authenticatedUser == null) {
            throw new AuthorizationException("Authentication is required to request a rematch.");
        }
        String roomCode = (rawRoomCode == null) ? "" : rawRoomCode.trim().toUpperCase();
        Room room = repository.findRoomByCode(roomCode)
                .orElseThrow(() -> new ResourceNotFoundException("Room '" + roomCode + "' not found."));

        ReentrantLock lock = room.getLock();
        lock.lock();
        try {
            Player player = room.getPlayerByUserId(authenticatedUser.getId());
            if (player == null) {
                throw new AuthorizationException("You are not a player in room '" + roomCode + "'.");
            }
            if (room.getPlayerX() == null || room.getPlayerO() == null) {
                throw new RoomException("Both players must be present in the room for a rematch.", 400, "OPPONENT_LEFT");
            }
            if (room.getStatus() == RoomStatus.IN_PROGRESS
                    && room.getGameEngine() != null
                    && !room.getGameEngine().isGameOver()) {
                throw new RoomException("Current match is still in progress.", 409, "MATCH_STILL_ACTIVE");
            }

            // Alternate starting player or reset immediately for instant rematch
            char nextStarter = 'X';
            if (room.getCurrentMatchId() != null) {
                Match prev = repository.findMatchById(room.getCurrentMatchId()).orElse(null);
                if (prev != null && prev.getOutcome() == MatchOutcome.X_WON) {
                    nextStarter = 'O'; // Challenger starts next round after X wins
                }
            }

            room.addChatMessage(ChatMessage.systemMessage(
                    roomCode,
                    authenticatedUser.getUsername() + " initiated a rematch. Player " + nextStarter + " leads off!"
            ));

            roomService.startNewMatchInRoom(room, nextStarter);
            Map<String, Object> roomDto = roomService.buildRoomDto(room);
            webSocketHub.broadcastToRoom(roomCode, "REMATCH_STARTED", roomDto);
            webSocketHub.broadcastLobbyUpdate("LOBBY_ROOMS_CHANGED", roomDto);
            return roomDto;
        } finally {
            lock.unlock();
        }
    }

    public List<Map<String, Object>> getMatchHistoryForUser(String userId) {
        List<Map<String, Object>> history = new ArrayList<>();
        for (Match match : repository.findMatchesForUser(userId)) {
            history.add(DtoMapper.toMatchDto(match));
            if (history.size() >= 30) break;
        }
        return history;
    }

    public Map<String, Object> getMatchByIdDto(String matchId) {
        Match match = repository.findMatchById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match '" + matchId + "' not found."));
        return DtoMapper.toMatchDto(match);
    }

    public List<Map<String, Object>> getLeaderboard() {
        List<Map<String, Object>> leaderboard = new ArrayList<>();
        for (User user : repository.findAll()) {
            leaderboard.add(DtoMapper.toUserDto(user));
            if (leaderboard.size() >= 15) break;
        }
        return leaderboard;
    }

    private void finalizeCompletedMatch(Room room, Match activeMatch, MatchOutcome outcome, String finalBoard) {
        room.setStatus(RoomStatus.COMPLETED);
        Player px = room.getPlayerX();
        Player po = room.getPlayerO();

        String winnerId = null;
        String winnerName = null;

        if (outcome == MatchOutcome.X_WON && px != null) {
            winnerId = px.getId();
            winnerName = px.getUsername();
            updateUserStats(px, po, false);
            room.addChatMessage(ChatMessage.systemMessage(
                    room.getRoomCode(),
                    px.getUsername() + " (X) won the match! View the Game Analysis panel for full move telemetry."
            ));
        } else if (outcome == MatchOutcome.O_WON && po != null) {
            winnerId = po.getId();
            winnerName = po.getUsername();
            updateUserStats(po, px, false);
            room.addChatMessage(ChatMessage.systemMessage(
                    room.getRoomCode(),
                    po.getUsername() + " (O) won the match! View the Game Analysis panel for full move telemetry."
            ));
        } else if (outcome == MatchOutcome.DRAW) {
            updateUserStats(px, po, true);
            room.addChatMessage(ChatMessage.systemMessage(
                    room.getRoomCode(),
                    "Match ended in a draw! Check the Game Analysis breakdown below."
            ));
        }

        if (activeMatch != null) {
            activeMatch.completeMatch(outcome, winnerId, winnerName, finalBoard);
            Move lastMove = !room.getGameEngine().getMoveHistory().isEmpty()
                    ? room.getGameEngine().getMoveHistory().get(room.getGameEngine().getMoveHistory().size() - 1)
                    : null;
            repository.recordMovePersistence(activeMatch, lastMove);
        }
        repository.saveRoom(room);
    }

    private void updateUserStats(Player first, Player second, boolean isDraw) {
        if (isDraw) {
            if (first != null) {
                repository.findById(first.getId()).ifPresent(u -> {
                    u.recordDraw();
                    first.setDraws(u.getDraws());
                    repository.save(u);
                });
            }
            if (second != null) {
                repository.findById(second.getId()).ifPresent(u -> {
                    u.recordDraw();
                    second.setDraws(u.getDraws());
                    repository.save(u);
                });
            }
        } else {
            if (first != null) {
                repository.findById(first.getId()).ifPresent(u -> {
                    u.recordWin();
                    first.setWins(u.getWins());
                    repository.save(u);
                });
            }
            if (second != null) {
                repository.findById(second.getId()).ifPresent(u -> {
                    u.recordLoss();
                    second.setLosses(u.getLosses());
                    repository.save(u);
                });
            }
        }
    }
}
