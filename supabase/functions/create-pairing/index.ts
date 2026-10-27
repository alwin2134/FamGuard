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

    // Check if the user is a guardian of the specified family, or ANY family
    let member: any = null;
    if (family_id && family_id !== '11111111-1111-1111-1111-111111111111') {
      const { data: specificMember } = await admin
        .from('family_members')
        .select('family_id, role, display_name, families(name)')
        .eq('family_id', family_id)
        .eq('user_id', user.id)
        .maybeSingle();

      if (specificMember && specificMember.role === 'guardian') {
        member = specificMember;
      }
    }

    if (!member) {
      // Look for ANY family where this authenticated user is a guardian
      const { data: anyMember } = await admin
        .from('family_members')
        .select('family_id, role, display_name, families(name)')
        .eq('user_id', user.id)
        .eq('role', 'guardian')
        .limit(1)
        .maybeSingle();

      if (anyMember) {
        member = anyMember;
      }
    }

    if (!member) {
      // Auto-provision a default family for this guardian!
      const familyName = user.email ? `${user.email.split('@')[0]}'s Family` : 'FamGuard Family';
      const displayName = user.email ? user.email.split('@')[0] : 'Guardian';

      const { data: newFamily, error: famErr } = await admin
        .from('families')
        .insert({ name: familyName })
        .select('id, name')
        .single();

      if (famErr || !newFamily) {
        throw new Error(famErr?.message ?? 'Failed to initialize guardian family');
      }

      const { data: newMember, error: memErr } = await admin
        .from('family_members')
        .insert({
          family_id: newFamily.id,
          user_id: user.id,
          role: 'guardian',
          display_name: displayName
        })
        .select('family_id, role, display_name, families(name)')
        .single();

      if (memErr || !newMember) {
        throw new Error(memErr?.message ?? 'Failed to register guardian membership');
      }

      member = newMember;
    }

    const effectiveFamilyId = member.family_id;

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
        family_id: effectiveFamilyId,
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
      family_id: effectiveFamilyId,
      actor_id: user.id,
      actor_type: 'guardian',
      event_name: 'pairing_token_created',
      details: { target_name, expires_at: expiresAt }
    });

    const qrPayload = JSON.stringify({
      token,
      family_id: effectiveFamilyId,
      family_name: (member.families as any)?.name ?? 'FamGuard Family',
      guardian_name: member.display_name,
      server_url: Deno.env.get('SUPABASE_URL') ?? '',
      anon_key: Deno.env.get('SUPABASE_ANON_KEY') ?? ''
    });

    return new Response(JSON.stringify({
      pairing_token: token,
      qr_payload: qrPayload,
      expires_at: expiresAt,
      family_id: effectiveFamilyId
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
