import { corsHeaders } from '../_shared/cors.ts';
import { authenticateGuardian, authenticateDevice, getServiceClient } from '../_shared/authHelper.ts';

Deno.serve(async (req: Request) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  try {
    const { device_id, device_secret, reason } = await req.json();

    if (!device_id) {
      return new Response(JSON.stringify({ error: 'Missing device_id' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    const admin = getServiceClient();

    let actorType = 'guardian';
    let actorId = '';

    if (device_secret) {
      await authenticateDevice(device_id, device_secret);
      actorType = 'device';
      actorId = device_id;
    } else {
      const { user } = await authenticateGuardian(req);
      actorId = user.id;

      // Verify guardian authority over device
      const { data: dev } = await admin.from('devices').select('family_id').eq('id', device_id).single();
      if (!dev) {
        return new Response(JSON.stringify({ error: 'Device not found' }), {
          status: 404,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        });
      }
      const { data: member } = await admin
        .from('family_members')
        .select('role')
        .eq('family_id', dev.family_id)
        .eq('user_id', user.id)
        .single();

      if (!member || member.role !== 'guardian') {
        return new Response(JSON.stringify({ error: 'Unauthorized guardian' }), {
          status: 403,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        });
      }
    }

    // 1. Mark device revoked
    await admin
      .from('devices')
      .update({
        status: 'revoked',
        is_online: false,
        updated_at: new Date().toISOString()
      })
      .eq('id', device_id);

    // 2. Disable all consents
    await admin
      .from('device_consents')
      .update({
        location_sharing_enabled: false,
        app_activity_enabled: false,
        remote_commands_enabled: false,
        updated_at: new Date().toISOString()
      })
      .eq('device_id', device_id);

    // 3. Stop active sessions
    await admin
      .from('location_sessions')
      .update({
        status: 'stopped',
        stopped_at: new Date().toISOString(),
        failure_reason: 'Device revoked: ' + (reason || 'No reason specified')
      })
      .eq('device_id', device_id)
      .in('status', ['requested', 'authorized', 'starting', 'active']);

    // 4. Cancel pending commands
    await admin
      .from('device_commands')
      .update({
        status: 'cancelled',
        failure_reason: 'Device was revoked'
      })
      .eq('device_id', device_id)
      .eq('status', 'pending');

    // 5. Audit event
    await admin.from('audit_events').insert({
      actor_id: actorId,
      actor_type: actorType,
      event_name: 'device_revoked',
      details: { device_id, actor_type: actorType, reason }
    });

    return new Response(JSON.stringify({
      success: true,
      device_id,
      status: 'revoked'
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
