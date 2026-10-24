-- ============================================================================
-- FamGuard Initial Schema Migration
-- Includes: Core tables, check constraints, RLS policies, indexes, and 7-day retention
-- ============================================================================

-- Extensions
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. FAMILIES
CREATE TABLE IF NOT EXISTS public.families (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 2. FAMILY MEMBERS
CREATE TABLE IF NOT EXISTS public.family_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES public.families(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    role TEXT NOT NULL CHECK (role IN ('guardian', 'ward')),
    display_name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (family_id, user_id)
);

-- 3. DEVICES
CREATE TABLE IF NOT EXISTS public.devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES public.families(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    model TEXT,
    os_version TEXT,
    status TEXT NOT NULL CHECK (status IN ('active', 'revoked', 'pending_pairing')) DEFAULT 'active',
    device_secret_hash TEXT NOT NULL,
    enrolled_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ,
    battery_level INT CHECK (battery_level IS NULL OR (battery_level >= 0 AND battery_level <= 100)),
    is_online BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 4. DEVICE CONSENTS
CREATE TABLE IF NOT EXISTS public.device_consents (
    device_id UUID PRIMARY KEY REFERENCES public.devices(id) ON DELETE CASCADE,
    location_sharing_enabled BOOLEAN NOT NULL DEFAULT false,
    app_activity_enabled BOOLEAN NOT NULL DEFAULT false,
    remote_commands_enabled BOOLEAN NOT NULL DEFAULT false,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 5. PAIRING TOKENS (Single-use, short-lived)
CREATE TABLE IF NOT EXISTS public.pairing_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES public.families(id) ON DELETE CASCADE,
    created_by UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE,
    target_name TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('pending', 'consumed', 'expired', 'revoked')) DEFAULT 'pending',
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    consumed_by_device_id UUID REFERENCES public.devices(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 6. LOCATION SESSIONS (On-demand, time-limited live location)
CREATE TABLE IF NOT EXISTS public.location_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    requested_by UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    status TEXT NOT NULL CHECK (status IN ('requested', 'authorized', 'starting', 'active', 'stop_requested', 'stopped', 'expired', 'blocked', 'failed')) DEFAULT 'requested',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    authorized_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    stopped_at TIMESTAMPTZ,
    failure_reason TEXT
);

-- 7. LOCATION UPDATES
CREATE TABLE IF NOT EXISTS public.location_updates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    session_id UUID NOT NULL REFERENCES public.location_sessions(id) ON DELETE CASCADE,
    latitude DOUBLE PRECISION NOT NULL CHECK (latitude >= -90.0 AND latitude <= 90.0),
    longitude DOUBLE PRECISION NOT NULL CHECK (longitude >= -180.0 AND longitude <= 180.0),
    accuracy_m REAL NOT NULL CHECK (accuracy_m >= 0),
    recorded_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 8. DEVICE APP EVENTS (Foreground/Background app activity)
CREATE TABLE IF NOT EXISTS public.device_app_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    package_name TEXT NOT NULL,
    app_label TEXT,
    event_type TEXT NOT NULL CHECK (event_type IN ('foreground', 'background', 'usage_summary')),
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    duration_seconds BIGINT CHECK (duration_seconds IS NULL OR duration_seconds >= 0),
    source TEXT NOT NULL DEFAULT 'android_usage_events' CHECK (source IN ('android_usage_events', 'usage_summary')),
    CONSTRAINT uq_device_event UNIQUE (device_id, package_name, event_type, occurred_at)
);

-- 9. DEVICE COMMANDS (refresh-location, ring)
CREATE TABLE IF NOT EXISTS public.device_commands (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    requested_by UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    action TEXT NOT NULL CHECK (action IN ('refresh_location', 'ring')),
    payload_json JSONB,
    status TEXT NOT NULL CHECK (status IN ('pending', 'delivered', 'executing', 'succeeded', 'failed', 'expired', 'cancelled', 'blocked')) DEFAULT 'pending',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    executed_at TIMESTAMPTZ,
    failure_reason TEXT
);

-- 10. AUDIT EVENTS
CREATE TABLE IF NOT EXISTS public.audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID REFERENCES public.families(id) ON DELETE SET NULL,
    actor_id UUID,
    actor_type TEXT NOT NULL CHECK (actor_type IN ('guardian', 'device', 'system')),
    event_name TEXT NOT NULL,
    details JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================================
-- INDEXES FOR PERFORMANCE AND RETENTION
-- ============================================================================
CREATE INDEX IF NOT EXISTS idx_family_members_user ON public.family_members(user_id);
CREATE INDEX IF NOT EXISTS idx_family_members_family ON public.family_members(family_id);
CREATE INDEX IF NOT EXISTS idx_devices_family ON public.devices(family_id);
CREATE INDEX IF NOT EXISTS idx_pairing_tokens_hash ON public.pairing_tokens(token_hash);
CREATE INDEX IF NOT EXISTS idx_location_sessions_device ON public.location_sessions(device_id, status);
CREATE INDEX IF NOT EXISTS idx_location_updates_session ON public.location_updates(session_id, recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_location_updates_device ON public.location_updates(device_id, recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_app_events_timeline ON public.device_app_events(device_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_app_events_retention ON public.device_app_events(occurred_at);
CREATE INDEX IF NOT EXISTS idx_device_commands_pending ON public.device_commands(device_id, status) WHERE status = 'pending';

-- ============================================================================
-- ROW LEVEL SECURITY (RLS)
-- ============================================================================
ALTER TABLE public.families ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.family_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.device_consents ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.pairing_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.location_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.location_updates ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.device_app_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.device_commands ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_events ENABLE ROW LEVEL SECURITY;

-- Helper function: Check if user belongs to family
CREATE OR REPLACE FUNCTION public.is_family_member(f_id UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.family_members
        WHERE family_id = f_id AND user_id = auth.uid()
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Helper function: Check if user is guardian of family
CREATE OR REPLACE FUNCTION public.is_family_guardian(f_id UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.family_members
        WHERE family_id = f_id AND user_id = auth.uid() AND role = 'guardian'
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Policies for families
CREATE POLICY "Users can view their families"
    ON public.families FOR SELECT
    USING (public.is_family_member(id));

CREATE POLICY "Guardians can update their family"
    ON public.families FOR UPDATE
    USING (public.is_family_guardian(id));

-- Policies for family_members
CREATE POLICY "Users can view members of their families"
    ON public.family_members FOR SELECT
    USING (public.is_family_member(family_id));

CREATE POLICY "Guardians can manage family members"
    ON public.family_members FOR ALL
    USING (public.is_family_guardian(family_id));

-- Policies for devices
CREATE POLICY "Family members can view family devices"
    ON public.devices FOR SELECT
    USING (public.is_family_member(family_id));

CREATE POLICY "Guardians can update device details"
    ON public.devices FOR UPDATE
    USING (public.is_family_guardian(family_id));

-- Policies for device_consents
CREATE POLICY "Family members can view device consents"
    ON public.device_consents FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM public.devices d
            WHERE d.id = device_id AND public.is_family_member(d.family_id)
        )
    );

-- Policies for pairing_tokens
CREATE POLICY "Guardians can create and view pairing tokens"
    ON public.pairing_tokens FOR ALL
    USING (public.is_family_guardian(family_id));

-- Policies for location_sessions
CREATE POLICY "Guardians can view and create location sessions"
    ON public.location_sessions FOR ALL
    USING (
        EXISTS (
            SELECT 1 FROM public.devices d
            WHERE d.id = device_id AND public.is_family_guardian(d.family_id)
        )
    );

-- Policies for location_updates
CREATE POLICY "Family members can view location updates"
    ON public.location_updates FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM public.devices d
            JOIN public.device_consents dc ON dc.device_id = d.id
            WHERE d.id = location_updates.device_id
              AND public.is_family_member(d.family_id)
              AND dc.location_sharing_enabled = true
        )
    );

-- Policies for device_app_events
CREATE POLICY "Guardians can view permitted app events"
    ON public.device_app_events FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM public.devices d
            JOIN public.device_consents dc ON dc.device_id = d.id
            WHERE d.id = device_app_events.device_id
              AND public.is_family_guardian(d.family_id)
              AND dc.app_activity_enabled = true
        )
    );

