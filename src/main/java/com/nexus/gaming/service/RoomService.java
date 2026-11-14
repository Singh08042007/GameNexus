package com.nexus.gaming.service;

import com.nexus.gaming.dto.DtoMapper;
import com.nexus.gaming.engine.GameEngine;
import com.nexus.gaming.engine.GameEngineFactory;
import com.nexus.gaming.exception.AuthorizationException;
import com.nexus.gaming.exception.ResourceNotFoundException;
import com.nexus.gaming.exception.RoomException;
import com.nexus.gaming.model.ChatMessage;
import com.nexus.gaming.model.GameType;
import com.nexus.gaming.model.Match;
import com.nexus.gaming.model.MatchOutcome;
import com.nexus.gaming.model.Player;
import com.nexus.gaming.model.Room;
import com.nexus.gaming.model.RoomStatus;
import com.nexus.gaming.model.User;
import com.nexus.gaming.repository.DataRepository;
import com.nexus.gaming.util.SecurityUtil;
import com.nexus.gaming.websocket.WebSocketHub;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service managing multiplayer rooms, game selection, in-room live chat,
 * player participation, capacity enforcement, duplicate join handling, and disconnection recovery.
 */
public class RoomService {

    private static final int ROOM_CODE_LENGTH = 6;

    private final DataRepository repository;
    private final WebSocketHub webSocketHub;

    public RoomService(DataRepository repository, WebSocketHub webSocketHub) {
        this.repository = repository;
        this.webSocketHub = webSocketHub;
    }

    public Room createRoom(User hostUser, String requestedRoomName) {
        return createRoom(hostUser, requestedRoomName, GameType.TIC_TAC_TOE.name());
    }

    public Room createRoom(User hostUser, String requestedRoomName, String requestedGameType) {
        if (hostUser == null) {
            throw new AuthorizationException("Authenticated user required to create a room.");
        }

        GameType gameType = GameType.fromString(requestedGameType);
        String roomCode = generateUniqueRoomCode();
        String roomName = (requestedRoomName == null || requestedRoomName.trim().isEmpty())
                ? hostUser.getUsername() + "'s " + gameType.getDisplayName() + " Room"
                : requestedRoomName.trim();
        if (roomName.length() > 48) {
            roomName = roomName.substring(0, 48);
        }

        GameEngine engine = GameEngineFactory.createEngine(gameType, 'X');
        Room room = new Room(roomCode, roomName, gameType, hostUser, engine);
        room.addChatMessage(ChatMessage.systemMessage(
                roomCode,
                hostUser.getUsername() + " opened the " + gameType.getDisplayName() + " arena. Share code " + roomCode + " to invite a challenger."
        ));
        repository.saveRoom(room);

        Map<String, Object> roomDto = buildRoomDto(room);
        webSocketHub.broadcastLobbyUpdate("LOBBY_ROOMS_CHANGED", roomDto);
        return room;
    }

    public Room joinRoom(User joiningUser, String rawRoomCode) {
        if (joiningUser == null) {
            throw new AuthorizationException("Authenticated user required to join a room.");
        }
        if (rawRoomCode == null || rawRoomCode.trim().isEmpty()) {
            throw new RoomException("Room ID is required to join a room.", 400, "MISSING_ROOM_CODE");
        }

        String roomCode = rawRoomCode.trim().toUpperCase();
        Room room = repository.findRoomByCode(roomCode)
                .orElseThrow(() -> new ResourceNotFoundException("Room '" + roomCode + "' does not exist."));

        ReentrantLock lock = room.getLock();
        lock.lock();
        try {
            if (room.getStatus() == RoomStatus.ABANDONED) {
                throw new RoomException("Room '" + roomCode + "' has already been closed.", 410, "ROOM_ABANDONED");
            }

            // Check if the user is already in this room (reconnection / duplicate join handling)
            if (room.hasPlayer(joiningUser.getId())) {
                Player existingPlayer = room.getPlayerByUserId(joiningUser.getId());
                if (existingPlayer != null) {
                    existingPlayer.setConnected(true);
                    repository.saveRoom(room);
                    Map<String, Object> dto = buildRoomDto(room);
                    webSocketHub.broadcastToRoom(roomCode, "PLAYER_RECONNECTED", dto);
                    return room;
                }
            }

            // Enforce 2-player maximum capacity
            if (room.isFull()) {
                throw new RoomException(
                        "Room '" + roomCode + "' is full (2/2 players). Cannot join.",
                        409,
                        "ROOM_FULL"
                );
            }

            if (room.getStatus() != RoomStatus.WAITING) {
                throw new RoomException(
                        "Room '" + roomCode + "' is no longer accepting new players.",
                        409,
                        "ROOM_NOT_WAITING"
                );
            }

            // Assign joining player to available slot (Player O if Player X is taken)
            if (room.getPlayerX() == null) {
                room.setPlayerX(new Player(joiningUser, 'X'));
            } else {
                room.setPlayerO(new Player(joiningUser, 'O'));
            }

            room.addChatMessage(ChatMessage.systemMessage(
                    roomCode,
                    joiningUser.getUsername() + " joined as Player O. Match started—Player X has the opening move!"
            ));

            // When both Player X and Player O are present, automatically start the match
            if (room.getPlayerX() != null && room.getPlayerO() != null) {
                startNewMatchInRoom(room, 'X');
            } else {
                repository.saveRoom(room);
            }

            Map<String, Object> dto = buildRoomDto(room);
            webSocketHub.broadcastToRoom(roomCode, "MATCH_STARTED", dto);
            webSocketHub.broadcastLobbyUpdate("LOBBY_ROOMS_CHANGED", dto);
            return room;
        } finally {
            lock.unlock();
        }
    }

