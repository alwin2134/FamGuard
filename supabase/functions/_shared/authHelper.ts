import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.42.0';

export function getServiceClient() {
  const supabaseUrl = Deno.env.get('SUPABASE_URL') ?? '';
  const serviceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? '';
  return createClient(supabaseUrl, serviceRoleKey);
}

export async function hashSecret(secret: string): Promise<string> {
  const encoder = new TextEncoder();
  const data = encoder.encode(secret);
  const hashBuffer = await crypto.subtle.digest('SHA-256', data);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
}

export async function authenticateGuardian(req: Request) {
  const authHeader = req.headers.get('Authorization');
  if (!authHeader) {
    throw new Error('Missing Authorization header');
  }

  const supabaseUrl = Deno.env.get('SUPABASE_URL') ?? '';
  const anonKey = Deno.env.get('SUPABASE_ANON_KEY') ?? '';
  const client = createClient(supabaseUrl, anonKey, {
    global: { headers: { Authorization: authHeader } }
  });

  const { data: { user }, error } = await client.auth.getUser();
  if (error || !user) {
    throw new Error('Unauthorized guardian token: ' + (error?.message ?? 'unknown'));
  }

  return { user, client };
}

export async function authenticateDevice(deviceId: string, deviceSecret: string) {
  const adminClient = getServiceClient();
  const secretHash = await hashSecret(deviceSecret);

  const { data: device, error } = await adminClient
    .from('devices')
    .select('id, family_id, name, status, device_secret_hash')
    .eq('id', deviceId)
    .single();

  if (error || !device) {
    throw new Error('Device not found');
  }

  if (device.status !== 'active') {
    throw new Error(`Device is not active (status: ${device.status})`);
  }

  if (device.device_secret_hash !== secretHash) {
    throw new Error('Invalid device credentials');
  }

  return { device, adminClient };
}
