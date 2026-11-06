# Orra Beta

Independent Android beta project derived from the original ORRA app.

- Android application ID: `com.orra.beta`
- Implementation: Kotlin and Jetpack Compose
- The original `orra-android` repository remains untouched.

## Current features

- Contacts, local favorites, keypad and ORRA-originated call history.
- Android Telecom call flow with SIM account selection (requires device testing).
- Voice recording with Android-provided noise suppression / echo cancellation when supported.
- Supabase-backed chat foundation: email/password account creation and sign-in, username search, direct conversations, sending and loading messages.
- Chat inbox refreshes from the server every 3 seconds in this beta. This is polling, not a persistent WebSocket connection or push delivery.

## Enable online chat

1. Create a Supabase project.
2. Open the Supabase SQL Editor and run the complete script at [supabase/orra_chat_schema.sql](supabase/orra_chat_schema.sql).
3. In the Supabase project settings, enable email/password authentication. For initial testing, decide whether email confirmation is required.
4. Copy the Project URL and the Publishable key from the Supabase project Connect/API settings.
5. Open ORRA > Chats, enter the Project URL and Publishable key, then create accounts on two separate devices or app installs.
6. Search for the other account's username and start a conversation.

The publishable key is designed for client use; never put a Supabase service-role/secret key in the Android app. The SQL script enables Row Level Security and restricts conversation/message access to conversation members.

## Beta limitations

- The backend schema must be applied to a real Supabase project before chat can connect.
- Messages are synchronized by periodic refresh in this beta; WebSocket realtime, push notifications, attachments, read receipts, typing indicators and end-to-end encryption are not implemented yet.
- Validate policies and authentication settings before production use. A successful Android build does not prove live backend connectivity or device-specific calling behavior.
