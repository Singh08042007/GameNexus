/**
 * NEXUS REST API Client (Vanilla ES6+)
 * Communicates exclusively with the Java OOP Backend REST endpoints.
 * Never holds or exposes database or Supabase service-role credentials.
 */

const TOKEN_STORAGE_KEY = "nexus_auth_token";
const USER_STORAGE_KEY = "nexus_auth_user";

export class ApiClient {
  constructor(baseUrl = "") {
    this.baseUrl = baseUrl;
  }

  getToken() {
    return sessionStorage.getItem(TOKEN_STORAGE_KEY) || localStorage.getItem(TOKEN_STORAGE_KEY) || "";
  }

  setSession(token, user) {
    if (token) {
      sessionStorage.setItem(TOKEN_STORAGE_KEY, token);
      localStorage.setItem(TOKEN_STORAGE_KEY, token);
    }
    if (user) {
      const serialized = JSON.stringify(user);
      sessionStorage.setItem(USER_STORAGE_KEY, serialized);
      localStorage.setItem(USER_STORAGE_KEY, serialized);
    }
  }

  clearSession() {
    sessionStorage.removeItem(TOKEN_STORAGE_KEY);
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    sessionStorage.removeItem(USER_STORAGE_KEY);
    localStorage.removeItem(USER_STORAGE_KEY);
  }

  getCachedUser() {
    const raw = sessionStorage.getItem(USER_STORAGE_KEY) || localStorage.getItem(USER_STORAGE_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw);
    } catch {
      return null;
    }
  }

  async request(path, options = {}) {
    const headers = {
      "Content-Type": "application/json",
      ...(options.headers || {})
    };

    const token = this.getToken();
    if (token) {
      headers["Authorization"] = `Bearer ${token}`;
    }

    const response = await fetch(`${this.baseUrl}${path}`, {
      ...options,
      headers
    });

    let payload = null;
    try {
      payload = await response.json();
    } catch {
      throw new Error(`Unexpected response from server (HTTP ${response.status}).`);
    }

    if (!response.ok || !payload.success) {
      const error = new Error(payload?.message || `Request failed (${response.status})`);
      error.status = response.status;
      error.errorCode = payload?.errorCode || "API_ERROR";
      throw error;
    }

    return payload.data;
  }

  getHealth() {
    return this.request("/api/health", { method: "GET" });
  }

  getGameCatalog() {
    return this.request("/api/games", { method: "GET" });
  }

  register(username, email, password) {
    return this.request("/api/auth/register", {
      method: "POST",
      body: JSON.stringify({ username, email, password })
    });
  }

  login(identifier, password) {
    return this.request("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ identifier, password })
    });
  }

  logout() {
    return this.request("/api/auth/logout", { method: "POST" });
  }

  getMe() {
    return this.request("/api/auth/me", { method: "GET" });
  }

  listRooms() {
    return this.request("/api/rooms", { method: "GET" });
  }

  createRoom(roomName, gameType = "TIC_TAC_TOE") {
    return this.request("/api/rooms/create", {
      method: "POST",
      body: JSON.stringify({ roomName, gameType })
    });
  }

  joinRoom(roomCode) {
    return this.request("/api/rooms/join", {
      method: "POST",
      body: JSON.stringify({ roomCode })
    });
  }

  sendChatMessage(roomCode, message) {
    return this.request("/api/rooms/chat", {
      method: "POST",
      body: JSON.stringify({ roomCode, message })
    });
  }

  leaveRoom(roomCode) {
    return this.request("/api/rooms/leave", {
      method: "POST",
      body: JSON.stringify({ roomCode })
    });
  }

  getRoom(roomCode) {
    return this.request(`/api/rooms/${encodeURIComponent(roomCode)}`, { method: "GET" });
  }

  submitMove(roomCode, row, col) {
    return this.request("/api/game/move", {
      method: "POST",
      body: JSON.stringify({ roomCode, row, col })
    });
  }

  requestRematch(roomCode) {
    return this.request("/api/game/rematch", {
      method: "POST",
      body: JSON.stringify({ roomCode })
    });
  }

  getMatchHistory() {
    return this.request("/api/matches/history", { method: "GET" });
  }

  getMatchAnalysis(matchId) {
    return this.request(`/api/matches/analysis/${encodeURIComponent(matchId)}`, { method: "GET" });
  }

  getLeaderboard() {
    return this.request("/api/leaderboard", { method: "GET" });
  }
}
