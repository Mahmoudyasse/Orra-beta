// Sends FCM data messages for new chat messages and incoming ORRA calls.
// Secrets: FCM_SERVICE_ACCOUNT (full service-account JSON), ORRA_PUSH_SECRET.
// SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are provided by Supabase.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const b64url = (input: string | Uint8Array): string => {
  const bytes = typeof input === "string" ? new TextEncoder().encode(input) : input;
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
};

async function googleAccessToken(sa: { client_email: string; private_key: string }): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const unsigned = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" })) + "." + b64url(JSON.stringify({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }));
  const pem = sa.private_key.replace(/-----[^-]+-----/g, "").replace(/\s/g, "");
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey("pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const sig = new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned)));
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: unsigned + "." + b64url(sig),
    }),
  });
  const json = await res.json();
  if (!json.access_token) throw new Error("Google token error: " + JSON.stringify(json));
  return json.access_token;
}

Deno.serve(async (request: Request) => {
  if (request.method !== "POST") return new Response("POST required", { status: 405 });
  const secret = Deno.env.get("ORRA_PUSH_SECRET");
  if (!secret || request.headers.get("x-orra-push-secret") !== secret) return new Response("forbidden", { status: 403 });

  const payload = await request.json();
  if (payload.type !== "INSERT" || !payload.record) return Response.json({ skipped: true });
  const record = payload.record;

  const supabase = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const sa = JSON.parse(Deno.env.get("FCM_SERVICE_ACCOUNT") ?? "{}");
  if (!sa.client_email || !sa.project_id) return Response.json({ error: "FCM_SERVICE_ACCOUNT missing" }, { status: 500 });

  let targets: { user_id: string; fcm_token: string; device_id: string }[] = [];
  let data: Record<string, string> = {};

  if (payload.table === "messages") {
    const { data: sender } = await supabase.from("profiles").select("username").eq("id", record.sender_id).maybeSingle();
    const { data: rows } = await supabase.rpc("orra_push_targets_for_conversation", {
      p_conversation: record.conversation_id, p_sender: record.sender_id,
    });
    targets = rows ?? [];
    data = {
      type: "message",
      title: "@" + (sender?.username ?? "ORRA"),
      body: String(record.body ?? "").slice(0, 140),
      conversation_id: String(record.conversation_id),
    };
  } else if (payload.table === "orra_call_sessions" && record.status === "ringing") {
    const { data: caller } = await supabase.from("profiles").select("username").eq("id", record.caller_id).maybeSingle();
    const { data: rows } = await supabase.rpc("orra_push_targets_for_user", { p_user: record.callee_id });
    targets = rows ?? [];
    data = {
      type: "call",
      title: "@" + (caller?.username ?? "ORRA") + (record.call_type === "video" ? " – video call" : " – voice call"),
      body: "Incoming ORRA call",
      call_id: String(record.id),
    };
  } else return Response.json({ skipped: true });

  if (targets.length === 0) return Response.json({ sent: 0 });
  const token = await googleAccessToken(sa);
  let sent = 0;
  for (const t of targets) {
    const res = await fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
      method: "POST",
      headers: { Authorization: "Bearer " + token, "Content-Type": "application/json" },
      body: JSON.stringify({ message: { token: t.fcm_token, data, android: { priority: "HIGH" } } }),
    });
    if (res.ok) sent++;
    else if (res.status === 404) await supabase.from("push_tokens").delete().eq("user_id", t.user_id).eq("device_id", t.device_id);
  }
  return Response.json({ sent });
});
