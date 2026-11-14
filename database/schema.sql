-- =============================================================================
-- NEXUS — Real-Time Multiplayer Gaming Hub
-- Supabase / PostgreSQL Database Schema & Row Level Security (RLS) Policies
-- Supports: TIC_TAC_TOE (3x3), CONNECT_FOUR (6x7), GOMOKU (9x9), QUAD_TAC_TOE (4x4)
-- =============================================================================
-- Paste and run this script in: Supabase Dashboard -> SQL Editor -> New Query

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- -----------------------------------------------------------------------------
-- 1. PROFILES TABLE (Extends Supabase auth.users)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL UNIQUE,
    username VARCHAR(32) NOT NULL UNIQUE,
    wins INTEGER NOT NULL DEFAULT 0 CHECK (wins >= 0),
    losses INTEGER NOT NULL DEFAULT 0 CHECK (losses >= 0),
    draws INTEGER NOT NULL DEFAULT 0 CHECK (draws >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_profiles_username ON public.profiles(username);
CREATE INDEX IF NOT EXISTS idx_profiles_wins ON public.profiles(wins DESC);

-- -----------------------------------------------------------------------------
-- 2. ROOMS TABLE (Multiplayer Game Rooms for All 4 Game Modes)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.rooms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_code VARCHAR(12) NOT NULL UNIQUE,
    room_name VARCHAR(80) NOT NULL,
    game_type VARCHAR(32) NOT NULL DEFAULT 'TIC_TAC_TOE'
        CHECK (game_type IN ('TIC_TAC_TOE', 'CONNECT_FOUR', 'GOMOKU', 'QUAD_TAC_TOE')),
    host_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    guest_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'WAITING'
        CHECK (status IN ('WAITING', 'IN_PROGRESS', 'COMPLETED', 'ABANDONED')),
    max_players INTEGER NOT NULL DEFAULT 2 CHECK (max_players = 2),
    current_match_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Upgrade existing rooms table constraint if created from an earlier version
ALTER TABLE public.rooms DROP CONSTRAINT IF EXISTS rooms_game_type_check;
ALTER TABLE public.rooms ADD CONSTRAINT rooms_game_type_check
    CHECK (game_type IN ('TIC_TAC_TOE', 'CONNECT_FOUR', 'GOMOKU', 'QUAD_TAC_TOE'));

CREATE INDEX IF NOT EXISTS idx_rooms_room_code ON public.rooms(room_code);
CREATE INDEX IF NOT EXISTS idx_rooms_status ON public.rooms(status);
CREATE INDEX IF NOT EXISTS idx_rooms_host_id ON public.rooms(host_id);

-- -----------------------------------------------------------------------------
-- 3. ROOM PARTICIPANTS TABLE (Tracks Player Membership & States in Rooms)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.room_participants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES public.rooms(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    player_symbol CHAR(1) NOT NULL CHECK (player_symbol IN ('X', 'O')),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    left_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (room_id, user_id),
    UNIQUE (room_id, player_symbol)
);

CREATE INDEX IF NOT EXISTS idx_room_participants_room_id ON public.room_participants(room_id);
CREATE INDEX IF NOT EXISTS idx_room_participants_user_id ON public.room_participants(user_id);

-- -----------------------------------------------------------------------------
-- 4. MATCHES TABLE (Authoritative Match Records & Outcomes up to 9x9 Boards)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.matches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID REFERENCES public.rooms(id) ON DELETE SET NULL,
    room_code VARCHAR(12) NOT NULL,
    game_type VARCHAR(32) NOT NULL DEFAULT 'TIC_TAC_TOE'
        CHECK (game_type IN ('TIC_TAC_TOE', 'CONNECT_FOUR', 'GOMOKU', 'QUAD_TAC_TOE')),
    player_x_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    player_x_name VARCHAR(32) NOT NULL,
    player_o_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    player_o_name VARCHAR(32) NOT NULL,
    winner_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    outcome VARCHAR(24) NOT NULL DEFAULT 'IN_PROGRESS'
        CHECK (outcome IN ('IN_PROGRESS', 'X_WON', 'O_WON', 'DRAW', 'FORFEIT')),
    final_board VARCHAR(100) NOT NULL DEFAULT '---------',
    move_count INTEGER NOT NULL DEFAULT 0 CHECK (move_count >= 0 AND move_count <= 100),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ended_at TIMESTAMP WITH TIME ZONE
);

-- Upgrade existing matches table columns/constraints if created from an earlier version
ALTER TABLE public.matches ALTER COLUMN final_board TYPE VARCHAR(100);
ALTER TABLE public.matches DROP CONSTRAINT IF EXISTS matches_move_count_check;
ALTER TABLE public.matches ADD CONSTRAINT matches_move_count_check
    CHECK (move_count >= 0 AND move_count <= 100);

