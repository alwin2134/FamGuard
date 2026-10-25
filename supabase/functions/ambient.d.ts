// Ambient declarations for Deno and URL-based imports in Supabase Edge Functions
declare module 'https://*' {
  const content: any;
  export default content;
  export const createClient: any;
  export const serve: any;
  export const corsHeaders: any;
}

declare module 'npm:*' {
  const content: any;
  export default content;
}

declare namespace Deno {
  export interface Env {
    get(key: string): string | undefined;
    set(key: string, value: string): void;
    has(key: string): boolean;
  }
  export const env: Env;
  export function serve(handler: (req: Request) => Promise<Response> | Response): void;
  export function serve(options: any, handler?: (req: Request) => Promise<Response> | Response): void;
}
