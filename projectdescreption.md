# NEXUS — Full-Stack Development & Implementation Prompt

You are an experienced Full-Stack Software Engineer and Java Backend Developer. Your task is to design, develop, and implement **NEXUS — Real-Time Multiplayer Gaming Hub**, following the existing NEXUS Project Blueprint PDF as the primary and authoritative specification.

Your objective is to transform the project blueprint into a fully functional, modern, responsive, secure and polished multiplayer gaming platform.

## 1. Mandatory First Step: Analyze the Project Blueprint

Before writing any code:

1. Locate and read the complete NEXUS Project Blueprint PDF available in the project folder.
2. Carefully analyze every section, including:
   - Project objectives and requirements.
   - Functional and non-functional requirements.
   - System architecture.
   - Technology stack.
   - Database schema and relationships.
   - Java OOP implementation requirements.
   - Game mechanics and multiplayer functionality.
   - Development milestones and roadmap.
3. Treat the PDF as the single source of truth.
4. Do not assume that any existing source code, database, configuration or application implementation exists.
5. Identify the required components and propose an implementation plan before starting development.

**Important:** Never skip blueprint analysis or replace its architectural decisions with your own assumptions.

## 2. Required Technology Stack

Develop the application using the following technologies:

### Frontend
- HTML5 — Application structure.
- CSS3 — Styling, animations, responsive layouts and visual design.
- Vanilla JavaScript (ES6+) — Client-side functionality and API communication.

Do not use React, Angular, Vue or other frontend frameworks unless explicitly required by the project blueprint.

### Backend
- Java — Primary backend programming language.
- Follow the Java architecture and frameworks specified in the PDF.
- Implement clean OOP-based architecture.
- Use REST APIs for frontend-backend communication.
- Implement WebSocket-based real-time communication if specified by the blueprint.

### Database and Backend Services
- Supabase — Database and supporting backend services.
- PostgreSQL — Supabase's underlying relational database.
- Supabase Authentication or the authentication approach specified in the blueprint.
- Supabase Storage only if required by the project.

**Important architectural requirement:**

Java must remain the primary application backend. Supabase must complement the Java backend rather than replace it.

All critical game logic, business rules, authorization checks, room management and move validation must be handled by the Java backend.

Never expose the Supabase service-role key or database credentials in frontend JavaScript.

## 3. Supabase Integration and Configuration

I will provide the following Supabase credentials:

