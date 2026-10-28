-- ============================================================================
-- Migration: Add WebRTC, Screen Mirroring, and Voice Calling Support
-- ============================================================================

-- 1. Update device_commands action check constraint to support WebRTC and remote control actions
ALTER TABLE public.device_commands DROP CONSTRAINT IF EXISTS device_commands_action_check;
ALTER TABLE public.device_commands ADD CONSTRAINT device_commands_action_check CHECK (
    action IN (
        'refresh_location',
        'ring',
        'lock_app',
        'unlock_app',
        'sync_activity',
        'stop_location',
        'start_screen_mirror',
        'stop_screen_mirror',
        'start_voice_call',
        'end_voice_call',
        'webrtc_signal'
    )
);

-- 2. Create webrtc_signals table for bidirectional P2P signaling (SDP offer/answer, ICE candidates)
CREATE TABLE IF NOT EXISTS public.webrtc_signals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    sender_type TEXT NOT NULL CHECK (sender_type IN ('controller', 'agent')),
    stream_type TEXT NOT NULL CHECK (stream_type IN ('screen_mirror', 'voice_call', 'location')),
    signal_type TEXT NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Indexes for ultra-fast polling and realtime subscription
CREATE INDEX IF NOT EXISTS idx_webrtc_signals_lookup 
    ON public.webrtc_signals(device_id, sender_type, stream_type, created_at DESC);

-- Enable RLS
ALTER TABLE public.webrtc_signals ENABLE ROW LEVEL SECURITY;

-- Allow authenticated guardians and paired agents to read/insert signals
CREATE POLICY "Allow authenticated read/write on webrtc_signals"
    ON public.webrtc_signals
    FOR ALL
    TO authenticated
    USING (true)
    WITH CHECK (true);

CREATE POLICY "Allow anon read/write on webrtc_signals"
    ON public.webrtc_signals
    FOR ALL
    TO anon
    USING (true)
    WITH CHECK (true);

-- Enable Realtime publication for webrtc_signals if publication exists
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.webrtc_signals;
    END IF;
END $$;
