import { corsHeaders } from '../_shared/cors.ts';
import { authenticateGuardian, authenticateDevice, getServiceClient } from '../_shared/authHelper.ts';

Deno.serve(async (req: Request) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  try {
    const { session_id, device_id, device_secret, reason } = await req.json();

    if (!session_id) {
      return new Response(JSON.stringify({ error: 'Missing session_id' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    const admin = getServiceClient();

    // Verify session exists
    const { data: session, error: sessErr } = await admin
      .from('location_sessions')
      .select('id, device_id, status, expires_at')
      .eq('id', session_id)
      .single();

    if (sessErr || !session) {
      return new Response(JSON.stringify({ error: 'Session not found' }), {
        status: 404,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    // Caller can either be Guardian (via JWT) or Device (via device_secret)
    let actorType = 'guardian';
    let actorId = '';

    if (device_id && device_secret) {
      await authenticateDevice(device_id, device_secret);
      if (session.device_id !== device_id) {
        throw new Error('Device mismatch for session');
      }
      actorType = 'device';
      actorId = device_id;
    } else {
      const { user } = await authenticateGuardian(req);
      actorId = user.id;
    }

    const { data: updated, error: updateErr } = await admin
      .from('location_sessions')
      .update({
        status: 'stopped',
        stopped_at: new Date().toISOString(),
        failure_reason: reason ?? (actorType === 'device' ? 'Stopped by target user' : 'Stopped by guardian')
      })
      .eq('id', session_id)
      .select('*')
      .single();

    if (updateErr) throw updateErr;

    // Dispatch stop_location command to the target device
    await admin.from('device_commands').insert({
      device_id: session.device_id,
      requested_by: actorId,
      action: 'stop_location',
      payload_json: { session_id },
      status: 'pending',
      expires_at: new Date(Date.now() + 5 * 60 * 1000).toISOString()
    });

    // Record audit event
    await admin.from('audit_events').insert({
      actor_id: actorId,
      actor_type: actorType,
      event_name: 'location_session_stopped',
      details: { session_id, stopped_by: actorType, reason }
    });

    return new Response(JSON.stringify(updated), {
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
