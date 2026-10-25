import { corsHeaders } from '../_shared/cors.ts';
import { authenticateGuardian, getServiceClient } from '../_shared/authHelper.ts';

Deno.serve(async (req: Request) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  try {
    const { user } = await authenticateGuardian(req);
    const { device_id, action, payload = {} } = await req.json();

    const allowedActions = ['refresh_location', 'ring', 'lock_app', 'unlock_app', 'sync_activity', 'stop_location'];
    if (!device_id || !action || !allowedActions.includes(action)) {
      return new Response(JSON.stringify({ error: `Valid device_id and supported action (${allowedActions.join(', ')}) required` }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    const admin = getServiceClient();

    // Verify guardian authority
    const { data: device, error: devErr } = await admin
      .from('devices')
      .select('id, family_id, name, status, device_consents(remote_commands_enabled)')
      .eq('id', device_id)
      .single();

    if (devErr || !device || device.status !== 'active') {
      return new Response(JSON.stringify({ error: 'Device not found or inactive' }), {
        status: 404,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    const { data: member } = await admin
      .from('family_members')
      .select('role')
      .eq('family_id', device.family_id)
      .eq('user_id', user.id)
      .single();

    if (!member || member.role !== 'guardian') {
      return new Response(JSON.stringify({ error: 'Only guardians can dispatch remote device commands' }), {
        status: 403,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    // Verify remote commands consent
    const consent = Array.isArray(device.device_consents) ? device.device_consents[0] : device.device_consents;
    if (!consent?.remote_commands_enabled) {
      return new Response(JSON.stringify({
        error: 'Remote device commands are disabled by target device user',
        code: 'COMMAND_CONSENT_REVOKED'
      }), {
        status: 403,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    // Expiry: 5 minutes default
    const expiresAt = new Date(Date.now() + 5 * 60 * 1000).toISOString();

    const { data: command, error: cmdErr } = await admin
      .from('device_commands')
      .insert({
        device_id,
        requested_by: user.id,
        action,
        payload_json: payload,
        status: 'pending',
        expires_at: expiresAt
      })
      .select('*')
      .single();

    if (cmdErr || !command) throw cmdErr;

    // Update locked_apps list on devices table for immediate guardian dashboard sync
    try {
      if (action === 'lock_app' && payload.package_name) {
        const { data: dev } = await admin.from('devices').select('locked_apps').eq('id', device_id).single();
        const currentLocked: string[] = Array.isArray(dev?.locked_apps) ? dev.locked_apps : [];
        if (!currentLocked.includes(payload.package_name)) {
          await admin.from('devices').update({ locked_apps: [...currentLocked, payload.package_name] }).eq('id', device_id);
        }
      } else if (action === 'unlock_app') {
        if (payload.package_name === 'all') {
          await admin.from('devices').update({ locked_apps: [] }).eq('id', device_id);
        } else if (payload.package_name) {
          const { data: dev } = await admin.from('devices').select('locked_apps').eq('id', device_id).single();
          const currentLocked: string[] = Array.isArray(dev?.locked_apps) ? dev.locked_apps : [];
          await admin.from('devices').update({ locked_apps: currentLocked.filter((p: string) => p !== payload.package_name) }).eq('id', device_id);
        }
      }
    } catch (_ignore) {
      // Best-effort cache update
    }

    // Audit event
    await admin.from('audit_events').insert({
      family_id: device.family_id,
      actor_id: user.id,
      actor_type: 'guardian',
      event_name: 'device_command_dispatched',
      details: { command_id: command.id, device_id, action }
    });

    return new Response(JSON.stringify(command), {
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
