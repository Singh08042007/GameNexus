# Team Details — NEXUS: Real-Time Multiplayer Gaming Hub

**Project Title:** NEXUS — Real-Time Multiplayer Gaming Hub & Live Tactical Game Analyzer  
**Repository:** [https://github.com/Singh08042007/GameNexus.git](https://github.com/Singh08042007/GameNexus.git)  
**Live Deployment:** [https://nexus-gaminghub-production.up.railway.app](https://nexus-gaminghub-production.up.railway.app)

---

## Team Roster

| # | Teammate Name | Roll No. | Primary Role | Key Modules & Contributions |
| :-: | :--- | :--- | :--- | :--- |
| 1 | **Deepinder Singh** | `247046` | Lead Backend Architect & Cloud Deployment | Java OOP Architecture (`NexusApplication`, `ApiController`), Polymorphic Multi-Game Engine (`GameEngine`, `AbstractBoardGame`, `GameEngineFactory`), Supabase Cloud Integration (`SupabaseClient`, `DataRepository`), Docker & Railway Production Deployment |
| 2 | **Gourav Garg** | `2547061` | Game Engine & Tactical AI Analysis Engineer | Game Rules Implementation (`TicTacToeEngine`, `ConnectFourEngine`, `GomokuEngine`, `QuadTacToeEngine`), Live & Post-Game Tactical Analysis Engine (`GameAnalyzer`), Move Accuracy & Threat Telemetry |
| 3 | **Harsh Dhiman** | `2547068` | Real-Time Networking & Room Systems Engineer | RFC 6455 WebSocket Hub (`WebSocketHub`, `WebSocketConnection`), Room Lifecycle & Matchmaking (`RoomService`, `GameService`), In-Room Live Chatroom (`ChatMessage`), Concurrency & Race-Condition Protection |
| 4 | **Hitika** | `2547077` | Frontend UI/UX & Interactive Arena Designer | Architectural Daylight UI Theme (`tokens.css`, `base.css`, `nexus.css`), Multi-Game Dynamic Board Renderer (`index.html`, `app.js`), Real-Time WebSocket & REST Client Integration (`ws.js`, `api.js`), Automated Verification Suite |

---

## Module Ownership Summary

### 1. Deepinder Singh (Roll No: `247046`)
- Designed the layered **Java Object-Oriented Programming (OOP)** backend architecture (`controller`, `service`, `engine`, `repository`, `model`, `dto`, `exception`, `config`).
- Engineered the dual-persistence layer (`SupabaseClient.java` + `DataRepository.java`) and PostgreSQL schema (`database/schema.sql`) with Row-Level Security (RLS).
- Configured multi-stage Docker containerization (`Dockerfile`) with Nginx single-port HTTP/WebSocket routing and deployed the live production app on Railway.

### 2. Gourav Garg (Roll No: `2547061`)
- Built the 4 polymorphic grid game engines:
  - **Tic-Tac-Toe** (`3×3` Grid — Align 3)
  - **Connect Four** (`6×7` Gravity Drop Rack — Align 4)
  - **Gomoku** (`9×9` Strategy Board — Align 5)
  - **Quad-Tac-Toe** (`4×4` Extended Arena — Align 4)
- Developed `GameAnalyzer.java` to evaluate move quality (`WINNING_STRIKE`, `DOUBLE_THREAT_FORK`, `CRITICAL_BLOCK`, `TACTICAL_THREAT`, `CENTER_CONTROL`, `POSITIONAL_PLAY`, `MISSED_DEFENSE`), player move accuracy percentages, opening classifications, and board control balance.

### 3. Harsh Dhiman (Roll No: `2547068`)
- Implemented the custom zero-dependency **RFC 6455 WebSocket Server** (`WebSocketHub.java` & `WebSocketConnection.java`) supporting SHA-1 handshakes, frame masking/unmasking, ping/pong heartbeats, and room-scoped pub/sub broadcasting.
- Built the **In-Room Live Chatroom** (`ChatMessage.java`, `SEND_CHAT` WebSocket event, `POST /api/rooms/chat`, and automated Arena Director system announcements).
- Enforced thread-safe room synchronization (`synchronized` room locks and `ConcurrentHashMap` stores) to prevent simultaneous move race conditions.

### 4. Hitika (Roll No: `2547077`)
- Crafted the **Architectural Daylight Light-Themed UI** (`frontend/css/tokens.css`, `frontend/css/base.css`, `frontend/css/nexus.css`) and 3-column tactical arena layout.
- Built the Vanilla ES6+ frontend controller (`frontend/js/app.js`, `frontend/js/api.js`, `frontend/js/ws.js`) with dynamic `3×3`, `4×4`, `6×7`, and `9×9` board rendering, Connect Four column drop controls, quick-chat preset chips, and live telemetry charts.
- Contributed to the 29-test automated verification suite (`NexusTestSuite.java`).
