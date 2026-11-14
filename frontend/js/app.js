/**
 * NEXUS Frontend Application Controller (Vanilla ES6+)
 * Coordinates views, multi-game selection (Tic-Tac-Toe 3x3, Connect Four 6x7, Gomoku 9x9, Quad-Tac-Toe 4x4),
 * real-time room chatroom, live tactical game analysis, and authoritative board rendering.
 */

import { ApiClient } from "./api.js";
import { RealtimeClient } from "./ws.js";

class NexusApp {
  constructor() {
    this.api = new ApiClient("");
    this.realtime = new RealtimeClient(
      (event) => this.handleRealtimeEvent(event),
      (online) => this.updateConnectionBadge(online)
    );

    this.currentUser = null;
    this.currentRoom = null;
    this.currentView = "landing";
    this.selectedGameType = "TIC_TAC_TOE";
    this.moveInFlight = false;
    this.chatInFlight = false;
  }

  async init() {
    this.bindElements();
    this.bindEvents();

    // Check server health & configure WebSocket port
    try {
      const health = await this.api.getHealth();
      if (health && health.wsPort) {
        this.realtime.configurePort(health.wsPort);
      }
      this.updateConnectionBadge(true);
    } catch {
      this.updateConnectionBadge(false);
    }

    // Restore existing session if token is present
    const savedToken = this.api.getToken();
    if (savedToken) {
      try {
        const user = await this.api.getMe();
        this.onAuthenticated(savedToken, user, false);
        return;
      } catch {
        this.api.clearSession();
      }
    }

    this.renderNavigationState();
    this.switchView("landing");
  }

  bindElements() {
    this.views = {
      landing: document.getElementById("view-landing"),
      auth: document.getElementById("view-auth"),
      dashboard: document.getElementById("view-dashboard"),
      arena: document.getElementById("view-arena"),
      history: document.getElementById("view-history")
    };

    // Nav elements
    this.navTabsContainer = document.getElementById("authenticated-nav");
    this.tabDashboard = document.getElementById("tab-dashboard");
    this.tabArena = document.getElementById("tab-arena");
    this.tabHistory = document.getElementById("tab-history");
    this.wsStatusDot = document.getElementById("ws-status-dot");
    this.wsStatusLabel = document.getElementById("ws-status-label");
    this.navUserBadge = document.getElementById("nav-user-badge");
    this.navUsername = document.getElementById("nav-username");
    this.navUserRecord = document.getElementById("nav-user-record");
    this.btnLogout = document.getElementById("btn-logout");
    this.btnNavAuth = document.getElementById("btn-nav-auth");

    // Auth elements
    this.tabAuthLogin = document.getElementById("tab-auth-login");
    this.tabAuthRegister = document.getElementById("tab-auth-register");
    this.formLogin = document.getElementById("form-login");
    this.formRegister = document.getElementById("form-register");
    this.authErrorBox = document.getElementById("auth-error-box");

    // Dashboard & Game Catalog elements
    this.gameSelectorGrid = document.getElementById("game-selector-grid");
    this.selectedGameIndicator = document.getElementById("selected-game-indicator");
    this.inputRoomGame = document.getElementById("input-room-game");
    this.statWins = document.getElementById("stat-wins");
    this.statLosses = document.getElementById("stat-losses");
    this.statDraws = document.getElementById("stat-draws");
    this.statWinrate = document.getElementById("stat-winrate");
    this.dashboardUserId = document.getElementById("dashboard-user-id");
    this.formCreateRoom = document.getElementById("form-create-room");
    this.inputRoomName = document.getElementById("input-room-name");
    this.formJoinCode = document.getElementById("form-join-code");
    this.inputJoinCode = document.getElementById("input-join-code");
    this.openRoomsList = document.getElementById("open-rooms-list");
    this.roomsCountBadge = document.getElementById("rooms-count-badge");

    // Arena elements
    this.arenaGameBadge = document.getElementById("arena-game-badge");
    this.arenaRoomStatusLabel = document.getElementById("arena-room-status-label");
    this.arenaRoomTitle = document.getElementById("arena-room-title");
    this.arenaRoomCode = document.getElementById("arena-room-code");
    this.btnCopyRoomCode = document.getElementById("btn-copy-room-code");
    this.slotPlayerX = document.getElementById("slot-player-x");
    this.slotPlayerO = document.getElementById("slot-player-o");
    this.slotXName = document.getElementById("slot-x-name");
    this.slotXRecord = document.getElementById("slot-x-record");
    this.slotXBadge = document.getElementById("slot-x-badge");
    this.slotOName = document.getElementById("slot-o-name");
    this.slotORecord = document.getElementById("slot-o-record");
    this.slotOBadge = document.getElementById("slot-o-badge");
    this.turnBannerText = document.getElementById("turn-banner-text");
    this.turnCounterPill = document.getElementById("turn-counter-pill");
    this.connectFourDropRow = document.getElementById("connect-four-drop-row");
    this.tttBoard = document.getElementById("ttt-board");
    this.moveLogList = document.getElementById("move-log-list");
    this.moveLogCount = document.getElementById("move-log-count");
    this.matchResultCallout = document.getElementById("match-result-callout");
    this.matchResultHeadline = document.getElementById("match-result-headline");
    this.matchResultSubtitle = document.getElementById("match-result-subtitle");

    // Live Analysis elements
    this.analysisOpeningName = document.getElementById("analysis-opening-name");
    this.analysisShareX = document.getElementById("analysis-share-x");
    this.analysisShareO = document.getElementById("analysis-share-o");
    this.analysisBarX = document.getElementById("analysis-bar-x");
    this.analysisBarO = document.getElementById("analysis-bar-o");
    this.analysisAccX = document.getElementById("analysis-acc-x");
    this.analysisAccO = document.getElementById("analysis-acc-o");
    this.analysisThreats = document.getElementById("analysis-threats");
    this.analysisBlocks = document.getElementById("analysis-blocks");
    this.analysisTurningPoint = document.getElementById("analysis-turning-point");
    this.analysisSummaryText = document.getElementById("analysis-summary-text");

    // Chatroom elements
    this.chatMessagesStream = document.getElementById("chat-messages-stream");
    this.chatPresetBar = document.getElementById("chat-preset-bar");
    this.formRoomChat = document.getElementById("form-room-chat");
    this.inputChatMessage = document.getElementById("input-chat-message");

    // History & Leaderboard elements
    this.matchHistoryList = document.getElementById("match-history-list");
    this.leaderboardList = document.getElementById("leaderboard-list");
    this.toastRegion = document.getElementById("toast-region");
  }

