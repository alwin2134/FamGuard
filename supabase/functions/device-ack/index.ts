import { corsHeaders } from '../_shared/cors.ts';
import { authenticateDevice } from '../_shared/authHelper.ts';

Deno.serve(async (req: Request) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  try {
    const { command_id, device_id, device_secret, status, failure_reason } = await req.json();

    if (!command_id || !device_id || !device_secret || !status) {
      return new Response(JSON.stringify({ error: 'Missing command_id, device_id, device_secret, or status' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    const validStatuses = ['delivered', 'executing', 'succeeded', 'failed', 'blocked'];
    if (!validStatuses.includes(status)) {
      return new Response(JSON.stringify({ error: `Invalid status: ${status}` }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    const { device, adminClient } = await authenticateDevice(device_id, device_secret);

    // Verify command
    const { data: command, error: cmdErr } = await adminClient
      .from('device_commands')
      .select('*')
      .eq('id', command_id)
      .eq('device_id', device_id)
      .single();

    if (cmdErr || !command) {
      return new Response(JSON.stringify({ error: 'Command not found' }), {
        status: 404,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    const updatePayload: any = {
      status,
      failure_reason: failure_reason ?? null
    };

    if (['succeeded', 'failed', 'blocked'].includes(status)) {
      updatePayload.executed_at = new Date().toISOString();
    }

    const { data: updated, error: updateErr } = await adminClient
      .from('device_commands')
      .update(updatePayload)
      .eq('id', command_id)
      .select('*')
      .single();

    if (updateErr) throw updateErr;

    // Audit event
    await adminClient.from('audit_events').insert({
      family_id: device.family_id,
      actor_id: device_id,
      actor_type: 'device',
      event_name: 'device_command_acked',
      details: { command_id, status, failure_reason }
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
