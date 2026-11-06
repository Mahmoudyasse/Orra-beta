# ORRA Beta

Independent Android beta project derived from the original ORRA app.

- Android application ID: `com.orra.beta`
- Implementation: Kotlin, Jetpack Compose, CameraX and Android Telecom
- Developer credit: **Hamoudy Yasser**
- The original `orra-android` repository remains untouched.

## Implemented app features

- Contacts: read, search, favorites, add, edit, delete and save contacts from a chat profile.
- Dialer and SIM selection using Android Telecom.
- In-call controls: answer, decline, mute, speaker, hold, keypad and end call.
- Camera: front/rear preview, zoom, rear flash, photo capture and HD video recording with microphone audio.
- Voice Studio: Android-supported noise suppression, echo cancellation and gain control, private persistent recording library, playback and delete.
- ORRA chat accounts, username search, direct chats and group creation/member management.
- Chat profile: display name, bio, optional phone number, avatar and cover image.
- Chat media: private image/file uploads and processed voice-note recording/upload.
- Message actions: edit, delete for me, delete for everyone and server-side edit/delete event history.
- Chat actions: block/unblock storage foundation, leave group, delete chat for me, mute preference and per-chat color themes.
- Global appearance: System, Dark and Light modes with liquid accent palettes.
- In-app ORRA Dynamic Island styling.

## Enable online chat and advanced messaging

1. Create a Supabase project.
2. Open Supabase SQL Editor and run the complete script [supabase/orra_chat_schema.sql](supabase/orra_chat_schema.sql).
3. Then run the complete upgrade script [supabase/orra_features_v2.sql](supabase/orra_features_v2.sql).
4. Enable email/password authentication. Configure email confirmation as desired for testing.
5. Copy the Project URL and Publishable key from Supabase project settings.
6. Open ORRA > Chats, enter the Project URL and Publishable key, then create accounts on two separate devices or app installs.
7. Search for the other account's username and start a direct chat or create a group.

The publishable key is designed for client use. Never put a Supabase service-role or secret key in the Android app. The database scripts enable Row Level Security and restrict messaging/storage access.

## Calling behavior

- Voice calls started from a chat use the other user's optional profile phone number and Android Telecom/SIM calling. ORRA must be selected as the default phone app and the user must grant required Android permissions.
- Video calls use Android Telecom carrier video calling. They work only when the carrier, both devices, SIM plans and network support bidirectional video. This is not internet-based ORRA-to-ORRA WebRTC calling.
- Camera video recording is a separate local feature and does not create a live video call.

## Beta limitations and production work

- Chat synchronization currently uses periodic refresh while the chat UI is open. It is not persistent WebSocket realtime.
- Push notifications are not configured; the mute preference is saved server-side but cannot suppress push notifications until a push delivery service is integrated.
- Internet-based ORRA voice/video calling, TURN infrastructure, read receipts, typing indicators, message forwarding, end-to-end encryption, robust offline delivery and account recovery are not implemented.
- Media upload, RLS and RPC behavior must be tested against the user's actual Supabase project after both SQL scripts are run.
- A successful Android build does not prove live backend connectivity, carrier video support or behavior across different devices. Test on two real devices before treating the beta as production-ready.