  bindEvents() {
    // Brand & Hero navigation
    document.getElementById("nav-brand-link").addEventListener("click", (e) => {
      e.preventDefault();
      if (this.currentUser) {
        this.switchView("dashboard");
      } else {
        this.switchView("landing");
      }
    });

    document.getElementById("btn-hero-start").addEventListener("click", () => {
      if (this.currentUser) {
        this.switchView("dashboard");
      } else {
        this.showAuthMode("register");
        this.switchView("auth");
      }
    });

    document.getElementById("btn-hero-login").addEventListener("click", () => {
      if (this.currentUser) {
        this.switchView("dashboard");
      } else {
        this.showAuthMode("login");
        this.switchView("auth");
      }
    });

    this.btnNavAuth.addEventListener("click", () => {
      this.showAuthMode("login");
      this.switchView("auth");
    });

    // Primary nav tabs
    this.tabDashboard.addEventListener("click", () => {
      this.switchView("dashboard");
      this.refreshDashboardData();
    });

    this.tabArena.addEventListener("click", () => {
      if (this.currentRoom) {
        this.switchView("arena");
      }
    });

    this.tabHistory.addEventListener("click", () => {
      this.switchView("history");
      this.refreshHistoryAndLeaderboard();
    });

    // Auth mode toggle
    this.tabAuthLogin.addEventListener("click", () => this.showAuthMode("login"));
    this.tabAuthRegister.addEventListener("click", () => this.showAuthMode("register"));

    // Login & Register submissions
    this.formLogin.addEventListener("submit", (e) => this.handleLoginSubmit(e));
    this.formRegister.addEventListener("submit", (e) => this.handleRegisterSubmit(e));
    this.btnLogout.addEventListener("click", () => this.handleLogout());

    // Game mode card selector
    this.gameSelectorGrid.querySelectorAll(".game-choice-card").forEach((card) => {
      card.addEventListener("click", () => {
        const gameType = card.getAttribute("data-game-type");
        this.selectGameMode(gameType);
      });
    });

    this.inputRoomGame.addEventListener("change", () => {
      this.selectGameMode(this.inputRoomGame.value);
    });

    // Room actions
    this.formCreateRoom.addEventListener("submit", (e) => this.handleCreateRoom(e));
    this.formJoinCode.addEventListener("submit", (e) => this.handleJoinByCode(e));
    document.getElementById("btn-refresh-lobby").addEventListener("click", () => this.refreshDashboardData());
    document.getElementById("btn-refresh-history").addEventListener("click", () => this.refreshHistoryAndLeaderboard());

    // Arena controls
    this.btnCopyRoomCode.addEventListener("click", () => this.copyRoomCode());
    document.getElementById("btn-back-lobby").addEventListener("click", () => {
      this.switchView("dashboard");
      this.refreshDashboardData();
    });
    document.getElementById("btn-leave-room").addEventListener("click", () => this.handleLeaveRoom());
    document.getElementById("btn-rematch").addEventListener("click", () => this.handleRematch());
    document.getElementById("btn-view-history").addEventListener("click", () => {
      this.switchView("history");
      this.refreshHistoryAndLeaderboard();
    });

    // Chatroom form & quick preset chips
    this.formRoomChat.addEventListener("submit", (e) => this.handleSendChat(e));
    this.chatPresetBar.querySelectorAll(".chat-preset-chip").forEach((chip) => {
      chip.addEventListener("click", () => {
        const presetText = chip.getAttribute("data-chat-preset");
        if (presetText) {
          this.sendChatText(presetText);
        }
      });
    });
  }

  selectGameMode(gameType) {
    this.selectedGameType = gameType || "TIC_TAC_TOE";
    this.inputRoomGame.value = this.selectedGameType;

    const labels = {
      TIC_TAC_TOE: "Tic-Tac-Toe (3x3 Classic Grid)",
      CONNECT_FOUR: "Connect Four (6x7 Gravity Drop)",
      GOMOKU: "Gomoku (9x9 Strategy Board)",
      QUAD_TAC_TOE: "Quad-Tac-Toe (4x4 Extended Arena)"
    };
    this.selectedGameIndicator.textContent = `Selected: ${labels[this.selectedGameType] || this.selectedGameType}`;

    this.gameSelectorGrid.querySelectorAll(".game-choice-card").forEach((card) => {
      const isMatch = card.getAttribute("data-game-type") === this.selectedGameType;
      card.classList.toggle("is-selected", isMatch);
    });
  }

  showAuthMode(mode) {
    this.hideAuthError();
    const isLogin = mode === "login";
    this.tabAuthLogin.classList.toggle("is-active", isLogin);
    this.tabAuthLogin.setAttribute("aria-selected", String(isLogin));
    this.tabAuthRegister.classList.toggle("is-active", !isLogin);
    this.tabAuthRegister.setAttribute("aria-selected", String(!isLogin));
    this.formLogin.classList.toggle("hidden", !isLogin);
    this.formRegister.classList.toggle("hidden", isLogin);
  }