    public Map<String, Object> sendChatMessage(User sender, String rawRoomCode, String rawMessage) {
        if (sender == null) {
            throw new AuthorizationException("Authentication is required to send chat messages.");
        }
        if (rawRoomCode == null || rawRoomCode.trim().isEmpty()) {
            throw new RoomException("Room code is required.", 400, "MISSING_ROOM_CODE");
        }
        if (rawMessage == null || rawMessage.trim().isEmpty()) {
            throw new RoomException("Chat message cannot be empty.", 400, "EMPTY_CHAT_MESSAGE");
        }

        String roomCode = rawRoomCode.trim().toUpperCase();
        Room room = repository.findRoomByCode(roomCode)
                .orElseThrow(() -> new ResourceNotFoundException("Room '" + roomCode + "' not found."));

        ReentrantLock lock = room.getLock();
        lock.lock();
        try {
            Player participant = room.getPlayerByUserId(sender.getId());
            if (participant == null) {
                throw new AuthorizationException("Only players inside room '" + roomCode + "' can send chat messages.");
            }

            String cleanText = rawMessage.trim();
            if (cleanText.length() > 280) {
                cleanText = cleanText.substring(0, 280);
            }

            ChatMessage chatMessage = new ChatMessage(
                    roomCode,
                    sender.getId(),
                    sender.getUsername(),
                    String.valueOf(participant.getSymbol()),
                    cleanText,
                    "CHAT"
            );
            room.addChatMessage(chatMessage);
            repository.saveRoom(room);

            Map<String, Object> chatDto = DtoMapper.toChatMessageDto(chatMessage);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("chatMessage", chatDto);
            payload.put("room", buildRoomDto(room));

            webSocketHub.broadcastToRoom(roomCode, "CHAT_MESSAGE", payload);
            return payload;
        } finally {
            lock.unlock();
        }
    }

    public Room leaveRoom(User user, String rawRoomCode) {
        if (rawRoomCode == null || rawRoomCode.trim().isEmpty()) {
            throw new RoomException("Room code is required.", 400, "MISSING_ROOM_CODE");
        }
        String roomCode = rawRoomCode.trim().toUpperCase();
        Room room = repository.findRoomByCode(roomCode)
                .orElseThrow(() -> new ResourceNotFoundException("Room '" + roomCode + "' not found."));

        ReentrantLock lock = room.getLock();
        lock.lock();
        try {
            if (!room.hasPlayer(user.getId())) {
                throw new AuthorizationException("You are not a participant in room '" + roomCode + "'.");
            }

            Player leavingPlayer = room.getPlayerByUserId(user.getId());
            if (room.getStatus() == RoomStatus.IN_PROGRESS && room.getGameEngine() != null
                    && !room.getGameEngine().isGameOver()) {
                // Leaving an active match results in forfeit; opponent wins
                handlePlayerForfeit(room, leavingPlayer);
                room.addChatMessage(ChatMessage.systemMessage(
                        roomCode,
                        user.getUsername() + " left the active match (Forfeit)."
                ));
            } else if (room.getStatus() == RoomStatus.WAITING) {
                room.setStatus(RoomStatus.ABANDONED);
                if (leavingPlayer != null) {
                    leavingPlayer.setConnected(false);
                }
            } else {
                if (leavingPlayer != null) {
                    leavingPlayer.setConnected(false);
                }
                room.setStatus(RoomStatus.ABANDONED);
            }

            repository.saveRoom(room);
            Map<String, Object> dto = buildRoomDto(room);
            webSocketHub.broadcastToRoom(roomCode, "PLAYER_LEFT", dto);
            webSocketHub.broadcastLobbyUpdate("LOBBY_ROOMS_CHANGED", dto);
            return room;
        } finally {
            lock.unlock();
        }
    }

