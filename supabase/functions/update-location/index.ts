import { serve } from 'https://deno.land/std@0.177.0/http/server.ts';
import { corsHeaders } from '../_shared/cors.ts';
import { authenticateDevice } from '../_shared/authHelper.ts';

serve(async (req: Request) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  try {
    const {
      device_id,
      device_secret,
      session_id,
      latitude,
      longitude,
      accuracy_m,
      recorded_at
    } = await req.json();

    if (!device_id || !device_secret || !session_id || latitude === undefined || longitude === undefined) {
      return new Response(JSON.stringify({ error: 'Missing required location parameters' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    // Coordinate validation
    const lat = Number(latitude);
    const lon = Number(longitude);
    const acc = Number(accuracy_m);

    if (isNaN(lat) || lat < -90 || lat > 90 || isNaN(lon) || lon < -180 || lon > 180 || isNaN(acc) || acc < 0) {
      return new Response(JSON.stringify({ error: 'Invalid coordinate or accuracy values' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    // Authenticate device
    const { device, adminClient } = await authenticateDevice(device_id, device_secret);

    // Verify consent
    const { data: consent } = await adminClient
      .from('device_consents')
      .select('location_sharing_enabled')
      .eq('device_id', device_id)
      .single();

    if (!consent?.location_sharing_enabled) {
      return new Response(JSON.stringify({
        error: 'Location sharing is not enabled on this device',
        code: 'CONSENT_REVOKED'
      }), {
        status: 403,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    // Validate active session
    const { data: session, error: sessErr } = await adminClient
      .from('location_sessions')
      .select('*')
      .eq('id', session_id)
      .eq('device_id', device_id)
      .single();

    if (sessErr || !session) {
      return new Response(JSON.stringify({ error: 'Invalid or unknown location session' }), {
        status: 404,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    // Check expiry
    const nowMs = Date.now();
    if (new Date(session.expires_at).getTime() <= nowMs) {
      await adminClient
        .from('location_sessions')
        .update({ status: 'expired' })
        .eq('id', session_id);

      return new Response(JSON.stringify({
        error: 'Location session has expired',
        code: 'SESSION_EXPIRED',
        session_status: 'expired'
      }), {
        status: 410,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    if (['stopped', 'expired', 'failed', 'blocked'].includes(session.status)) {
      return new Response(JSON.stringify({
        error: `Location session is ${session.status}`,
        code: 'SESSION_TERMINATED',
        session_status: session.status
      }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    // If starting or authorized, mark as active
    let currentStatus = session.status;
    if (session.status === 'authorized' || session.status === 'starting' || session.status === 'requested') {
      await adminClient
        .from('location_sessions')
        .update({ status: 'active', started_at: new Date().toISOString() })
        .eq('id', session_id);
      currentStatus = 'active';
    }

    // Insert location fix
    const { error: insertErr } = await adminClient
      .from('location_updates')
      .insert({
        device_id,
        session_id,
        latitude: lat,
        longitude: lon,
        accuracy_m: acc,
        recorded_at: recorded_at || new Date().toISOString()
      });

    if (insertErr) throw insertErr;

    // Update device heartbeat
    await adminClient
      .from('devices')
      .update({
        last_seen_at: new Date().toISOString(),
        is_online: true
      })
      .eq('id', device_id);

    return new Response(JSON.stringify({
      success: true,
      session_status: currentStatus
    }), {
      status: 200,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' }
    });

  } catch (err: any) {
    return new Response(JSON.stringify({ error: err.message || 'Internal Server Error' }), {
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' }
    });
  }
});