  showAuthError(message) {
    this.authErrorBox.textContent = message;
    this.authErrorBox.classList.remove("hidden");
  }

  hideAuthError() {
    this.authErrorBox.textContent = "";
    this.authErrorBox.classList.add("hidden");
  }

  async handleLoginSubmit(event) {
    event.preventDefault();
    this.hideAuthError();
    const identifier = document.getElementById("login-identifier").value.trim();
    const password = document.getElementById("login-password").value;

    if (!identifier || !password) {
      this.showAuthError("Please enter both your email/username and password.");
      return;
    }

    const submitBtn = document.getElementById("btn-submit-login");
    submitBtn.disabled = true;
    try {
      const data = await this.api.login(identifier, password);
      this.onAuthenticated(data.token, data.user, true);
      this.formLogin.reset();
    } catch (err) {
      this.showAuthError(err.message);
    } finally {
      submitBtn.disabled = false;
    }
  }

  async handleRegisterSubmit(event) {
    event.preventDefault();
    this.hideAuthError();
    const username = document.getElementById("reg-username").value.trim();
    const email = document.getElementById("reg-email").value.trim();
    const password = document.getElementById("reg-password").value;

    if (!username || !email || !password) {
      this.showAuthError("All registration fields are required.");
      return;
    }

    const submitBtn = document.getElementById("btn-submit-register");
    submitBtn.disabled = true;
    try {
      const data = await this.api.register(username, email, password);
      this.onAuthenticated(data.token, data.user, true);
      this.formRegister.reset();
    } catch (err) {
      this.showAuthError(err.message);
    } finally {
      submitBtn.disabled = false;
    }
  }

  onAuthenticated(token, user, showWelcomeToast = false) {
    this.currentUser = user;
    this.api.setSession(token, user);
    this.renderNavigationState();
    this.renderUserStats(user);
    this.realtime.connect(token, this.currentRoom ? this.currentRoom.roomCode : null);

    if (showWelcomeToast) {
      this.showToast(`Welcome, ${user.username}! Session active.`, "success");
    }

    this.switchView("dashboard");
    this.refreshDashboardData();
  }

  async handleLogout() {
    try {
      await this.api.logout();
    } catch {
      // Proceed with local cleanup
    }
    this.realtime.disconnect(true);
    this.api.clearSession();
    this.currentUser = null;
    this.currentRoom = null;
    this.renderNavigationState();
    this.switchView("landing");
    this.showToast("Signed out of NEXUS.", "info");
  }

  renderNavigationState() {
    const loggedIn = Boolean(this.currentUser);
    this.navTabsContainer.classList.toggle("hidden", !loggedIn);
    this.navUserBadge.classList.toggle("hidden", !loggedIn);
    this.btnLogout.classList.toggle("hidden", !loggedIn);
    this.btnNavAuth.classList.toggle("hidden", loggedIn);
    this.tabArena.classList.toggle("hidden", !this.currentRoom);

    if (loggedIn && this.currentUser) {
      this.navUsername.textContent = this.currentUser.username;
      this.navUserRecord.textContent = `${this.currentUser.wins}W-${this.currentUser.losses}L-${this.currentUser.draws}D`;
    }
  }

  renderUserStats(user) {
    if (!user) return;
    this.currentUser = user;
    this.api.setSession(this.api.getToken(), user);
    this.renderNavigationState();

    this.statWins.textContent = String(user.wins ?? 0);
    this.statLosses.textContent = String(user.losses ?? 0);
    this.statDraws.textContent = String(user.draws ?? 0);
    const total = (user.wins || 0) + (user.losses || 0) + (user.draws || 0);
    const winrate = total > 0 ? Math.round((user.wins / total) * 100) : 0;
    this.statWinrate.textContent = `${winrate}%`;
    this.dashboardUserId.textContent = `@${user.username}`;
  }

  switchView(viewName) {
    if (!this.currentUser && (viewName === "dashboard" || viewName === "arena" || viewName === "history")) {
      this.showAuthMode("login");
      viewName = "auth";
    }

    this.currentView = viewName;
    Object.entries(this.views).forEach(([key, section]) => {
      const isHidden = key !== viewName;
      section.classList.toggle("hidden", isHidden);
      section.hidden = isHidden;
    });

    this.tabDashboard.classList.toggle("is-active", viewName === "dashboard");
    this.tabArena.classList.toggle("is-active", viewName === "arena");
    this.tabHistory.classList.toggle("is-active", viewName === "history");
  }

  async refreshDashboardData() {
    if (!this.currentUser) return;
    try {
      const [me, rooms] = await Promise.all([
        this.api.getMe(),
        this.api.listRooms()
      ]);
      this.renderUserStats(me);
      this.renderOpenRoomsList(rooms || []);
    } catch (err) {
      this.showToast(err.message, "error");
    }
  }

