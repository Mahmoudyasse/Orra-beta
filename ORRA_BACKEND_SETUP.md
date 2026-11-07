# ORRA backend setup

## Apply SQL migrations in this exact order

Run each file in the Supabase SQL Editor, once, in this order:

1. `supabase/orra_chat_schema.sql`
2. `supabase/orra_features_v2.sql`
3. `supabase/orra_presence_v3.sql`
4. `supabase/orra_internet_calls_v1.sql`
5. `supabase/orra_reliability_v4.sql`
6. `supabase/orra_security_v5.sql`
7. `supabase/orra_attachments_v6.sql`
8. `supabase/orra_direct_messages_v7.sql`
9. `supabase/orra_storage_upsert_v8.sql`
10. `supabase/orra_account_backup_history_v10.sql`

The reliability migration adds message delivery/read receipts and restricts call acceptance to the callee. The security migration removes anonymous execution from the app RPCs and adds missing foreign-key indexes. The attachment migration allows generic Android document-provider files while keeping the bucket private and capped at 20 MB. The direct-message migration ensures a DM is never accidentally opened as a group chat. The storage upsert migration grants safe owner-only UPDATE permission required by Supabase Storage upsert. The v10 migration supports phone-only Auth profiles, verified phone directory entries, content snapshots for deleted messages, and private daily chat backups with restore.

## Enable email confirmation deep links

In Supabase Dashboard:

- Authentication → URL Configuration → add `com.orra.beta://auth-callback/` to Redirect URLs.
- In the confirmation email template, keep the Supabase confirmation URL and ensure its redirect uses the supplied redirect target (`{{ .RedirectTo }}`) after verification.
- Do not use `localhost` as the mobile redirect target.

The app handles the access and refresh tokens returned in the callback fragment. If email confirmation is disabled, password sign-in returns a session immediately. Daily chat snapshots are stored in the private `orra_chat_backups` table; users can create and restore backups from Settings. Deleted-message snapshots are available from each chat’s settings.

## Enable real phone/SIM-number sign-in

The app uses Supabase Phone OTP. The SIM is the user's SMS inbox; Android does not expose a reliable or privacy-safe way for ORRA to read the SIM's phone number automatically.

In Supabase Dashboard:

- Authentication → Sign In / Providers → enable Phone.
- Configure an SMS provider (Twilio, Vonage, MessageBird, or another supported provider) with its real credentials.
- Confirm SMS rate limits and OTP expiry.
- Use E.164 numbers such as +2010XXXXXXXX.
- For existing email accounts, the profile screen can link a phone number only after the phone-change OTP is verified.

Until the provider is configured, the app can request an OTP but Supabase cannot deliver a real SMS.

## Enable Google sign-in

- In Supabase Dashboard → Authentication → Providers, enable Google.
- Add the Google OAuth Client ID and Client Secret from the Google Cloud OAuth client registered for this app.
- Add the Supabase callback URL shown in the Google provider settings to the Google Cloud OAuth client's authorized redirect URIs.
- Add `com.orra.beta://auth-callback/` to Supabase Redirect URLs as above.

The app's Google button starts Supabase OAuth in the browser. It does not grant Gmail mailbox access; Gmail data access would require separate Google scopes and consent.

## Configure TURN for reliable calls across networks

The authenticated `orra-turn-credentials` Edge Function has been deployed to the linked Supabase project. The Android client calls it before WebRTC setup and always has a public STUN fallback. TURN is needed for restrictive NATs, carrier networks, and some Wi-Fi networks. The function currently returns STUN only until the two TURN secrets below are configured.

1. Provision a TURN server/provider that supports the coturn REST shared-secret credential format.
2. Configure the TURN server with `use-auth-secret`, `static-auth-secret` and a `realm`.
3. Deploy the Edge Function:

   `supabase functions deploy orra-turn-credentials`

4. Set these Edge Function secrets (never put them in Android or Git):

   - `ORRA_TURN_URL` — for example `turn:turn.example.com:3478?transport=udp`
   - `ORRA_TURN_SHARED_SECRET` — the same shared secret configured on coturn

The Edge Function generates short-lived, user-scoped credentials. Without these secrets, it returns STUN only; calls may fail on restrictive networks.

## Current backend deployment state

- SQL migrations 1–10 are applied to project `bmehobgcdjtnbwsdgjxg`.
- `orra-turn-credentials` Edge Function is deployed with JWT verification enabled.
- Chat and profile buckets are private; chat files are capped at 100 MB and profile images at 5 MB.
- Database smoke tests for conversation creation, message receipts, message edit/delete, group creation, and call signaling/accept/end ran inside a rolled-back transaction.

## Push notifications

Incoming ORRA calls show an in-app prompt while ORRA is running. Calls and chat messages cannot reliably notify when the app is backgrounded or terminated until Firebase Cloud Messaging is configured.

This repository does not contain `google-services.json` or Firebase service-account credentials. Those are project-specific and must come from the Firebase project registered for application ID `com.orra.beta`. Never commit a service-account private key or FCM server credential. Once the Firebase Android app file is added, the FCM receiver and server-side call notification function can be enabled.

## Test checklist

- Register two distinct accounts and verify both email links return to ORRA.
- Sign in on two devices.
- Send text in both directions; verify message appears on the other device.
- Send image, document, location, and voice note; open/play each on the recipient device.
- Verify sent → delivered → read indicators after opening the recipient conversation.
- Start audio and video calls in both directions, switch Wi-Fi/mobile data, mute/unmute, camera on/off, speaker on/off, reject, and hang up.
- Repeat calls with the app backgrounded after FCM is configured.
- Test SIM 1/SIM 2 and incoming cellular calls on a physical dual-SIM device.
- Deny each runtime permission once and verify the app explains the missing permission without crashing.


## v11: device sessions and push notifications

1. Run `supabase/orra_sessions_push_v11.sql` in the Supabase SQL Editor (Devices screen + push tokens).
2. Firebase: create a project, add an Android app with package `com.orra.beta`, download `google-services.json` into `app/`, commit it. Without this file the app builds normally and push stays off.
3. Firebase console > Project settings > Service accounts > Generate new private key.
4. Deploy the Edge Function: `supabase functions deploy orra-push-send --no-verify-jwt`
5. Set secrets: `supabase secrets set FCM_SERVICE_ACCOUNT='<the whole JSON>' ORRA_PUSH_SECRET='<long random string>'`
6. Supabase Dashboard > Database > Webhooks: create two webhooks (INSERT on `public.messages`, INSERT on `public.orra_call_sessions`), both POST to `https://<project-ref>.supabase.co/functions/v1/orra-push-send` with header `x-orra-push-secret: <same random string>`.
