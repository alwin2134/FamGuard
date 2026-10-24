import { serve } from 'https://deno.land/std@0.177.0/http/server.ts';
import { corsHeaders } from '../_shared/cors.ts';
import { getServiceClient, hashSecret } from '../_shared/authHelper.ts';

serve(async (req: Request) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  try {
    const { token, device_name, device_model, os_version, consents } = await req.json();

    if (!token || !device_name) {
      return new Response(JSON.stringify({ error: 'Missing token or device_name' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    const admin = getServiceClient();
    const tokenHash = await hashSecret(token);

    // Atomically find valid pending token
    const { data: pairingToken, error: tokenErr } = await admin
      .from('pairing_tokens')
      .select('id, family_id, created_by, target_name, expires_at, status, families(name)')
      .eq('token_hash', tokenHash)
      .eq('status', 'pending')
      .single();

    if (tokenErr || !pairingToken) {
      return new Response(JSON.stringify({ error: 'Invalid or already used pairing token' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    // Check expiry
    if (new Date(pairingToken.expires_at).getTime() < Date.now()) {
      await admin.from('pairing_tokens').update({ status: 'expired' }).eq('id', pairingToken.id);
      return new Response(JSON.stringify({ error: 'Pairing token has expired' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    // Generate random device secret
    const secretBytes = new Uint8Array(32);
    crypto.getRandomValues(secretBytes);
    const deviceSecret = Array.from(secretBytes).map(b => b.toString(16).padStart(2, '0')).join('');
    const secretHash = await hashSecret(deviceSecret);

    // Insert device record
    const { data: newDevice, error: deviceErr } = await admin
      .from('devices')
      .insert({
        family_id: pairingToken.family_id,
        name: device_name,
        model: device_model ?? 'Android Device',
        os_version: os_version ?? 'Android',
        status: 'active',
        device_secret_hash: secretHash,
        is_online: true,
        last_seen_at: new Date().toISOString()
      })
      .select('id')
      .single();

    if (deviceErr || !newDevice) {
      throw deviceErr || new Error('Failed to create device');
    }

    // Insert device consents
    const locConsent = Boolean(consents?.location_sharing);
    const actConsent = Boolean(consents?.app_activity);
    const cmdConsent = Boolean(consents?.remote_commands);

    await admin.from('device_consents').insert({
      device_id: newDevice.id,
      location_sharing_enabled: locConsent,
      app_activity_enabled: actConsent,
      remote_commands_enabled: cmdConsent,
      updated_at: new Date().toISOString()
    });

    // Mark token as consumed atomically
    await admin
      .from('pairing_tokens')
      .update({
        status: 'consumed',
        consumed_at: new Date().toISOString(),
        consumed_by_device_id: newDevice.id
      })
      .eq('id', pairingToken.id);

    // Audit event
    await admin.from('audit_events').insert({
      family_id: pairingToken.family_id,
      actor_id: newDevice.id,
      actor_type: 'device',
      event_name: 'device_paired',
      details: {
        device_name,
        device_model,
        consents: { location_sharing: locConsent, app_activity: actConsent, remote_commands: cmdConsent }
      }
    });

    return new Response(JSON.stringify({
      device_id: newDevice.id,
      device_secret: deviceSecret,
      family_id: pairingToken.family_id,
      family_name: (pairingToken.families as any)?.name ?? 'FamGuard Family'
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