  renderOpenRoomsList(rooms) {
    this.roomsCountBadge.textContent = `${rooms.length} ROOM${rooms.length === 1 ? "" : "S"}`;

    if (!rooms.length) {
      this.openRoomsList.innerHTML = `
        <div class="empty-state">
          No active rooms yet. Pick a game mode above and click <strong>Create Room</strong> to host a match.
        </div>
      `;
      return;
    }

    this.openRoomsList.innerHTML = "";
    rooms.forEach((room) => {
      const row = document.createElement("div");
      row.className = "room-row";

      const isParticipant =
        (room.playerX && room.playerX.id === this.currentUser?.id) ||
        (room.playerO && room.playerO.id === this.currentUser?.id);

      let statusClass = "is-waiting";
      if (room.status === "IN_PROGRESS") statusClass = "is-active";
      if (room.status === "COMPLETED" || room.status === "ABANDONED") statusClass = "is-done";

      const hostName = room.playerX ? room.playerX.username : "Host";
      const guestName = room.playerO ? room.playerO.username : "Open Slot";
      const gameLabel = room.gameDisplayName || room.gameType || "Tic-Tac-Toe";

      const infoDiv = document.createElement("div");
      infoDiv.innerHTML = `
        <div style="display: flex; align-items: center; gap: 0.45rem; flex-wrap: wrap; margin-bottom: 0.25rem;">
          <span class="room-code-badge">${this.escapeHtml(room.roomCode)}</span>
          <span class="room-game-pill">${this.escapeHtml(gameLabel)}</span>
          <strong style="font-family: var(--font-display); font-size: var(--text-sm);">
            ${this.escapeHtml(room.roomName)}
          </strong>
          <span class="room-status-badge ${statusClass}">
            ${this.escapeHtml(room.status)} (${room.playerCount}/${room.maxPlayers})
          </span>
        </div>
        <div style="font-size: var(--text-xs); color: var(--text-secondary);">
          X: ${this.escapeHtml(hostName)} &middot; O: ${this.escapeHtml(guestName)}
        </div>
      `;

      const actionBtn = document.createElement("button");
      actionBtn.type = "button";
      actionBtn.className = isParticipant ? "btn btn-primary btn-sm" : "btn btn-secondary btn-sm";

      if (isParticipant) {
        actionBtn.textContent = "Re-enter Room";
        actionBtn.addEventListener("click", () => this.joinRoomByCode(room.roomCode));
      } else if (room.status === "WAITING" && room.playerCount < room.maxPlayers) {
        actionBtn.textContent = "Join Match";
        actionBtn.addEventListener("click", () => this.joinRoomByCode(room.roomCode));
      } else {
        actionBtn.textContent = "Full";
        actionBtn.disabled = true;
      }

      row.appendChild(infoDiv);
      row.appendChild(actionBtn);
      this.openRoomsList.appendChild(row);
    });
  }

  async handleCreateRoom(event) {
    event.preventDefault();
    const roomName = this.inputRoomName.value.trim();
    const gameType = this.inputRoomGame.value || this.selectedGameType || "TIC_TAC_TOE";
    const btn = document.getElementById("btn-create-room");
    btn.disabled = true;
    try {
      const room = await this.api.createRoom(roomName, gameType);
      this.inputRoomName.value = "";
      this.enterArenaWithRoom(room);
      this.showToast(`Created ${room.gameDisplayName || gameType} room ${room.roomCode}.`, "success");
    } catch (err) {
      this.showToast(err.message, "error");
    } finally {
      btn.disabled = false;
    }
  }

  async handleJoinByCode(event) {
    event.preventDefault();
    const code = this.inputJoinCode.value.trim().toUpperCase();
    if (!code) {
      this.showToast("Please enter a valid Room Code.", "error");
      return;
    }
    await this.joinRoomByCode(code);
  }

  async joinRoomByCode(code) {
    try {
      const room = await this.api.joinRoom(code);
      this.inputJoinCode.value = "";
      this.enterArenaWithRoom(room);
      this.showToast(`Joined Room ${room.roomCode}.`, "success");
    } catch (err) {
      this.showToast(err.message, "error");
    }
  }

  enterArenaWithRoom(room) {
    this.currentRoom = room;
    this.renderNavigationState();
    this.realtime.subscribeRoom(room.roomCode);
    this.renderArena(room);
    this.switchView("arena");
  }