-- Policies for device_commands
CREATE POLICY "Guardians can create and view commands"
    ON public.device_commands FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM public.devices d
            WHERE d.id = device_id AND public.is_family_guardian(d.family_id)
        )
    );

CREATE POLICY "Guardians can insert commands"
    ON public.device_commands FOR INSERT
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.devices d
            JOIN public.device_consents dc ON dc.device_id = d.id
            WHERE d.id = device_id 
              AND public.is_family_guardian(d.family_id)
              AND dc.remote_commands_enabled = true
        )
    );

-- Policies for audit_events
CREATE POLICY "Guardians can view family audit events"
    ON public.audit_events FOR SELECT
    USING (public.is_family_guardian(family_id));

-- ============================================================================
-- 7-DAY RETENTION CLEANUP FUNCTION
-- ============================================================================
CREATE OR REPLACE FUNCTION public.purge_expired_telemetry()
RETURNS TABLE (
    purged_app_events BIGINT,
    purged_location_updates BIGINT,
    purged_expired_sessions BIGINT
) AS $$
DECLARE
    v_purged_app_events BIGINT := 0;
    v_purged_locations BIGINT := 0;
    v_purged_sessions BIGINT := 0;
BEGIN
    -- 1. Delete app events older than 7 days
    WITH deleted_events AS (
        DELETE FROM public.device_app_events
        WHERE occurred_at < (now() - INTERVAL '7 days')
        RETURNING id
    )
    SELECT count(*) INTO v_purged_app_events FROM deleted_events;

    -- 2. Delete location updates for sessions completed/expired over 7 days ago
    WITH deleted_locs AS (
        DELETE FROM public.location_updates
        WHERE received_at < (now() - INTERVAL '7 days')
        RETURNING id
    )
    SELECT count(*) INTO v_purged_locations FROM deleted_locs;

    -- 3. Delete stopped or expired location sessions older than 7 days
    WITH deleted_sessions AS (
        DELETE FROM public.location_sessions
        WHERE status IN ('stopped', 'expired', 'failed', 'blocked')
          AND expires_at < (now() - INTERVAL '7 days')
        RETURNING id
    )
    SELECT count(*) INTO v_purged_sessions FROM deleted_sessions;

    RETURN QUERY SELECT v_purged_app_events, v_purged_locations, v_purged_sessions;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