- Supabase Project URL: (https://zkvmrtknkrzkmzfkvqpj.supabase.co)
- Supabase Anon/Publishable Key: <SUPABASE_ANON_KEY_FROM_ENV>
- Supabase Database Connection Details: 
defualt key <SUPABASE_ANON_KEY_FROM_ENV>
secret keys <SUPABASE_SECRET_KEY_FROM_ENV>


Integration requirements:

1. Configure Supabase according to the database architecture in the blueprint.
2. Create the required PostgreSQL tables, relationships, constraints and indexes.
3. Implement appropriate Row Level Security (RLS) policies.
4. Configure authentication securely.
5. Use environment variables for sensitive configuration.
6. Never hardcode secret credentials into source files.
7. Never expose the Supabase service-role key, database password or other privileged credentials to the frontend.
8. Keep database access organized and consistent with the Java backend architecture.
9. Implement proper error handling for database operations.
10. Verify database connectivity before implementing dependent functionality.

If direct PostgreSQL connectivity is required, use an appropriate Java database integration approach such as JDBC, provided it is compatible with the blueprint.

Do not introduce unnecessary database technologies.

## 4. Frontend Development — Modern Gaming Experience

Build a visually impressive, premium-quality gaming interface.

The design should feel like a professional multiplayer gaming platform rather than a basic college project.

### Design Direction

Follow these principles:

- Modern and minimalistic UI.
- Premium gaming dashboard aesthetics.
- Clean layouts with sufficient breathing space.
- Consistent typography and visual hierarchy.
- Carefully selected color palette.
- Smooth transitions and subtle animations.
- Beautiful buttons, cards, modals and navigation.
- Responsive design for desktop, tablet and mobile.
- Consistent spacing, borders, shadows and component styling.
- Clear loading, success, empty and error states.

Avoid:
- Overcrowded interfaces.
- Excessive gradients and unnecessary animations.
- Inconsistent components.
- Unnecessary visual elements.
- Poor mobile responsiveness.

### Required Frontend Sections

Implement the pages and components required by the blueprint, including the relevant MVP screens:

1. Landing Page
   - NEXUS branding.
   - Platform introduction.
   - Gaming-focused hero section.
   - Login and registration navigation.
   - Call-to-action buttons.

2. Authentication
   - Registration.
   - Login.
   - Logout.
   - Form validation.
   - Authentication error handling.

3. User Dashboard
   - User profile.
   - Available games.
   - Create Room.
   - Join Room.
   - Active match information.

4. Multiplayer Room
   - Room ID and room information.
   - Player information.
   - Player waiting status.
   - Join and leave functionality.
   - Match-starting state.

5. Tic-Tac-Toe Game Interface
   - Interactive 3×3 board.
   - Player X and Player O indicators.
   - Current turn information.
   - Real-time board updates.
   - Win, loss and draw states.
   - Restart or rematch functionality where specified.

6. Match Results
   - Match outcome.
   - Player information.
   - Match history if required by the blueprint.

All pages must be connected to actual backend functionality. Do not create buttons or interfaces that only look functional.

## 5. Java Backend Architecture

Implement a modular Java backend following the architecture documented in the PDF.

Maintain clear separation of responsibilities.

Suggested logical layers, subject to the blueprint:

- Controller Layer — Handles incoming HTTP/WebSocket requests.
- Service Layer — Contains business logic.
- Repository Layer — Handles database operations.
- Model/Entity Layer — Represents application data.
- DTO Layer — Handles request and response objects.
- Configuration Layer — Manages application configuration.
- Exception Layer — Handles application errors.
- Game Engine Layer — Contains game-specific rules and logic.

### OOP Requirements

Demonstrate proper implementation of:

- Encapsulation.
- Inheritance.
- Abstraction.
- Polymorphism.
- Interfaces.
- Constructors.
- Method overriding.
- Exception handling.
- Collections.
- Java networking and concurrency where required.

Avoid unnecessary abstractions. Every class should have a clear responsibility.

## 6. Core MVP Functionality

Implement the MVP incrementally in the following order.

### Milestone 1 — Project Foundation
- Initialize the frontend and Java backend.
- Configure the required dependencies.
- Establish the database connection.
- Set up environment variables.
- Create the initial project structure.
- Verify that the frontend, backend and database communicate correctly.

### Milestone 2 — Authentication
- User registration.
- User login.
- Secure authentication.
- Session/token management.
- Protected API endpoints.
- Logout.

### Milestone 3 — Room Management
- Create multiplayer rooms.
- Generate unique room IDs.
- Join rooms using room IDs.
- Restrict rooms to the permitted player count.
- Handle invalid room IDs.
- Handle duplicate joining.
- Maintain room and player states.
- Handle player disconnection.

### Milestone 4 — Real-Time Multiplayer Tic-Tac-Toe
- Support exactly two players per match.
- Assign X and O.
- Synchronize the game board.
- Display whose turn it is.
- Broadcast valid moves.
- Maintain consistent game state.
- Handle player disconnections.

### Milestone 5 — Server-Authoritative Game Engine

This is a critical requirement.

The Java backend must be responsible for validating every move.

Implement:
- Turn validation.
- Player identity validation.
- Room membership validation.
- Cell availability validation.
- Game status validation.
- Win detection.
- Draw detection.
- Match completion.
- Prevention of duplicate or invalid moves.

Never trust the frontend to determine whether a player has won or whether a move is valid.

The frontend should only display the authoritative game state received from the server.

### Milestone 6 — Database Persistence
Persist the relevant information according to the blueprint:

- User information.
- Room information.
- Player participation.
- Match records.
- Match outcomes.
- Game history.

Use appropriate database constraints and transactions to maintain data consistency.

### Milestone 7 — Testing and Stabilization
- Test registration and login.
- Test room creation and joining.
- Test two-player synchronization.
- Test invalid moves.
- Test simultaneous requests.
- Test win and draw conditions.
- Test player disconnection.
- Test database persistence.
- Test API error responses.
- Test responsive frontend layouts.

Do not proceed to advanced functionality until the MVP works reliably.

## 7. Security Requirements

Follow secure development practices throughout the application.

- Validate all incoming requests.
- Implement authentication and authorization.
- Protect private API endpoints.
- Prevent unauthorized room access.
- Prevent players from impersonating each other.
- Validate all game actions on the server.
- Protect credentials using environment variables.
- Configure CORS correctly.
- Use parameterized SQL queries or safe ORM operations.
- Apply appropriate Supabase RLS policies.
- Avoid exposing internal server errors.
- Handle expired and invalid authentication tokens.
- Prevent race conditions in simultaneous game actions.

## 8. Code Quality and Project Structure

Maintain a clean and understandable folder structure.

Separate:
- Frontend assets.
- Java source code.
- Configuration files.
- Database scripts.
- API-related components.
- Game engine logic.
- Tests.

Use meaningful class, method, variable and file names.

Write reusable components and avoid unnecessary code duplication.

Include comments where they genuinely improve understanding.

Do not put the entire application into one Java class or one JavaScript file.

## 9. Development Rules

Follow these rules strictly:

1. Work according to the PDF roadmap.
2. Complete one milestone before starting the next.
3. Explain the purpose of each major implementation.
4. Identify the files that will be created or modified.
5. Explain significant architectural decisions before changing the architecture.
6. Do not introduce unnecessary frameworks or dependencies.
7. Do not implement advanced features before the MVP is stable.
8. Do not use mock data as a substitute for actual backend integration.
9. Do not claim functionality is complete without testing it.
10. Preserve existing working functionality.
11. Keep the implementation understandable for a Semester 3 B.Tech student.
12. Update documentation only when explicitly required.

## 10. Final Quality Expectations

The completed NEXUS platform should have:

- A polished and responsive frontend.
- A properly structured Java backend.
- A working Supabase/PostgreSQL integration.
- Secure authentication.
- Functional room creation and joining.
- Real-time two-player multiplayer gameplay.
- Server-side game validation.
- Reliable match completion.
- Database persistence.
- Proper error handling.
- Meaningful test coverage.
- Clear Java OOP implementation.

The final application must be a genuinely functional multiplayer gaming platform, not merely a frontend prototype.

## 11. How to Begin

Before implementation, perform these tasks in order:

1. Locate and read the complete NEXUS Project Blueprint PDF.
2. Summarize the specified architecture and technology stack.
3. Identify the database schema and required entities.
4. Present the proposed frontend and backend folder structure.
5. Explain the Supabase integration approach.
6. Break development into implementation milestones.
7. Identify any missing information or architectural conflicts.
8. Begin Milestone 1 only after the analysis is complete.

**Final Instruction:**

Build NEXUS carefully, incrementally and professionally. The original PDF blueprint takes priority over assumptions or personal architectural preferences.

Focus on functionality, security, maintainability, clean UI/UX and a strong demonstration of Java OOP concepts.

The goal is to build a project that is impressive as a real-world application while remaining understandable and defensible during a Semester 3 practical examination and viva.