  renderArena(room) {
    if (!room) return;
    this.currentRoom = room;

    const gameType = room.gameType || "TIC_TAC_TOE";
    const rows = room.rows || room.gameState?.rows || 3;
    const cols = room.cols || room.gameState?.cols || 3;
    const winLength = room.winLength || room.gameState?.winLength || 3;

    this.arenaGameBadge.textContent = `${room.gameDisplayName || gameType} · ${rows}×${cols} (Align ${winLength})`;
    this.arenaRoomTitle.textContent = room.roomName;
    this.arenaRoomCode.textContent = room.roomCode;
    this.arenaRoomStatusLabel.textContent = `ROOM STATUS · ${room.status}`;

    const gs = room.gameState || {
      board: "-".repeat(rows * cols),
      rows,
      cols,
      winLength,
      currentTurn: "X",
      moveCount: 0,
      outcome: "IN_PROGRESS",
      gameOver: false,
      winningLine: null,
      moves: [],
      analysis: null
    };

    // Player X & Player O Cards
    const px = room.playerX;
    const po = room.playerO;
    const isInProgress = room.status === "IN_PROGRESS" && !gs.gameOver;

    this.slotPlayerX.classList.toggle("is-active-turn-x", isInProgress && gs.currentTurn === "X");
    this.slotPlayerO.classList.toggle("is-active-turn-o", isInProgress && gs.currentTurn === "O");

    if (px) {
      this.slotXName.textContent = px.username + (px.id === this.currentUser?.id ? " (You)" : "");
      this.slotXRecord.textContent = `${px.wins}W · ${px.losses}L · ${px.draws}D`;
      this.slotXBadge.textContent = px.connected ? "ONLINE · PLAYER X" : "DISCONNECTED · X";
    } else {
      this.slotXName.textContent = "Waiting...";
      this.slotXRecord.textContent = "Host Slot";
      this.slotXBadge.textContent = "PLAYER X";
    }

    if (po) {
      this.slotOName.textContent = po.username + (po.id === this.currentUser?.id ? " (You)" : "");
      this.slotORecord.textContent = `${po.wins}W · ${po.losses}L · ${po.draws}D`;
      this.slotOBadge.textContent = po.connected ? "ONLINE · PLAYER O" : "DISCONNECTED · O";
    } else {
      this.slotOName.textContent = "Waiting for Challenger...";
      this.slotORecord.textContent = `Share code ${room.roomCode} to invite`;
      this.slotOBadge.textContent = "OPEN SLOT · O";
    }

    // Turn status banner
    this.turnCounterPill.textContent = `MOVE ${gs.moveCount || 0}`;
    const mySymbol = this.getMySymbolInRoom(room);

    if (room.status === "WAITING") {
      this.turnBannerText.textContent = "Waiting for second player to join room...";
    } else if (gs.gameOver || room.status === "COMPLETED") {
      this.turnBannerText.textContent = this.describeOutcomeHeadline(room, gs);
    } else if (mySymbol && gs.currentTurn === mySymbol) {
      this.turnBannerText.textContent =
        gameType === "CONNECT_FOUR"
          ? `Your Turn (${mySymbol}) — Select a column to drop your disc.`
          : `Your Turn (${mySymbol}) — Select an open coordinate on the ${rows}×${cols} board.`;
    } else {
      const activePlayerName = gs.currentTurn === "X" ? (px?.username || "Player X") : (po?.username || "Player O");
      this.turnBannerText.textContent = `Waiting for ${activePlayerName} (${gs.currentTurn}) to move...`;
    }

    // Connect Four column drop buttons
    const canInteract =
      room.status === "IN_PROGRESS" &&
      !gs.gameOver &&
      Boolean(mySymbol) &&
      gs.currentTurn === mySymbol;

    if (gameType === "CONNECT_FOUR") {
      this.connectFourDropRow.classList.remove("hidden");
      this.connectFourDropRow.innerHTML = "";
      const boardStr = gs.board || "-".repeat(rows * cols);
      for (let c = 0; c < cols; c++) {
        const dropBtn = document.createElement("button");
        dropBtn.type = "button";
        dropBtn.className = "drop-col-btn";
        dropBtn.textContent = `Drop ${c + 1}`;
        const topCellChar = boardStr.charAt(c);
        const colFull = topCellChar === "X" || topCellChar === "O";
        dropBtn.disabled = !canInteract || colFull;
        dropBtn.addEventListener("click", () => this.handleCellClick(0, c));
        this.connectFourDropRow.appendChild(dropBtn);
      }
    } else {
      this.connectFourDropRow.classList.add("hidden");
      this.connectFourDropRow.innerHTML = "";
    }

    // Dynamic Grid Class (3x3, 4x4, 6x7, 9x9)
    this.tttBoard.className = "ttt-board";
    if (rows === 4 && cols === 4) this.tttBoard.classList.add("grid-4x4");
    if (rows === 6 && cols === 7) this.tttBoard.classList.add("grid-6x7");
    if (rows === 9 && cols === 9) this.tttBoard.classList.add("grid-9x9");

    // Render Board Cells
    const winningSet = new Set(Array.isArray(gs.winningLine) ? gs.winningLine : []);
    const boardStr = gs.board || "-".repeat(rows * cols);
    const totalCells = rows * cols;
    const movesList = Array.isArray(gs.moves) ? gs.moves : [];
    const lastMove = movesList.length > 0 ? movesList[movesList.length - 1] : null;

    this.tttBoard.innerHTML = "";
    for (let idx = 0; idx < totalCells; idx++) {
      const row = Math.floor(idx / cols);
      const col = idx % cols;
      const cellChar = boardStr.charAt(idx) || "-";
      const isOccupied = cellChar === "X" || cellChar === "O";

      const cellBtn = document.createElement("button");
      cellBtn.type = "button";
      cellBtn.className = "board-cell";
      cellBtn.setAttribute("role", "gridcell");
      cellBtn.setAttribute(
        "aria-label",
        `Row ${row + 1}, Column ${col + 1}, ${isOccupied ? cellChar : "Empty"}`
      );

      if (cellChar === "X") {
        cellBtn.classList.add("cell-x");
        cellBtn.textContent = "X";
      } else if (cellChar === "O") {
        cellBtn.classList.add("cell-o");
        cellBtn.textContent = "O";
      }

      if (lastMove && lastMove.row === row && lastMove.col === col) {
        cellBtn.classList.add("is-last-move");
      }

      if (winningSet.has(idx)) {
        cellBtn.classList.add("is-winning-cell");
      }

      if (gameType === "CONNECT_FOUR") {
        const colHasSpace = (boardStr.charAt(col) || "-") === "-";
        cellBtn.disabled = !canInteract || !colHasSpace;
      } else {
        cellBtn.disabled = isOccupied || !canInteract;
      }

      cellBtn.addEventListener("click", () => this.handleCellClick(row, col));
      this.tttBoard.appendChild(cellBtn);
    }

    // Post-match outcome banner
    const showResultBanner = gs.gameOver || room.status === "COMPLETED";
    this.matchResultCallout.classList.toggle("hidden", !showResultBanner);
    if (showResultBanner) {
      this.matchResultHeadline.textContent = this.describeOutcomeHeadline(room, gs);
      this.matchResultSubtitle.textContent =
        gs.analysis?.summary || this.describeOutcomeSubtitle(room, gs);
    }

    // Render Live Analysis, Chatroom, and Annotated Move Log
    this.renderLiveAnalysis(gs.analysis);
    this.renderRoomChat(room.chatMessages || []);
    this.renderAnnotatedMoveLog(gs.moves || [], gs.analysis?.moveEvaluations || []);
  }