    public void handleWebSocketDisconnect(String roomCode, String userId) {
        if (roomCode == null || userId == null) return;
        Optional<Room> roomOpt = repository.findRoomByCode(roomCode);
        if (!roomOpt.isPresent()) return;

        Room room = roomOpt.get();
        ReentrantLock lock = room.getLock();
        lock.lock();
        try {
            Player player = room.getPlayerByUserId(userId);
            if (player == null) return;

            // Check if the user still has another open WebSocket tab in this room
            if (webSocketHub.hasActiveRoomConnection(roomCode, userId)) {
                return;
            }

            player.setConnected(false);
            repository.saveRoom(room);
            Map<String, Object> dto = buildRoomDto(room);
            webSocketHub.broadcastToRoom(roomCode, "PLAYER_DISCONNECTED", dto);
        } finally {
            lock.unlock();
        }
    }

    public Room getRoomByCode(String rawRoomCode) {
        if (rawRoomCode == null || rawRoomCode.trim().isEmpty()) {
            throw new RoomException("Room code is required.", 400, "MISSING_ROOM_CODE");
        }
        String code = rawRoomCode.trim().toUpperCase();
        return repository.findRoomByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Room '" + code + "' not found."));
    }

    public List<Map<String, Object>> listAvailableAndActiveRooms(String currentUserId) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Room room : repository.findAllRooms()) {
            boolean isParticipant = room.hasPlayer(currentUserId);
            if (room.getStatus() == RoomStatus.WAITING
                    || room.getStatus() == RoomStatus.IN_PROGRESS
                    || isParticipant) {
                list.add(buildRoomDto(room));
            }
            if (list.size() >= 25) break;
        }
        return list;
    }

    public Match startNewMatchInRoom(Room room, char startingSymbol) {
        GameType gameType = room.getGameType() != null ? room.getGameType() : GameType.TIC_TAC_TOE;
        if (room.getGameEngine() == null || room.getGameEngine().getGameType() != gameType) {
            room.setGameEngine(GameEngineFactory.createEngine(gameType, startingSymbol));
        } else {
            room.getGameEngine().reset(startingSymbol);
        }
        if (room.getPlayerX() != null) room.getPlayerX().setReadyForRematch(false);
        if (room.getPlayerO() != null) room.getPlayerO().setReadyForRematch(false);

        Match match = new Match(room.getId(), room.getRoomCode(), gameType, room.getPlayerX(), room.getPlayerO());
        repository.saveMatch(match);
        room.setCurrentMatchId(match.getId());
        room.setStatus(RoomStatus.IN_PROGRESS);
        repository.saveRoom(room);
        return match;
    }

    private void handlePlayerForfeit(Room room, Player forfeitingPlayer) {
        forfeitingPlayer.setConnected(false);
        room.getGameEngine().forfeit(forfeitingPlayer.getSymbol());
        room.setStatus(RoomStatus.COMPLETED);

        Player winnerPlayer = (forfeitingPlayer.getSymbol() == 'X') ? room.getPlayerO() : room.getPlayerX();
        Match activeMatch = room.getCurrentMatchId() != null
                ? repository.findMatchById(room.getCurrentMatchId()).orElse(null)
                : null;

        if (activeMatch != null && winnerPlayer != null) {
            activeMatch.completeMatch(
                    MatchOutcome.FORFEIT,
                    winnerPlayer.getId(),
                    winnerPlayer.getUsername(),
                    room.getGameEngine().getBoardString()
            );
            repository.saveMatch(activeMatch);
        }

        if (winnerPlayer != null) {
            repository.findById(winnerPlayer.getId()).ifPresent(u -> {
                u.recordWin();
                winnerPlayer.setWins(u.getWins());
                repository.save(u);
            });
        }
        repository.findById(forfeitingPlayer.getId()).ifPresent(u -> {
            u.recordLoss();
            forfeitingPlayer.setLosses(u.getLosses());
            repository.save(u);
        });
    }

    public Map<String, Object> buildRoomDto(Room room) {
        Match activeMatch = null;
        if (room.getCurrentMatchId() != null) {
            activeMatch = repository.findMatchById(room.getCurrentMatchId()).orElse(null);
        }
        return DtoMapper.toRoomDto(room, activeMatch);
    }

    private String generateUniqueRoomCode() {
        for (int attempt = 0; attempt < 50; attempt++) {
            String candidate = SecurityUtil.generateRoomCode(ROOM_CODE_LENGTH);
            if (!repository.findRoomByCode(candidate).isPresent()) {
                return candidate;
            }
        }
        return SecurityUtil.generateRoomCode(ROOM_CODE_LENGTH + 2);
    }
}
