import { serve } from 'https://deno.land/std@0.177.0/http/server.ts';
import { corsHeaders } from '../_shared/cors.ts';
import { authenticateDevice } from '../_shared/authHelper.ts';

serve(async (req: Request) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  try {
    const { device_id, device_secret, events } = await req.json();

    if (!device_id || !device_secret || !Array.isArray(events)) {
      return new Response(JSON.stringify({ error: 'Missing device_id, device_secret, or events array' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    if (events.length === 0) {
      return new Response(JSON.stringify({ ingested: 0, skipped_duplicates: 0 }), {
        status: 200,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    // Authenticate device
    const { device, adminClient } = await authenticateDevice(device_id, device_secret);

    // Verify app-activity consent
    const { data: consent } = await adminClient
      .from('device_consents')
      .select('app_activity_enabled')
      .eq('device_id', device_id)
      .single();

    if (!consent?.app_activity_enabled) {
      return new Response(JSON.stringify({
        error: 'App activity monitoring is not consented on this device',
        code: 'CONSENT_DENIED'
      }), {
        status: 403,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    // Format events for insertion
    const records = events.map((e: any) => ({
      device_id,
      package_name: e.package_name,
      app_label: e.app_label ?? null,
      event_type: e.event_type,
      occurred_at: e.occurred_at,
      received_at: new Date().toISOString(),
      duration_seconds: e.duration_seconds ?? null,
      source: e.source ?? 'android_usage_events'
    }));

    // Perform idempotent insertion (ignore duplicates based on unique constraint)
    const { data: inserted, error: insertErr } = await adminClient
      .from('device_app_events')
      .upsert(records, {
        onConflict: 'device_id,package_name,event_type,occurred_at',
        ignoreDuplicates: true
      })
      .select('id');

    if (insertErr) throw insertErr;

    const insertedCount = inserted ? inserted.length : 0;
    const skipped = records.length - insertedCount;

    // Heartbeat update
    await adminClient
      .from('devices')
      .update({
        last_seen_at: new Date().toISOString(),
        is_online: true
      })
      .eq('id', device_id);

    return new Response(JSON.stringify({
      ingested: insertedCount,
      skipped_duplicates: skipped
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