  renderLiveAnalysis(analysis) {
    if (!analysis) {
      this.analysisOpeningName.textContent = "Awaiting Opening";
      this.analysisShareX.textContent = "X Control: 50%";
      this.analysisShareO.textContent = "O Control: 50%";
      this.analysisBarX.style.width = "50%";
      this.analysisBarO.style.width = "50%";
      this.analysisAccX.textContent = "100%";
      this.analysisAccO.textContent = "100%";
      this.analysisThreats.textContent = "X: 0 · O: 0";
      this.analysisBlocks.textContent = "X: 0 · O: 0";
      this.analysisTurningPoint.textContent = "No decisive tactical swing yet.";
      this.analysisSummaryText.textContent =
        "Make a move on the board to generate live move accuracy, threat radar, and positional telemetry.";
      return;
    }

    const shareX = analysis.controlShareX ?? 50;
    const shareO = analysis.controlShareO ?? 50;
    this.analysisOpeningName.textContent = analysis.openingName || "Standard Opening";
    this.analysisShareX.textContent = `X Control: ${shareX}%`;
    this.analysisShareO.textContent = `O Control: ${shareO}%`;
    this.analysisBarX.style.width = `${shareX}%`;
    this.analysisBarO.style.width = `${shareO}%`;

    this.analysisAccX.textContent = `${analysis.accuracyX ?? 100}%`;
    this.analysisAccO.textContent = `${analysis.accuracyO ?? 100}%`;
    this.analysisThreats.textContent = `X: ${analysis.threatsCreatedX ?? 0} · O: ${analysis.threatsCreatedO ?? 0}`;
    this.analysisBlocks.textContent = `X: ${analysis.blocksMadeX ?? 0} · O: ${analysis.blocksMadeO ?? 0}`;
    this.analysisTurningPoint.textContent = analysis.turningPoint || "Balanced tactical exchange.";
    this.analysisSummaryText.textContent = analysis.summary || "";
  }

