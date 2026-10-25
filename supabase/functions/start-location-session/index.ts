import { corsHeaders } from '../_shared/cors.ts';
import { authenticateGuardian, getServiceClient } from '../_shared/authHelper.ts';

Deno.serve(async (req: Request) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  try {
    const { user } = await authenticateGuardian(req);
    const { device_id, duration_minutes = 5 } = await req.json();

    if (!device_id) {
      return new Response(JSON.stringify({ error: 'Missing device_id' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    const admin = getServiceClient();

    // Verify guardian has authority over device's family
    const { data: device, error: devErr } = await admin
      .from('devices')
      .select('id, family_id, name, status, device_consents(location_sharing_enabled)')
      .eq('id', device_id)
      .single();

    if (devErr || !device || device.status !== 'active') {
      return new Response(JSON.stringify({ error: 'Device not found or inactive' }), {
        status: 404,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    const { data: guardianMember, error: gmErr } = await admin
      .from('family_members')
      .select('role')
      .eq('family_id', device.family_id)
      .eq('user_id', user.id)
      .single();

    if (gmErr || !guardianMember || guardianMember.role !== 'guardian') {
      return new Response(JSON.stringify({ error: 'Unauthorized: Guardian role required' }), {
        status: 403,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    // Verify consent
    const consent = Array.isArray(device.device_consents) ? device.device_consents[0] : device.device_consents;
    if (!consent?.location_sharing_enabled) {
      return new Response(JSON.stringify({
        error: 'Location sharing is disabled or not consented by the target user',
        code: 'CONSENT_DENIED'
      }), {
        status: 403,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    // Check for existing active or requested session
    const { data: existingSession } = await admin
      .from('location_sessions')
      .select('*')
      .eq('device_id', device_id)
      .in('status', ['requested', 'authorized', 'starting', 'active'])
      .gt('expires_at', new Date().toISOString())
      .order('created_at', { ascending: false })
      .limit(1)
      .maybeSingle();

    if (existingSession) {
      // Re-dispatch refresh_location command so target device streams immediately
      await admin.from('device_commands').insert({
        device_id,
        requested_by: user.id,
        action: 'refresh_location',
        payload_json: { session_id: existingSession.id, expires_at: existingSession.expires_at },
        status: 'pending',
        expires_at: existingSession.expires_at
      });

      return new Response(JSON.stringify(existingSession), {
        status: 200,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    // WhatsApp-style live location: always active until explicitly closed
    const validMinutes = Number(duration_minutes);
    const expiresAt = (validMinutes && validMinutes > 0)
      ? new Date(Date.now() + validMinutes * 60 * 1000).toISOString()
      : new Date(Date.now() + 1000 * 86400 * 365 * 10).toISOString(); // 10 years indefinite

    const { data: newSession, error: sessErr } = await admin
      .from('location_sessions')
      .insert({
        device_id,
        requested_by: user.id,
        status: 'authorized',
        authorized_at: new Date().toISOString(),
        expires_at: expiresAt
      })
      .select('*')
      .single();

    if (sessErr || !newSession) {
      throw sessErr || new Error('Failed to create location session');
    }

    // Create a device command to signal the target to start location updates
    await admin.from('device_commands').insert({
      device_id,
      requested_by: user.id,
      action: 'refresh_location',
      payload_json: { session_id: newSession.id, expires_at: expiresAt },
      status: 'pending',
      expires_at: expiresAt
    });

    // Record audit event
    await admin.from('audit_events').insert({
      family_id: device.family_id,
      actor_id: user.id,
      actor_type: 'guardian',
      event_name: 'location_session_started',
      details: { device_id, session_id: newSession.id, duration_minutes: validMinutes }
    });

    return new Response(JSON.stringify(newSession), {
      status: 201,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' }
    });

  } catch (err: any) {
    return new Response(JSON.stringify({ error: err.message || 'Internal Server Error' }), {
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' }
    });
  }
});
