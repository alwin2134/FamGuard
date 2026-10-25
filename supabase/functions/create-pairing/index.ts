import { corsHeaders } from '../_shared/cors.ts';
import { authenticateGuardian, getServiceClient, hashSecret } from '../_shared/authHelper.ts';

Deno.serve(async (req: Request) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  try {
    const { user } = await authenticateGuardian(req);
    const { family_id, target_name } = await req.json();

    if (!family_id || !target_name) {
      return new Response(JSON.stringify({ error: 'Missing family_id or target_name' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    const admin = getServiceClient();

    // Verify guardian role in this family
    const { data: member, error: memberErr } = await admin
      .from('family_members')
      .select('role, display_name, families(name)')
      .eq('family_id', family_id)
      .eq('user_id', user.id)
      .single();

    if (memberErr || !member || member.role !== 'guardian') {
      return new Response(JSON.stringify({ error: 'Only authorized guardians can generate pairing tokens' }), {
        status: 403,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    // Generate random 32-char token
    const randomBytes = new Uint8Array(24);
    crypto.getRandomValues(randomBytes);
    const token = Array.from(randomBytes).map(b => b.toString(16).padStart(2, '0')).join('');
    const tokenHash = await hashSecret(token);

    // 15 minutes expiry
    const expiresAt = new Date(Date.now() + 15 * 60 * 1000).toISOString();

    const { error: insertErr } = await admin
      .from('pairing_tokens')
      .insert({
        family_id,
        created_by: user.id,
        token_hash: tokenHash,
        target_name,
        status: 'pending',
        expires_at: expiresAt
      });

    if (insertErr) {
      throw insertErr;
    }

    // Record audit event
    await admin.from('audit_events').insert({
      family_id,
      actor_id: user.id,
      actor_type: 'guardian',
      event_name: 'pairing_token_created',
      details: { target_name, expires_at: expiresAt }
    });

    const qrPayload = JSON.stringify({
      token,
      family_id,
      family_name: (member.families as any)?.name ?? 'FamGuard Family',
      guardian_name: member.display_name,
      server_url: Deno.env.get('SUPABASE_URL') ?? '',
      anon_key: Deno.env.get('SUPABASE_ANON_KEY') ?? ''
    });

    return new Response(JSON.stringify({
      pairing_token: token,
      qr_payload: qrPayload,
      expires_at: expiresAt
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