  renderRoomChat(chatMessages) {
    if (!chatMessages.length) {
      this.chatMessagesStream.innerHTML = `
        <div class="empty-state" style="padding: var(--space-4);">
          No messages yet. Say hello to your opponent!
        </div>
      `;
      return;
    }

    this.chatMessagesStream.innerHTML = "";
    chatMessages.forEach((msg) => {
      const bubble = document.createElement("div");
      const isSystem = msg.messageType === "SYSTEM" || msg.senderSymbol === "SYS";
      const isSelf = this.currentUser && msg.senderId === this.currentUser.id;

      bubble.className = "chat-bubble";
      if (isSystem) bubble.classList.add("is-system");
      else if (isSelf) bubble.classList.add("is-self");

      const timeStr = msg.createdAt
        ? new Date(msg.createdAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
        : "";

      if (isSystem) {
        bubble.innerHTML = `
          <div>${this.escapeHtml(msg.message)}</div>
        `;
      } else {
        bubble.innerHTML = `
          <div class="chat-bubble-header">
            <div>
              <span class="chat-sender">${this.escapeHtml(msg.senderUsername || "Player")}</span>
              <span class="chat-sender-sym">${this.escapeHtml(msg.senderSymbol || "")}</span>
            </div>
            <span class="mono" style="font-size: 0.68rem; color: var(--text-muted);">${this.escapeHtml(timeStr)}</span>
          </div>
          <div style="color: var(--text-primary); word-break: break-word;">${this.escapeHtml(msg.message)}</div>
        `;
      }
      this.chatMessagesStream.appendChild(bubble);
    });

    this.chatMessagesStream.scrollTop = this.chatMessagesStream.scrollHeight;
  }

  renderAnnotatedMoveLog(moves, evaluations) {
    this.moveLogCount.textContent = `${moves.length} MOVE${moves.length === 1 ? "" : "S"}`;
    if (!moves.length) {
      this.moveLogList.innerHTML = `
        <div class="empty-state" style="padding: var(--space-4);">
          No moves played yet.
        </div>
      `;
      return;
    }

    const evalByMoveNumber = new Map();
    if (Array.isArray(evaluations)) {
      evaluations.forEach((ev) => {
        evalByMoveNumber.set(ev.moveNumber, ev);
      });
    }

    this.moveLogList.innerHTML = "";
    [...moves].reverse().forEach((m) => {
      const ev = evalByMoveNumber.get(m.moveNumber);
      const item = document.createElement("div");
      item.className = "move-log-item";

      let badgeClass = "";
      if (ev) {
        if (ev.classification === "WINNING_STRIKE" || ev.classification === "DOUBLE_THREAT_FORK" || ev.classification === "CRITICAL_BLOCK") {
          badgeClass = "is-brilliant";
        } else if (ev.classification === "TACTICAL_THREAT" || ev.classification === "CENTER_CONTROL") {
          badgeClass = "is-threat";
        } else if (ev.classification === "MISSED_DEFENSE") {
          badgeClass = "is-mistake";
        }
      }

      const coordText = ev?.coordLabel || `R${m.row + 1}·C${m.col + 1}`;
      const badgeText = ev?.badgeLabel || "Move";
      const commentary = ev?.commentary || "";

      item.innerHTML = `
        <div class="move-log-top">
          <span>
            <strong class="mono">#${m.moveNumber}</strong>
            <strong>${this.escapeHtml(m.playerUsername || m.symbol)} (${this.escapeHtml(m.symbol)})</strong>
            <span class="mono" style="color: var(--text-muted); margin-left: 4px;">${this.escapeHtml(coordText)}</span>
          </span>
          <span class="eval-badge ${badgeClass}">${this.escapeHtml(badgeText)}</span>
        </div>
        ${commentary ? `<div style="color: var(--text-secondary); font-size: 0.72rem;">${this.escapeHtml(commentary)}</div>` : ""}
      `;
      this.moveLogList.appendChild(item);
    });
  }

  async handleSendChat(event) {
    event.preventDefault();
    const text = this.inputChatMessage.value.trim();
    if (!text) return;
    this.inputChatMessage.value = "";
    await this.sendChatText(text);
  }

  async sendChatText(text) {
    if (!this.currentRoom || this.chatInFlight) return;
    this.chatInFlight = true;
    try {
      const payload = await this.api.sendChatMessage(this.currentRoom.roomCode, text);
      if (payload && payload.room) {
        this.renderArena(payload.room);
      }
    } catch (err) {
      this.showToast(err.message, "error");
    } finally {
      this.chatInFlight = false;
    }
  }

  getMySymbolInRoom(room) {
    if (!this.currentUser || !room) return null;
    if (room.playerX && room.playerX.id === this.currentUser.id) return "X";
    if (room.playerO && room.playerO.id === this.currentUser.id) return "O";
    return null;
  }

  describeOutcomeHeadline(room, gs) {
    switch (gs.outcome) {
      case "X_WON":
        return `${room.playerX?.username || "Player X"} (X) Wins!`;
      case "O_WON":
        return `${room.playerO?.username || "Player O"} (O) Wins!`;
      case "DRAW":
        return "Stalemate — Match Drawn!";
      case "FORFEIT":
        return `${gs.winnerName || "Remaining Player"} Wins by Forfeit!`;
      default:
        return "Match Complete";
    }
  }

  describeOutcomeSubtitle(room, gs) {
    const mySym = this.getMySymbolInRoom(room);
    if (gs.outcome === "DRAW") {
      return "Both players locked the grid. Neither side yielded a line.";
    }
    if (gs.outcome === "FORFEIT") {
      return "Opponent left the active match.";
    }
    if ((gs.outcome === "X_WON" && mySym === "X") || (gs.outcome === "O_WON" && mySym === "O")) {
      return "Victory recorded to your career profile.";
    }
    return "Defeat recorded. Request an instant rematch to even the score!";
  }

  async handleCellClick(row, col) {
    if (!this.currentRoom || this.moveInFlight) return;
    this.moveInFlight = true;
    try {
      const updatedRoom = await this.api.submitMove(this.currentRoom.roomCode, row, col);
      this.renderArena(updatedRoom);
      if (updatedRoom.gameState?.gameOver) {
        const me = await this.api.getMe();
        this.renderUserStats(me);
      }
    } catch (err) {
      this.showToast(err.message, "error");
    } finally {
      this.moveInFlight = false;
    }
  }

  async handleRematch() {
    if (!this.currentRoom) return;
    try {
      const updatedRoom = await this.api.requestRematch(this.currentRoom.roomCode);
      this.renderArena(updatedRoom);
      this.showToast("New round started!", "success");
    } catch (err) {
      this.showToast(err.message, "error");
    }
  }

  async handleLeaveRoom() {
    if (!this.currentRoom) return;
    const code = this.currentRoom.roomCode;
    try {
      await this.api.leaveRoom(code);
    } catch {
      // Proceed to lobby even if room was already closed
    }
    this.realtime.subscribeRoom(null);
    this.currentRoom = null;
    this.renderNavigationState();
    this.switchView("dashboard");
    this.refreshDashboardData();
    this.showToast(`Left room ${code}.`, "info");
  }

  async copyRoomCode() {
    if (!this.currentRoom?.roomCode) return;
    try {
      await navigator.clipboard.writeText(this.currentRoom.roomCode);
      this.showToast(`Room code ${this.currentRoom.roomCode} copied to clipboard.`, "success");
    } catch {
      this.showToast(`Room Code: ${this.currentRoom.roomCode}`, "info");
    }
  }

  async refreshHistoryAndLeaderboard() {
    if (!this.currentUser) return;
    try {
      const [history, leaderboard, me] = await Promise.all([
        this.api.getMatchHistory(),
        this.api.getLeaderboard(),
        this.api.getMe()
      ]);
      this.renderUserStats(me);
      this.renderMatchHistory(history || []);
      this.renderLeaderboard(leaderboard || []);
    } catch (err) {
      this.showToast(err.message, "error");
    }
  }

  renderMatchHistory(matches) {
    if (!matches.length) {
      this.matchHistoryList.innerHTML = `<div class="empty-state">No completed matches yet. Finish a game in the Arena to view full tactical analysis reports!</div>`;
      return;
    }

    this.matchHistoryList.innerHTML = "";
    matches.forEach((m) => {
      const row = document.createElement("div");
      row.className = "history-row";

      let badgeClass = "is-done";
      let outcomeLabel = m.outcome;
      if (m.outcome === "DRAW") {
        outcomeLabel = "DRAW";
      } else if (m.winnerId === this.currentUser?.id) {
        badgeClass = "is-waiting";
        outcomeLabel = "VICTORY";
      } else if (m.outcome !== "IN_PROGRESS") {
        badgeClass = "is-active";
        outcomeLabel = "DEFEAT";
      }

      const dateStr = m.startedAt ? new Date(m.startedAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) : "";
      const gameLabel = m.gameDisplayName || m.gameType || "Tic-Tac-Toe";
      const analysis = m.analysis || {};

      row.innerHTML = `
        <div class="history-row-top">
          <div>
            <div style="display: flex; align-items: center; gap: 0.45rem; flex-wrap: wrap; margin-bottom: 0.25rem;">
              <span class="room-code-badge">${this.escapeHtml(m.roomCode || "ROOM")}</span>
              <span class="room-game-pill">${this.escapeHtml(gameLabel)}</span>
              <strong style="font-family: var(--font-display); font-size: var(--text-sm);">
                ${this.escapeHtml(m.playerXName || "Player X")} (X) vs ${this.escapeHtml(m.playerOName || "Player O")} (O)
              </strong>
            </div>
            <div style="font-size: var(--text-xs); color: var(--text-secondary);">
              ${m.moveCount || 0} moves &middot; Winner: ${this.escapeHtml(m.winnerName || (m.outcome === "DRAW" ? "Draw" : "N/A"))} &middot; ${this.escapeHtml(dateStr)}
            </div>
          </div>
          <span class="room-status-badge ${badgeClass}">${this.escapeHtml(outcomeLabel)}</span>
        </div>
        <div class="history-row-analysis">
          <div style="display: flex; flex-wrap: wrap; gap: 0.75rem; font-family: var(--font-mono); font-size: var(--text-xs); color: var(--text-primary);">
            <span>Opening: <strong>${this.escapeHtml(analysis.openingName || "Standard")}</strong></span>
            <span>X Accuracy: <strong>${analysis.accuracyX ?? 100}%</strong></span>
            <span>O Accuracy: <strong>${analysis.accuracyO ?? 100}%</strong></span>
            <span>Threats: <strong>${(analysis.threatsCreatedX || 0) + (analysis.threatsCreatedO || 0)}</strong></span>
            <span>Blocks: <strong>${(analysis.blocksMadeX || 0) + (analysis.blocksMadeO || 0)}</strong></span>
          </div>
          <div>${this.escapeHtml(analysis.summary || "")}</div>
        </div>
      `;
      this.matchHistoryList.appendChild(row);
    });
  }

  renderLeaderboard(users) {
    if (!users.length) {
      this.leaderboardList.innerHTML = `<div class="empty-state">No ranked players yet.</div>`;
      return;
    }

    this.leaderboardList.innerHTML = "";
    users.forEach((u, index) => {
      const row = document.createElement("div");
      row.className = "history-row";
      row.innerHTML = `
        <div class="history-row-top">
          <div style="display: flex; align-items: center; gap: 0.75rem;">
            <span class="room-code-badge">#${index + 1}</span>
            <div>
              <strong style="font-family: var(--font-display); font-size: var(--text-sm);">
                ${this.escapeHtml(u.username)}
              </strong>
              <div style="font-size: var(--text-xs); color: var(--text-muted);">
                ${u.totalMatches || 0} Total Matches
              </div>
            </div>
          </div>
          <div class="mono" style="font-size: var(--text-xs); color: var(--accent-primary); font-weight: 700;">
            ${u.wins}W · ${u.losses}L · ${u.draws}D
          </div>
        </div>
      `;
      this.leaderboardList.appendChild(row);
    });
  }

  handleRealtimeEvent(message) {
    if (!message || !message.event) return;

    switch (message.event) {
      case "MATCH_STARTED":
      case "MOVE_MADE":
      case "MATCH_ENDED":
      case "REMATCH_STARTED":
      case "PLAYER_RECONNECTED":
      case "PLAYER_DISCONNECTED":
      case "PLAYER_LEFT": {
        if (message.data && this.currentRoom && message.roomCode === this.currentRoom.roomCode) {
          this.renderArena(message.data);
          if (message.event === "MATCH_STARTED") {
            this.showToast("Challenger joined! Match is now live.", "success");
          } else if (message.event === "PLAYER_DISCONNECTED") {
            this.showToast("Opponent connection interrupted.", "error");
          } else if (message.event === "PLAYER_LEFT") {
            this.showToast("Opponent left the room.", "error");
          } else if (message.event === "MATCH_ENDED") {
            this.api.getMe().then((me) => this.renderUserStats(me)).catch(() => {});
          }
        }
        break;
      }
      case "CHAT_MESSAGE": {
        if (message.data && this.currentRoom && message.roomCode === this.currentRoom.roomCode) {
          if (message.data.room) {
            this.renderArena(message.data.room);
          } else if (message.data.chatMessage) {
            const currentChats = Array.isArray(this.currentRoom.chatMessages)
              ? [...this.currentRoom.chatMessages, message.data.chatMessage]
              : [message.data.chatMessage];
            this.currentRoom.chatMessages = currentChats;
            this.renderRoomChat(currentChats);
          }
        }
        break;
      }
      case "LOBBY_ROOMS_CHANGED": {
        if (this.currentView === "dashboard" && this.currentUser) {
          this.api.listRooms().then((rooms) => this.renderOpenRoomsList(rooms || [])).catch(() => {});
        }
        break;
      }
      case "ERROR": {
        if (message.message) {
          this.showToast(message.message, "error");
        }
        break;
      }
      default:
        break;
    }
  }

  updateConnectionBadge(online) {
    this.wsStatusDot.classList.toggle("is-online", online);
    this.wsStatusDot.classList.toggle("is-offline", !online);
    this.wsStatusLabel.textContent = online ? "LIVE SYNC" : "RECONNECTING";
  }

  showToast(message, type = "info") {
    const item = document.createElement("div");
    item.className = `toast-item ${type === "error" ? "is-error" : type === "success" ? "is-success" : ""}`;
    item.innerHTML = `
      <span>${this.escapeHtml(message)}</span>
      <button type="button" class="mono" style="font-size: 0.75rem; color: var(--text-muted);">✕</button>
    `;
    item.querySelector("button").addEventListener("click", () => item.remove());
    this.toastRegion.appendChild(item);

    window.setTimeout(() => {
      if (item.parentElement) {
        item.remove();
      }
    }, 4200);
  }

  escapeHtml(raw) {
    if (raw === null || raw === undefined) return "";
    return String(raw)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#039;");
  }
}

window.addEventListener("DOMContentLoaded", () => {
  const app = new NexusApp();
  app.init();
});