CREATE INDEX IF NOT EXISTS idx_matches_room_code ON public.matches(room_code);
CREATE INDEX IF NOT EXISTS idx_matches_player_x ON public.matches(player_x_id);
CREATE INDEX IF NOT EXISTS idx_matches_player_o ON public.matches(player_o_id);
CREATE INDEX IF NOT EXISTS idx_matches_started_at ON public.matches(started_at DESC);

-- -----------------------------------------------------------------------------
-- 5. MATCH MOVES TABLE (Audit Log of Server-Validated Moves for 3x3 to 9x9)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.match_moves (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    match_id UUID NOT NULL REFERENCES public.matches(id) ON DELETE CASCADE,
    player_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    player_symbol CHAR(1) NOT NULL CHECK (player_symbol IN ('X', 'O')),
    row_index INTEGER NOT NULL CHECK (row_index >= 0 AND row_index <= 15),
    col_index INTEGER NOT NULL CHECK (col_index >= 0 AND col_index <= 15),
    move_number INTEGER NOT NULL CHECK (move_number >= 1 AND move_number <= 100),
    board_state_after VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (match_id, move_number),
    UNIQUE (match_id, row_index, col_index)
);

-- Upgrade existing match_moves columns/constraints if created from an earlier version
ALTER TABLE public.match_moves ALTER COLUMN board_state_after TYPE VARCHAR(100);
ALTER TABLE public.match_moves DROP CONSTRAINT IF EXISTS match_moves_row_index_check;
ALTER TABLE public.match_moves ADD CONSTRAINT match_moves_row_index_check
    CHECK (row_index >= 0 AND row_index <= 15);
ALTER TABLE public.match_moves DROP CONSTRAINT IF EXISTS match_moves_col_index_check;
ALTER TABLE public.match_moves ADD CONSTRAINT match_moves_col_index_check
    CHECK (col_index >= 0 AND col_index <= 15);
ALTER TABLE public.match_moves DROP CONSTRAINT IF EXISTS match_moves_move_number_check;
ALTER TABLE public.match_moves ADD CONSTRAINT match_moves_move_number_check
    CHECK (move_number >= 1 AND move_number <= 100);

CREATE INDEX IF NOT EXISTS idx_match_moves_match_id ON public.match_moves(match_id);

-- -----------------------------------------------------------------------------
-- 6. ROOM CHAT MESSAGES TABLE (In-Room Real-Time Chat Log)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.room_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_code VARCHAR(12) NOT NULL,
    sender_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    sender_name VARCHAR(32) NOT NULL,
    sender_symbol VARCHAR(8) NOT NULL DEFAULT 'SYS',
    message TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_room_messages_room_code ON public.room_messages(room_code);

-- -----------------------------------------------------------------------------
-- 7. AUTOMATIC PROFILE CREATION TRIGGER ON SUPABASE AUTH SIGNUP
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS '
DECLARE
    derived_username VARCHAR(32);
BEGIN
    derived_username := COALESCE(
        NEW.raw_user_meta_data->>''username'',
        SPLIT_PART(NEW.email, ''@'', 1)
    );
    INSERT INTO public.profiles (id, email, username)
    VALUES (NEW.id, NEW.email, derived_username)
    ON CONFLICT (id) DO NOTHING;
    RETURN NEW;
END;
';

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- -----------------------------------------------------------------------------
-- 8. ROW LEVEL SECURITY (RLS) POLICIES
-- -----------------------------------------------------------------------------
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.rooms ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.room_participants ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.matches ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.match_moves ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.room_messages ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Profiles are viewable by authenticated users" ON public.profiles;
CREATE POLICY "Profiles are viewable by authenticated users"
    ON public.profiles FOR SELECT
    TO authenticated
    USING (true);

DROP POLICY IF EXISTS "Rooms are viewable by authenticated users" ON public.rooms;
CREATE POLICY "Rooms are viewable by authenticated users"
    ON public.rooms FOR SELECT
    TO authenticated
    USING (true);

DROP POLICY IF EXISTS "Room participants viewable by authenticated users" ON public.room_participants;
CREATE POLICY "Room participants viewable by authenticated users"
    ON public.room_participants FOR SELECT
    TO authenticated
    USING (true);

DROP POLICY IF EXISTS "Matches viewable by authenticated users" ON public.matches;
CREATE POLICY "Matches viewable by authenticated users"
    ON public.matches FOR SELECT
    TO authenticated
    USING (true);

DROP POLICY IF EXISTS "Match moves viewable by authenticated users" ON public.match_moves;
CREATE POLICY "Match moves viewable by authenticated users"
    ON public.match_moves FOR SELECT
    TO authenticated
    USING (true);

DROP POLICY IF EXISTS "Room messages viewable by authenticated users" ON public.room_messages;
CREATE POLICY "Room messages viewable by authenticated users"
    ON public.room_messages FOR SELECT
    TO authenticated
    USING (true);
