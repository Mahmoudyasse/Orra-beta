import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function base64(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (request.method !== "POST") return Response.json({ error: "POST required" }, { status: 405, headers: cors });

  const url = Deno.env.get("SUPABASE_URL");
  const anon = Deno.env.get("SUPABASE_ANON_KEY");
  const turnUrl = Deno.env.get("ORRA_TURN_URL");
  const sharedSecret = Deno.env.get("ORRA_TURN_SHARED_SECRET");
  const authorization = request.headers.get("Authorization");

  if (!url || !anon || !authorization) {
    return Response.json({ error: "Authentication configuration is missing." }, { status: 401, headers: cors });
  }

  const supabase = createClient(url, anon, { global: { headers: { Authorization: authorization } } });
  const { data: { user }, error } = await supabase.auth.getUser();
  if (error || !user) return Response.json({ error: "Valid ORRA sign-in is required." }, { status: 401, headers: cors });

  const iceServers: Array<Record<string, unknown>> = [
    { urls: ["stun:stun.l.google.com:19302"] },
  ];

  if (turnUrl && sharedSecret) {
    const expires = Math.floor(Date.now() / 1000) + 3600;
    const username = `${expires}:${user.id}`;
    const key = await crypto.subtle.importKey(
      "raw", new TextEncoder().encode(sharedSecret), { name: "HMAC", hash: "SHA-1" }, false, ["sign"],
    );
    const signature = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(username));
    iceServers.push({
      urls: [turnUrl],
      username,
      credential: base64(new Uint8Array(signature)),
    });
  }

  return Response.json({ iceServers, turnConfigured: iceServers.length > 1 }, {
    headers: { ...cors, "Cache-Control": "no-store" },
  });
});
