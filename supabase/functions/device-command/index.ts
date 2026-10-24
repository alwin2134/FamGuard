import { serve } from 'https://deno.land/std@0.177.0/http/server.ts';
import { corsHeaders } from '../_shared/cors.ts';
import { authenticateGuardian, getServiceClient } from '../_shared/authHelper.ts';

serve(async (req: Request) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  try {
    const { user } = await authenticateGuardian(req);
    const { device_id, action, payload = {} } = await req.json();

    if (!device_id || !action || !['refresh_location', 'ring'].includes(action)) {
      return new Response(JSON.stringify({ error: 'Valid device_id and supported action (refresh_location, ring) required' }), {
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
