import { serve } from 'https://deno.land/std@0.177.0/http/server.ts';
import { corsHeaders } from '../_shared/cors.ts';
import { authenticateGuardian, getServiceClient } from '../_shared/authHelper.ts';

serve(async (req: Request) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  try {
    const { user } = await authenticateGuardian(req);
    const url = new URL(req.url);
    const deviceId = url.searchParams.get('device_id');
    const limit = Math.min(parseInt(url.searchParams.get('limit') ?? '50', 10), 200);

    if (!deviceId) {
      return new Response(JSON.stringify({ error: 'Missing device_id parameter' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    const admin = getServiceClient();

    // Verify guardian authorization
    const { data: device, error: devErr } = await admin
      .from('devices')
      .select('id, family_id, name, last_seen_at, is_online, device_consents(app_activity_enabled)')
      .eq('id', deviceId)
      .single();

    if (devErr || !device) {
      return new Response(JSON.stringify({ error: 'Device not found' }), {
        status: 404,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    const { data: member, error: memErr } = await admin
      .from('family_members')
      .select('role')
      .eq('family_id', device.family_id)
      .eq('user_id', user.id)
      .single();

    if (memErr || !member || member.role !== 'guardian') {
      return new Response(JSON.stringify({ error: 'Unauthorized: Guardian access required' }), {
        status: 403,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    const consent = Array.isArray(device.device_consents) ? device.device_consents[0] : device.device_consents;
    if (!consent?.app_activity_enabled) {
      return new Response(JSON.stringify({
        error: 'App activity monitoring is currently disabled by the target user',
        code: 'CONSENT_DISABLED'
      }), {
        status: 403,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    // Fetch timeline events (past 7 days maximum)
    const sevenDaysAgo = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString();
    const { data: events, error: eventsErr } = await admin
      .from('device_app_events')
      .select('*')
      .eq('device_id', deviceId)
      .gte('occurred_at', sevenDaysAgo)
      .order('occurred_at', { ascending: false })
      .limit(limit);

    if (eventsErr) throw eventsErr;

    const latestEvent = events && events.length > 0 ? events[0] : null;

    // Daily summary calculations
    const todayStart = new Date();
    todayStart.setHours(0, 0, 0, 0);

    const { data: todayEvents } = await admin
      .from('device_app_events')
      .select('package_name, app_label, duration_seconds')
      .eq('device_id', deviceId)
      .gte('occurred_at', todayStart.toISOString());

    const usageMap: Record<string, { app_label: string; total_seconds: number }> = {};
    if (todayEvents) {
      for (const ev of todayEvents) {
        const pkg = ev.package_name;
        const dur = Number(ev.duration_seconds) || 0;
        if (!usageMap[pkg]) {
          usageMap[pkg] = { app_label: ev.app_label || pkg, total_seconds: 0 };
        }
        usageMap[pkg].total_seconds += dur;
      }
    }

    const summaries = Object.entries(usageMap)
      .map(([package_name, val]) => ({
        package_name,
        app_label: val.app_label,
        total_minutes: Math.round(val.total_seconds / 60)
      }))
      .sort((a, b) => b.total_minutes - a.total_minutes);

    return new Response(JSON.stringify({
      device_id: deviceId,
      device_name: device.name,
      is_online: device.is_online,
      last_seen_at: device.last_seen_at,
      latest_detected_app: latestEvent ? {
        package_name: latestEvent.package_name,
        app_label: latestEvent.app_label,
        event_type: latestEvent.event_type,
        occurred_at: latestEvent.occurred_at
      } : null,
      timeline: events || [],
      daily_summaries: summaries
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
