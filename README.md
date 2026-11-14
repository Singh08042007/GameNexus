# NEXUS — Real-Time Multiplayer Gaming Hub & Live Tactical Game Analyzer

[![Live Deployment](https://img.shields.io/badge/Live_App-Railway_Production-1d4ed8?style=for-the-badge)](https://nexus-gaminghub-production.up.railway.app)
[![Backend](https://img.shields.io/badge/Backend-Java_17_OOP-0f766e?style=for-the-badge)](#4-object-oriented-programming-oop-architecture--class-schema)
[![Realtime](https://img.shields.io/badge/Realtime-RFC_6455_WebSockets-a82410?style=for-the-badge)](#7-real-time-websocket-protocol)
[![Database](https://img.shields.io/badge/Database-Supabase_PostgreSQL-14161b?style=for-the-badge)](#8-supabase-database-schema--er-diagram)

**NEXUS** is a server-authoritative, real-time multiplayer gaming hub built from the ground up using a **modular Java Object-Oriented Programming (OOP) backend**, a **custom RFC 6455 WebSocket server**, **Supabase Authentication & PostgreSQL persistence**, and an **Architectural Daylight HTML5 / CSS3 / Vanilla ES6+ frontend**.

Players can register or sign in, create or join 6-character room codes, choose from **four competitive 2-player grid games**, chat live inside the room with quick tactical presets, and inspect **live & post-match tactical game analysis** (move accuracy, winning threat detection, defensive saves, board control dominance, and opening classification).

- **Live Production App:** [https://nexus-gaminghub-production.up.railway.app](https://nexus-gaminghub-production.up.railway.app)
- **GitHub Repository:** [https://github.com/Singh08042007/GameNexus.git](https://github.com/Singh08042007/GameNexus.git)
- **Detailed Team Documentation:** [`team details.md`](./team%20details.md) / [`TEAM_DETAILS.md`](./TEAM_DETAILS.md)

---

## 1. Team Details

| # | Teammate Name | Roll No. | Role & Focus Area |
| :-: | :--- | :--- | :--- |
| 1 | **Deepinder Singh** | `247046` | Lead Backend Architect, Supabase Cloud Integration & Railway Docker Deployment |
| 2 | **Gourav Garg** | `2547061` | Multi-Game Engine Developer & Tactical Game Analysis (`GameAnalyzer`) |
| 3 | **Harsh Dhiman** | `2547068` | RFC 6455 WebSocket Server, Matchmaking Rooms & Live Room Chatroom |
| 4 | **Hitika** | `2547077` | Architectural Daylight Frontend UI/UX, Dynamic Board Renderer & Automated Testing |

> See [`team details.md`](./team%20details.md) for a full module-by-module breakdown of each teammate's contributions.

---

## 2. Key Features

### Four Playable Multiplayer Grid Games
Every room lets the host pick one of four server-validated board games powered by polymorphic Java game engines:
1. **Tic-Tac-Toe (`3×3` Classic Grid · Align 3)**: Fast-paced tactical duel with center/corner opening classification and fork detection.
2. **Connect Four (`6×7` Gravity Drop Rack · Align 4)**: Players select any column (`Col 1`–`Col 7`); discs automatically drop to the lowest unoccupied row in that column.
3. **Gomoku (`9×9` Strategy Board · Align 5)**: Deep 81-cell positional strategy where players build intersecting lines to align 5 consecutive stones.
4. **Quad-Tac-Toe (`4×4` Extended Arena · Align 4)**: Expanded 16-cell tactical arena emphasizing central `2×2` core control.

### Real-Time In-Room Chatroom
- **Instant Bidirectional Messaging**: Players in a room can chat before, during, and after matches over WebSockets (`SEND_CHAT` / `CHAT_MESSAGE`) with REST fallback (`POST /api/rooms/chat`).
- **Tactical Quick-Chat Presets**: One-click chips (*"Good luck!"*, *"Nice move!"*, *"Watch that fork!"*, *"Great block!"*, *"Rematch?"*).
- **Automated Arena Director Announcements**: System messages announce room creation, challenger arrival, match victories/draws/forfeits, and rematch resets.

### Live & Post-Match Tactical Game Analysis (`GameAnalyzer`)
After every move—and in the Match History archive—the Java `GameAnalyzer` simulates board lines to compute:
- **Move-by-Move Classification**:
  - `WINNING_STRIKE` (+100 score) — Completes the required N-in-a-row line
  - `DOUBLE_THREAT_FORK` (+95 score) — Creates 2+ simultaneous winning threats
  - `CRITICAL_BLOCK` (+90 score) — Denies an opponent's immediate winning threat
  - `TACTICAL_THREAT` (+82 score) — Sets up a 1-move-away winning line
  - `CENTER_CONTROL` (+78 score) — Claims high-value central board territory
  - `POSITIONAL_PLAY` (+72 score) — Standard developing move
  - `MISSED_DEFENSE` (+35 score) — Fails to block an opponent's immediate threat
- **Player X & Player O Move Accuracy (%)**
- **Board Control Dominance Share (`X Control %` vs `O Control %`)**
- **Opening Classification, Key Turning Point & Executive Summary**

---

## 3. System Workflow & Architecture Flowcharts

### 3.1 High-Level System Architecture & Cloud Ingress Flowchart

```mermaid
flowchart TB
    subgraph Client["Client Layer (Browser SPA - Vanilla ES6+ & Daylight CSS)"]
        UI["Multi-Game Arena UI (index.html + nexus.css)"]
        Controller["NexusApp Controller (app.js)"]
        RESTClient["ApiClient Fetch Wrapper (api.js)"]
        WSClient["RealtimeClient WebSocket (ws.js)"]
        UI --> Controller
        Controller --> RESTClient
        Controller --> WSClient
    end

    subgraph Ingress["Cloud & Container Edge (Railway / Docker)"]
        Nginx["Nginx Single-Port Router ($PORT: 8080)"]
    end

    subgraph JavaBackend["Java 17 OOP Backend (NexusApplication)"]
        subgraph Transport["Transport & Protocol Layer"]
            HttpSrv["ApiController (HTTP/1.1 Server :18080)"]
            WsSrv["WebSocketHub (RFC 6455 Server :18081)"]
        end

        subgraph Services["Business Logic & Service Layer"]
            AuthSvc["AuthService (Token & Session Auth)"]
            RoomSvc["RoomService (Lifecycle, Capacity & Chat)"]
            GameSvc["GameService (Synchronized Move Execution)"]
        end

        subgraph Engines["Polymorphic Game Engine & Tactical AI"]
            Factory["GameEngineFactory"]
            TTT["TicTacToeEngine (3x3 - Align 3)"]
            C4["ConnectFourEngine (6x7 Gravity - Align 4)"]
            GMK["GomokuEngine (9x9 - Align 5)"]
            QTT["QuadTacToeEngine (4x4 - Align 4)"]
            Analyzer["GameAnalyzer (Accuracy, Forks, Blocks & Control)"]
        end

        subgraph DataLayer["Repository & Persistence Layer"]
            Repo["DataRepository (ConcurrentHashMap + DTO Mapper)"]
            LocalStore["Local Resilient Snapshot (data/nexus_store.json)"]
            SupaClient["SupabaseClient (Server-Only Secret Key)"]
        end
    end

    subgraph SupabaseCloud["Supabase Cloud Platform"]
        SupaAuth["Supabase Auth (/auth/v1)"]
        SupaDB["Supabase PostgreSQL + RLS (/rest/v1)"]
    end

    RESTClient -->|"HTTPS REST (/api/*)"| Nginx
    WSClient <-->|"WSS Upgrade (/ws)"| Nginx
    Nginx -->|"Proxy / & /api/*"| HttpSrv
    Nginx <-->|"Upgrade /ws"| WsSrv

    HttpSrv --> AuthSvc
    HttpSrv --> RoomSvc
    HttpSrv --> GameSvc
    WsSrv <--> RoomSvc
    WsSrv <--> GameSvc

    GameSvc --> Factory
    RoomSvc --> Factory
    Factory --> TTT
    Factory --> C4
    Factory --> GMK
    Factory --> QTT
    GameSvc --> Analyzer

    AuthSvc --> Repo
    RoomSvc --> Repo
    GameSvc --> Repo
    Repo <--> LocalStore
    Repo --> SupaClient
    SupaClient --> SupaAuth
    SupaClient --> SupaDB
```

---

### 3.2 End-to-End Player & Matchmaking Workflow

```mermaid
flowchart LR
    Start(["Player Opens NEXUS"]) --> AuthCheck{"Authenticated?"}
    AuthCheck -->|"No"| AuthView["Register / Sign In (/api/auth/*)"]
    AuthView --> TokenIssued["Bearer Token Issued + WebSocket Connected"]
    AuthCheck -->|"Yes"| TokenIssued

    TokenIssued --> Lobby["Game Hub & Lobby View"]
    Lobby --> ActionChoice{"Create or Join?"}

    ActionChoice -->|"Create Room"| PickGame["Pick Game Mode (3x3, 6x7, 9x9, 4x4)"]
    PickGame --> CreateRoom["POST /api/rooms/create (Status: WAITING)"]
    CreateRoom --> WaitGuest["Host Enters Arena & Shares 6-Char Room Code"]

    ActionChoice -->|"Join Room"| EnterCode["Enter 6-Char Code or Click Open Room"]
    EnterCode --> JoinRoom["POST /api/rooms/join (Status: IN_PROGRESS)"]

    WaitGuest --> MatchStart["2/2 Players Connected (Broadcast ROOM_JOINED)"]
    JoinRoom --> MatchStart

    MatchStart --> ArenaLoop["3-Column Tactical Arena Active"]

    subgraph ArenaLoop["Real-Time Gameplay, Chat & Telemetry Loop"]
        direction TB
        ChatAction["Send Chat / Quick Preset"] --> BroadcastChat["Broadcast CHAT_MESSAGE to Room"]
        MoveAction["Player Clicks Cell / Column"] --> ValidateMove{"Server Validates Turn & Cell"}
        ValidateMove -->|"Invalid"| RejectMove["Return 422 InvalidMoveException"]
        ValidateMove -->|"Valid"| ApplyMove["Update Board + Run GameAnalyzer"]
        ApplyMove --> CheckEnd{"Win or Draw?"}
    end

    CheckEnd -->|"No (Next Turn)"| BroadcastMove["Broadcast MOVE_MADE + Live Analysis"]
    BroadcastMove --> MoveAction

    CheckEnd -->|"Yes (Match Complete)"| RecordStats["Update W/L/D Stats & Persist Match"]
    RecordStats --> BroadcastEnd["Broadcast MATCH_ENDED + Full Analysis Report"]
    BroadcastEnd --> PostMatch{"Rematch or Leave?"}

    PostMatch -->|"Play Rematch"| SwapRoles["Swap X/O Symbols & Reset Board (REMATCH_STARTED)"]
    SwapRoles --> MoveAction
    PostMatch -->|"Leave / History"| HistoryView["View Match Analysis Archive & Leaderboard"]
```

---

### 3.3 Real-Time Move Validation, Chat & Analysis Sequence Diagram

```mermaid
sequenceDiagram
    autonumber
    actor PX as Player X (Host Browser)
    actor PO as Player O (Challenger Browser)
    participant API as ApiController / WebSocketHub
    participant GS as GameService (Synchronized Lock)
    participant GE as Polymorphic GameEngine
    participant GA as GameAnalyzer
    participant DB as DataRepository & Supabase

    Note over PX,PO: Room #NX4820 is IN_PROGRESS (Connect Four / Tic-Tac-Toe / Gomoku / Quad-Tac-Toe)

    PX->>API: POST /api/game/move {roomCode: "NX4820", row: 2, col: 3}
    API->>GS: submitMove(userX, "NX4820", 2, 3)
    activate GS
    Note right of GS: Acquires synchronized(room) lock
    GS->>GE: validateAndApplyMove('X', 2, 3)
    activate GE
    GE-->>GS: MoveResult(valid=true, actualRow=5, actualCol=3, outcome=IN_PROGRESS)
    deactivate GE

    GS->>DB: saveMove(move) & saveRoom(room)
    GS->>GA: analyze(gameType, rows, cols, winLength, moves, outcome)
    activate GA
    GA-->>GS: AnalysisReport(accuracyX, accuracyO, controlShare, moveEvaluations)
    deactivate GA

    GS->>API: broadcastToRoom("NX4820", Event="MOVE_MADE", roomDtoWithAnalysis)
    deactivate GS

    par Real-Time WebSocket Push
        API-->>PX: WS Frame: {"event": "MOVE_MADE", "payload": {...}}
        API-->>PO: WS Frame: {"event": "MOVE_MADE", "payload": {...}}
    end

    PO->>API: WS Frame: {"action": "SEND_CHAT", "roomCode": "NX4820", "message": "Nice move!"}
    API->>DB: Append ChatMessage to Room
    par Instant Chat Broadcast
        API-->>PX: WS Frame: {"event": "CHAT_MESSAGE", "message": {...}}
        API-->>PO: WS Frame: {"event": "CHAT_MESSAGE", "message": {...}}
    end
```

---

### 3.4 Room Lifecycle State Machine

```mermaid
stateDiagram-v2
    [*] --> WAITING : Host creates room (Player X assigned)
    WAITING --> IN_PROGRESS : Challenger joins room (Player O assigned, 2/2)
    WAITING --> ABANDONED : Host leaves before challenger joins

    IN_PROGRESS --> IN_PROGRESS : Valid move applied (Turn toggles X <-> O)
    IN_PROGRESS --> COMPLETED : Winning line aligned (X_WON / O_WON) or Board full (DRAW)
    IN_PROGRESS --> COMPLETED : Player leaves mid-match (FORFEIT win awarded to remaining player)

    COMPLETED --> IN_PROGRESS : Play Rematch triggered (X/O swapped, board reset)
    COMPLETED --> ABANDONED : Both players leave room
    ABANDONED --> [*]
```

---

## 4. Object-Oriented Programming (OOP) Architecture & Class Schema

The backend (`src/main/java/com/nexus/gaming/`) is designed as a clean demonstration of core Java OOP principles:

```mermaid
classDiagram
    class BaseEntity {
        <<abstract>>
        -String id
        -String createdAt
        -String updatedAt
        +getId() String
        +touch() void
    }

    class User {
        -String username
        -String email
        -String passwordHash
        -int wins
        -int losses
        -int draws
        +recordWin() void
        +recordLoss() void
        +recordDraw() void
    }

    class Room {
        -String roomCode
        -String roomName
        -GameType gameType
        -RoomStatus status
        -Player playerX
        -Player playerO
        -GameEngine gameEngine
        -List~ChatMessage~ chatMessages
        +isFull() boolean
        +startNewMatch() Match
    }

    class Match {
        -String roomCode
        -GameType gameType
        -MatchOutcome outcome
        -String finalBoard
        -List~Move~ moves
        +recordMove(Move, String) void
        +complete(MatchOutcome, String, String) void
    }

    class GameEngine {
        <<interface>>
        +getGameType() GameType
        +getRows() int
        +getCols() int
        +getWinLength() int
        +validateAndApplyMove(char, int, int) MoveResult
        +checkWinCondition() MatchOutcome
        +getWinningLine() int[]
        +resetBoard() void
    }

    class AbstractBoardGame {
        <<abstract>>
        #int rows
        #int cols
        #int winLength
        #char[][] board
        #char currentTurn
        #int moveCount
        +validateAndApplyMove(char, int, int) MoveResult
        # evaluateLineWin() MatchOutcome
    }

    class TicTacToeEngine {
        +TicTacToeEngine() 3x3 Align 3
    }

    class ConnectFourEngine {
        +ConnectFourEngine() 6x7 Gravity Align 4
        +validateAndApplyMove(char, int, int) MoveResult
    }

    class GomokuEngine {
        +GomokuEngine() 9x9 Align 5
    }

    class QuadTacToeEngine {
        +QuadTacToeEngine() 4x4 Align 4
    }

    class GameEngineFactory {
        +createEngine(GameType)$ GameEngine
    }

    class GameAnalyzer {
        +analyze(GameType, int, int, int, List~Move~, MatchOutcome)$ Map
    }

    BaseEntity <|-- User
    BaseEntity <|-- Room
    BaseEntity <|-- Match
    GameEngine <|.. AbstractBoardGame
    AbstractBoardGame <|-- TicTacToeEngine
    AbstractBoardGame <|-- ConnectFourEngine
    AbstractBoardGame <|-- GomokuEngine
    AbstractBoardGame <|-- QuadTacToeEngine
    GameEngineFactory ..> GameEngine : instantiates
    Room *-- GameEngine : delegates rules
    Room ..> GameAnalyzer : generates telemetry
```

| OOP Principle | Implementation in NEXUS |
| :--- | :--- |
| **Abstraction** | `GameEngine` interface and `AbstractBoardGame` abstract base class define the contract (`validateAndApplyMove`, `getBoardString`, `getWinningLine`, `checkWinCondition`) while hiding N-in-a-row directional scanning mechanics. |
| **Inheritance** | `TicTacToeEngine`, `ConnectFourEngine`, `GomokuEngine`, and `QuadTacToeEngine` inherit board state management and win detection from `AbstractBoardGame`. Domain models (`User`, `Player`, `Room`, `Match`, `Move`) inherit identity and timestamps from `BaseEntity`. |
| **Polymorphism** | `Room` holds a polymorphic `GameEngine` reference created via `GameEngineFactory.createEngine(GameType)`. `ConnectFourEngine` overrides `validateAndApplyMove()` to implement gravity-based column dropping while sharing directional win detection. |
| **Encapsulation** | All domain models (`Room`, `Match`, `User`, `ChatMessage`, `AppConfig`) keep state fields `private`, expose controlled mutators, and synchronize state transitions. |
| **Factory Pattern** | `GameEngineFactory` instantiates the concrete `GameEngine` implementation corresponding to the room's selected `GameType`. |
| **Custom Exception Hierarchy** | `NexusException` base runtime exception with HTTP status codes, extended by `AuthenticationException` (401), `AuthorizationException` (403), `ResourceNotFoundException` (404), `RoomException` (400/409), and `InvalidMoveException` (422). |
| **Thread Safety & Concurrency** | `ConcurrentHashMap` repositories and room-level `synchronized (room)` locks guarantee that simultaneous moves or join requests never corrupt board state. |

---

## 5. Project Directory Structure

```text
Nexus-GamingHUB/
├── Dockerfile                      # Multi-stage Java 17 + Nginx single-port container build
├── .env.example                    # Template for environment variables
├── README.md                       # Project documentation & workflow schemas
├── TEAM_DETAILS.md                 # Team roster & module ownership
├── team details.md                 # Team roster & module ownership
├── database/
│   └── schema.sql                  # Supabase PostgreSQL tables, constraints, triggers & RLS
├── scripts/
│   ├── run.ps1                     # Compiles and starts the local NEXUS server
│   └── test.ps1                    # Compiles and runs the 29-test verification suite
├── frontend/
│   ├── index.html                  # Single-page app (Landing, Auth, Lobby, Arena, Analysis)
│   ├── css/
│   │   ├── tokens.css              # Warm Alabaster & Crisp Paper daylight design tokens
│   │   ├── base.css                # Reset, typography & accessible browser surfaces
│   │   └── nexus.css               # 3-column Arena, 4 board grids, chatroom & telemetry UI
│   └── js/
│       ├── api.js                  # Fetch REST client with Bearer token session storage
│       ├── ws.js                   # Auto-reconnecting RFC 6455 WebSocket client
│       └── app.js                  # Main UI controller, board renderer & analysis binder
└── src/
    ├── main/java/com/nexus/gaming/
    │   ├── NexusApplication.java   # Server entry point & WebSocket message router
    │   ├── config/                 # EnvLoader & immutable AppConfig
    │   ├── controller/             # ApiController (REST endpoints + static file server)
    │   ├── dto/                    # DtoMapper (JSON payloads + live GameAnalyzer injection)
    │   ├── engine/                 # GameEngine, AbstractBoardGame, 4 engines, Factory, Analyzer
    │   ├── exception/              # Custom HTTP-mapped exception hierarchy
    │   ├── model/                  # BaseEntity, User, Player, Room, Match, Move, ChatMessage
    │   ├── repository/             # Repository<T>, DataRepository, SupabaseClient
    │   ├── service/                # AuthService, RoomService, GameService
    │   ├── util/                   # Zero-dependency JsonUtil parser/serializer & SecurityUtil
    │   └── websocket/              # RFC 6455 WebSocketHub & WebSocketConnection
    └── test/java/com/nexus/gaming/
        └── NexusTestSuite.java     # 29 end-to-end automated unit & concurrency tests
```

---

## 6. REST API Specification

| Method | Endpoint | Auth | Description |
| :--- | :--- | :-: | :--- |
| `GET` | `/api/health` | No | Returns server status, WebSocket port, and Supabase connectivity |
| `GET` | `/api/games` | No | Lists all 4 supported games (`rows`, `cols`, `winLength`, description) |
| `POST` | `/api/auth/register` | No | Registers a new player account (`username`, `email`, `password`) |
| `POST` | `/api/auth/login` | No | Authenticates via email/username + password and issues a session token |
| `POST` | `/api/auth/logout` | Yes | Invalidates the caller's active Bearer token |
| `GET` | `/api/auth/me` | Yes | Returns the authenticated player's profile and career W/L/D stats |
| `GET` | `/api/rooms` | Yes | Lists all open/active multiplayer rooms in the lobby |
| `POST` | `/api/rooms/create` | Yes | Creates a new room with chosen `roomName` and `gameType` |
| `POST` | `/api/rooms/join` | Yes | Joins a room by 6-character `roomCode` (or reconnects existing member) |
| `POST` | `/api/rooms/leave` | Yes | Leaves a room (triggers `FORFEIT` win for remaining player if mid-match) |
| `POST` | `/api/rooms/rematch` | Yes | Resets the board and swaps X/O roles for an immediate rematch |
| `POST` | `/api/rooms/chat` | Yes | Posts a real-time chat message to the room and broadcasts via WebSocket |
| `GET` | `/api/rooms/{CODE}` | Yes | Fetches full room state, board array, chat history, and live analysis |
| `POST` | `/api/game/move` | Yes | Validates and applies a move (`roomCode`, `row`, `col`) |
| `GET` | `/api/matches` | Yes | Returns recent completed matches with full post-game analysis reports |
| `GET` | `/api/matches/analysis/{ID}` | Yes | Returns deep tactical analysis for a specific match ID |
| `GET` | `/api/leaderboard` | Yes | Returns top players ranked by wins and win rate |

---

## 7. Real-Time WebSocket Protocol

- **Endpoint:** `ws://localhost:8081/ws?token=<TOKEN>&roomCode=<CODE>` (or `wss://<domain>/ws` in production)
- **Client → Server Actions:**
  - `{"action": "SUBSCRIBE_ROOM", "roomCode": "NX4820"}`
  - `{"action": "MAKE_MOVE", "roomCode": "NX4820", "row": 1, "col": 1}`
  - `{"action": "SEND_CHAT", "roomCode": "NX4820", "message": "Good luck!"}`
  - `{"action": "PING"}`
- **Server → Client Broadcast Events:**
  - `ROOM_CREATED`, `ROOM_JOINED`, `ROOM_UPDATED`, `ROOM_LEFT`, `REMATCH_STARTED`, `LOBBY_UPDATED`
  - `MOVE_MADE`, `MATCH_ENDED`
  - `CHAT_MESSAGE`
  - `PONG`, `ERROR`

---

## 8. Supabase Database Schema & ER Diagram

### 8.1 Entity-Relationship Schema (`database/schema.sql`)

```mermaid
erDiagram
    PROFILES ||--o{ ROOMS : "hosts / joins"
    PROFILES ||--o{ ROOM_PARTICIPANTS : "participates"
    ROOMS ||--o{ ROOM_PARTICIPANTS : "contains"
    ROOMS ||--o{ MATCHES : "hosts"
    PROFILES ||--o{ MATCHES : "plays as X or O"
    MATCHES ||--o{ MATCH_MOVES : "logs"
    PROFILES ||--o{ MATCH_MOVES : "executes"
    ROOMS ||--o{ ROOM_MESSAGES : "streams"
    PROFILES ||--o{ ROOM_MESSAGES : "sends"

    PROFILES {
        uuid id PK
        varchar email UK
        varchar username UK
        int wins
        int losses
        int draws
        timestamptz created_at
        timestamptz updated_at
    }

    ROOMS {
        uuid id PK
        varchar room_code UK
        varchar room_name
        varchar game_type
        uuid host_id FK
        uuid guest_id FK
        varchar status
        int max_players
        uuid current_match_id
    }

    ROOM_PARTICIPANTS {
        uuid id PK
        uuid room_id FK
        uuid user_id FK
        char player_symbol
        boolean is_active
    }

    MATCHES {
        uuid id PK
        uuid room_id FK
        varchar room_code
        varchar game_type
        uuid player_x_id FK
        uuid player_o_id FK
        uuid winner_id FK
        varchar outcome
        varchar final_board
        int move_count
    }

    MATCH_MOVES {
        bigint id PK
        uuid match_id FK
        uuid player_id FK
        char player_symbol
        int row_index
        int col_index
        int move_number
        varchar board_state_after
    }

    ROOM_MESSAGES {
        uuid id PK
        varchar room_code
        uuid sender_id FK
        varchar sender_name
        varchar sender_symbol
        text message
        timestamptz created_at
    }
```

### 8.2 Provisioning Steps
1. Create a Supabase project and copy `.env.example` to `.env` with your `SUPABASE_URL`, `SUPABASE_ANON_KEY`, and `SUPABASE_SECRET_KEY`.
2. Open **Supabase Dashboard → SQL Editor → New Query**.
3. Paste and run [`database/schema.sql`](./database/schema.sql) to provision all tables, constraints, triggers (`on_auth_user_created`), and Row-Level Security (RLS) policies.

---

## 9. Local Setup, Testing & Deployment

### Prerequisites
- **Java JDK 17+** (compatible with Java 8 through Java 25)
- **PowerShell** (Windows) or POSIX shell

### Run the 29-Test Automated Verification Suite
```powershell
powershell -ExecutionPolicy Bypass -File scripts/test.ps1
```
Verifies all 4 game engines (`TicTacToe`, `ConnectFour`, `Gomoku`, `QuadTacToe`), `GameAnalyzer` accuracy/evaluations, user registration/login, room capacity, live chat authorization, move validation, simultaneous move race conditions, forfeit handling, and persistence.

### Start the Local Server
```powershell
powershell -ExecutionPolicy Bypass -File scripts/run.ps1
```
- **Web App & REST API:** `http://localhost:8080`
- **WebSocket Server:** `ws://localhost:8081/ws`

### Deploy with Docker / Railway
```bash
docker build -t nexus-gaminghub .
docker run -p 8080:8080 --env-file .env nexus-gaminghub
```
Or deploy directly to Railway:
```bash
railway up
```
