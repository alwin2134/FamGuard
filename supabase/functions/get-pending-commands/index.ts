import { corsHeaders } from '../_shared/cors.ts';
import { authenticateDevice } from '../_shared/authHelper.ts';

Deno.serve(async (req: Request) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  try {
    const { device_id, device_secret } = await req.json();

    if (!device_id || !device_secret) {
      return new Response(JSON.stringify({ error: 'Missing device_id or device_secret' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    const { device, adminClient } = await authenticateDevice(device_id, device_secret);

    // Fetch pending, non-expired commands for this device
    const { data: commands, error } = await adminClient
      .from('device_commands')
      .select('*')
      .eq('device_id', device_id)
      .eq('status', 'pending')
      .gt('expires_at', new Date().toISOString())
      .order('created_at', { ascending: true });

    if (error) throw error;

    return new Response(JSON.stringify(commands || []), {
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
