package com.nexus.gaming.model;

import com.nexus.gaming.engine.GameEngine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Represents a multiplayer game room holding up to two players, an active GameEngine,
 * and a real-time room chat log.
 * Uses a per-room ReentrantLock to guarantee thread-safe state transitions and prevent race conditions.
 */
public class Room extends BaseEntity {

    private static final int MAX_CHAT_HISTORY = 100;

    private String roomCode;
    private String roomName;
    private GameType gameType;
    private Player playerX; // Host
    private Player playerO; // Challenger
    private RoomStatus status;
    private int maxPlayers;
    private String currentMatchId;
    private GameEngine gameEngine;
    private final List<ChatMessage> chatMessages = new ArrayList<>();
    private final transient ReentrantLock lock = new ReentrantLock(true);

    public Room() {
        super();
        this.gameType = GameType.TIC_TAC_TOE;
        this.status = RoomStatus.WAITING;
        this.maxPlayers = 2;
    }

    public Room(String roomCode, String roomName, User hostUser, GameEngine gameEngine) {
        this(roomCode, roomName, gameEngine != null ? gameEngine.getGameType() : GameType.TIC_TAC_TOE, hostUser, gameEngine);
    }

    public Room(String roomCode, String roomName, GameType gameType, User hostUser, GameEngine gameEngine) {
        super();
        this.roomCode = roomCode;
        this.roomName = roomName;
        this.gameType = (gameType != null) ? gameType : GameType.TIC_TAC_TOE;
        this.playerX = new Player(hostUser, 'X');
        this.status = RoomStatus.WAITING;
        this.maxPlayers = this.gameType.getMaxPlayers();
        this.gameEngine = gameEngine;
    }

    @Override
    public String getEntityType() {
        return "ROOM";
    }

    public ReentrantLock getLock() {
        return lock;
    }

    public synchronized void addChatMessage(ChatMessage message) {
        if (message == null) return;
        chatMessages.add(message);
        if (chatMessages.size() > MAX_CHAT_HISTORY) {
            chatMessages.remove(0);
        }
        touch();
    }

    public synchronized List<ChatMessage> getChatMessages() {
        return Collections.unmodifiableList(new ArrayList<>(chatMessages));
    }

    public int getCurrentPlayerCount() {
        int count = 0;
        if (playerX != null) count++;
        if (playerO != null) count++;
        return count;
    }

    public boolean isFull() {
        return getCurrentPlayerCount() >= maxPlayers;
    }

    public boolean hasPlayer(String userId) {
        if (userId == null) return false;
        return (playerX != null && userId.equals(playerX.getId()))
                || (playerO != null && userId.equals(playerO.getId()));
    }

    public Player getPlayerByUserId(String userId) {
        if (userId == null) return null;
        if (playerX != null && userId.equals(playerX.getId())) {
            return playerX;
        }
        if (playerO != null && userId.equals(playerO.getId())) {
            return playerO;
        }
        return null;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public void setRoomCode(String roomCode) {
        this.roomCode = roomCode;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public GameType getGameType() {
        return gameType;
    }

    public void setGameType(GameType gameType) {
        this.gameType = gameType;
    }

    public Player getPlayerX() {
        return playerX;
    }

    public void setPlayerX(Player playerX) {
        this.playerX = playerX;
        touch();
    }

    public Player getPlayerO() {
        return playerO;
    }

    public void setPlayerO(Player playerO) {
        this.playerO = playerO;
        touch();
    }

    public RoomStatus getStatus() {
        return status;
    }

    public void setStatus(RoomStatus status) {
        this.status = status;
        touch();
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public String getCurrentMatchId() {
        return currentMatchId;
    }

    public void setCurrentMatchId(String currentMatchId) {
        this.currentMatchId = currentMatchId;
    }

    public GameEngine getGameEngine() {
        return gameEngine;
    }

    public void setGameEngine(GameEngine gameEngine) {
        this.gameEngine = gameEngine;
    }
}
