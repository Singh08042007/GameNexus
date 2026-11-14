/**
 * NEXUS Real-Time WebSocket Client (Vanilla ES6+)
 * Connects to the Java RFC 6455 WebSocket Hub for instant room, turn, board, and chat updates,
 * with automatic reconnection and resilient state synchronization.
 */

export class RealtimeClient {
  constructor(onEventCallback, onStatusChangeCallback) {
    this.onEvent = onEventCallback;
    this.onStatusChange = onStatusChangeCallback;
    this.wsPort = 8081;
    this.socket = null;
    this.token = "";
    this.subscribedRoomCode = null;
    this.reconnectTimer = null;
    this.heartbeatTimer = null;
    this.connected = false;
  }

  configurePort(wsPort) {
    if (wsPort && Number.isInteger(wsPort)) {
      this.wsPort = wsPort;
    }
  }

  connect(token, roomCode = null) {
    this.token = token || "";
    if (roomCode !== null) {
      this.subscribedRoomCode = roomCode ? roomCode.toUpperCase() : null;
    }

    this.disconnect(false);
    if (!this.token) {
      return;
    }

    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const host = window.location.hostname || "localhost";
    const isLocalHost = host === "localhost" || host === "127.0.0.1";
    const params = new URLSearchParams({ token: this.token });
    if (this.subscribedRoomCode) {
      params.set("roomCode", this.subscribedRoomCode);
    }

    const authority = (!isLocalHost || window.location.protocol === "https:")
      ? window.location.host
      : `${host}:${this.wsPort}`;
    const wsUrl = `${protocol}//${authority}/ws?${params.toString()}`;
    try {
      this.socket = new WebSocket(wsUrl);
    } catch {
      this.scheduleReconnect();
      return;
    }

    this.socket.addEventListener("open", () => {
      this.connected = true;
      if (this.onStatusChange) {
        this.onStatusChange(true);
      }
      if (this.subscribedRoomCode) {
        this.send({ action: "SUBSCRIBE_ROOM", roomCode: this.subscribedRoomCode });
      }
      this.startHeartbeat();
    });

    this.socket.addEventListener("message", (event) => {
      try {
        const message = JSON.parse(event.data);
        if (this.onEvent) {
          this.onEvent(message);
        }
      } catch {
        // Ignore malformed frame
      }
    });

    this.socket.addEventListener("close", () => {
      this.connected = false;
      this.stopHeartbeat();
      if (this.onStatusChange) {
        this.onStatusChange(false);
      }
      if (this.token) {
        this.scheduleReconnect();
      }
    });

    this.socket.addEventListener("error", () => {
      this.connected = false;
    });
  }

  subscribeRoom(roomCode) {
    this.subscribedRoomCode = roomCode ? roomCode.toUpperCase() : null;
    if (this.connected && this.socket && this.socket.readyState === WebSocket.OPEN) {
      if (this.subscribedRoomCode) {
        this.send({ action: "SUBSCRIBE_ROOM", roomCode: this.subscribedRoomCode });
      } else {
        this.send({ action: "LEAVE_ROOM_CHANNEL" });
      }
    }
  }

  sendChat(roomCode, message) {
    if (this.connected && this.socket && this.socket.readyState === WebSocket.OPEN) {
      this.send({ action: "SEND_CHAT", roomCode, message });
      return true;
    }
    return false;
  }

  send(payload) {
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify(payload));
    }
  }

  startHeartbeat() {
    this.stopHeartbeat();
    this.heartbeatTimer = window.setInterval(() => {
      if (this.connected) {
        this.send({ action: "PING" });
      }
    }, 20000);
  }

  stopHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  scheduleReconnect() {
    if (this.reconnectTimer) return;
    this.reconnectTimer = window.setTimeout(() => {
      this.reconnectTimer = null;
      if (this.token) {
        this.connect(this.token, this.subscribedRoomCode);
      }
    }, 3000);
  }

  disconnect(clearToken = true) {
    this.stopHeartbeat();
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    if (clearToken) {
      this.token = "";
      this.subscribedRoomCode = null;
    }
    if (this.socket) {
      try {
        this.socket.close();
      } catch {
        // Ignore close errors
      }
      this.socket = null;
    }
    this.connected = false;
  }
}
