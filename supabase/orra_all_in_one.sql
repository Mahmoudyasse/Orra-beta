-- ORRA: ALL database migrations in one file (safe to run more than once).
-- Supabase Dashboard > SQL Editor > New query > paste > Run.


-- =====================================================================
-- orra_chat_schema.sql
-- =====================================================================
-- ORRA Beta messaging schema
-- Run this entire file in Supabase Dashboard > SQL Editor.

create extension if not exists pgcrypto;

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  username text not null unique,
  created_at timestamptz not null default now()
);

create table if not exists public.conversations (
  id uuid primary key default gen_random_uuid(),
  created_at timestamptz not null default now()
);

create table if not exists public.conversation_members (
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  joined_at timestamptz not null default now(),
  primary key (conversation_id, user_id)
);

create table if not exists public.messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  sender_id uuid not null references public.profiles(id) on delete cascade,
  body text not null check (char_length(body) between 1 and 4000),
  created_at timestamptz not null default now()
);

create index if not exists messages_conversation_created_idx
  on public.messages(conversation_id, created_at desc);
create index if not exists conversation_members_user_idx
  on public.conversation_members(user_id);

create or replace function public.handle_new_orra_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  requested_name text;
begin
  requested_name := lower(coalesce(new.raw_user_meta_data->>'username', split_part(new.email, '@', 1)));
  requested_name := regexp_replace(requested_name, '[^a-z0-9_.]', '', 'g');
  if length(requested_name) < 3 then
    requested_name := 'user_' || substr(new.id::text, 1, 8);
  end if;
  insert into public.profiles(id, username)
  values (new.id, requested_name)
  on conflict do nothing;

  if not exists (select 1 from public.profiles where id = new.id) then
    insert into public.profiles(id, username)
    values (new.id, requested_name || '_' || substr(new.id::text, 1, 6))
    on conflict (id) do nothing;
  end if;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created_orra on auth.users;
create trigger on_auth_user_created_orra
after insert on auth.users
for each row execute procedure public.handle_new_orra_user();

create or replace function public.is_orra_conversation_member(target_conversation uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.conversation_members
    where conversation_id = target_conversation and user_id = auth.uid()
  );
$$;

create or replace function public.create_or_get_conversation(other_user_id uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  current_user_id uuid := auth.uid();
  existing_id uuid;
  new_id uuid;
begin
  if current_user_id is null then
    raise exception 'Authentication required';
  end if;
  if other_user_id is null or other_user_id = current_user_id then
    raise exception 'Choose another user';
  end if;
  if not exists (select 1 from public.profiles where id = other_user_id) then
    raise exception 'User not found';
  end if;

  select first_member.conversation_id into existing_id
  from public.conversation_members first_member
  join public.conversation_members second_member
    on second_member.conversation_id = first_member.conversation_id
  where first_member.user_id = current_user_id
    and second_member.user_id = other_user_id
  limit 1;

  if existing_id is not null then
    return existing_id;
  end if;

  insert into public.conversations default values returning id into new_id;
  insert into public.conversation_members(conversation_id, user_id)
  values (new_id, current_user_id), (new_id, other_user_id);
  return new_id;
end;
$$;

alter table public.profiles enable row level security;
alter table public.conversations enable row level security;
alter table public.conversation_members enable row level security;
alter table public.messages enable row level security;

drop policy if exists "Authenticated users can find profiles" on public.profiles;
create policy "Authenticated users can find profiles"
on public.profiles for select to authenticated using (true);

drop policy if exists "Users update own profile" on public.profiles;
create policy "Users update own profile"
on public.profiles for update to authenticated
using (id = auth.uid()) with check (id = auth.uid());

drop policy if exists "Members read conversations" on public.conversations;
create policy "Members read conversations"
on public.conversations for select to authenticated
using (public.is_orra_conversation_member(id));

drop policy if exists "Members read membership" on public.conversation_members;
create policy "Members read membership"
on public.conversation_members for select to authenticated
using (public.is_orra_conversation_member(conversation_id));

drop policy if exists "Members read messages" on public.messages;
create policy "Members read messages"
on public.messages for select to authenticated
using (public.is_orra_conversation_member(conversation_id));

drop policy if exists "Members send messages as themselves" on public.messages;
create policy "Members send messages as themselves"
on public.messages for insert to authenticated
with check (
  sender_id = auth.uid()
  and public.is_orra_conversation_member(conversation_id)
);

grant usage on schema public to authenticated;
grant select on public.profiles, public.conversations, public.conversation_members, public.messages to authenticated;
grant update (username) on public.profiles to authenticated;
grant insert on public.messages to authenticated;
grant execute on function public.create_or_get_conversation(uuid) to authenticated;
grant execute on function public.is_orra_conversation_member(uuid) to authenticated;

-- Enable database change publication for future WebSocket subscriptions.
do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime'
      and schemaname = 'public'
      and tablename = 'messages'
  ) then
    alter publication supabase_realtime add table public.messages;
  end if;
end $$;


-- =====================================================================
-- orra_features_v2.sql
-- =====================================================================
-- ORRA messaging upgrade v2. Run after orra_chat_schema.sql in Supabase SQL Editor.
-- Adds real group chats, profile media, message attachments/edit/delete, blocks and per-chat mute settings.

alter table public.profiles
  add column if not exists display_name text,
  add column if not exists bio text not null default '',
  add column if not exists phone_number text,
  add column if not exists avatar_url text,
  add column if not exists cover_url text,
  add column if not exists updated_at timestamptz not null default now();

alter table public.conversations
  add column if not exists title text not null default '',
  add column if not exists is_group boolean not null default false,
  add column if not exists created_by uuid references public.profiles(id) on delete set null;

alter table public.messages
  add column if not exists message_type text not null default 'text',
  add column if not exists media_url text,
  add column if not exists edited_at timestamptz,
  add column if not exists deleted_for_everyone boolean not null default false,
  add column if not exists reply_to uuid references public.messages(id) on delete set null;

create table if not exists public.blocked_users (
  blocker_id uuid not null references public.profiles(id) on delete cascade,
  blocked_id uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker_id, blocked_id),
  check (blocker_id <> blocked_id)
);

create table if not exists public.conversation_settings (
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  muted_until timestamptz,
  archived boolean not null default false,
  pinned boolean not null default false,
  updated_at timestamptz not null default now(),
  primary key (conversation_id, user_id)
);

create table if not exists public.message_deletions (
  message_id uuid not null references public.messages(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  deleted_at timestamptz not null default now(),
  primary key (message_id, user_id)
);

alter table public.blocked_users enable row level security;
alter table public.conversation_settings enable row level security;
alter table public.message_deletions enable row level security;

drop policy if exists "Users manage own blocks" on public.blocked_users;
create policy "Users manage own blocks" on public.blocked_users
  for all to authenticated
  using (blocker_id = auth.uid())
  with check (blocker_id = auth.uid());

drop policy if exists "Users manage own conversation settings" on public.conversation_settings;
create policy "Users manage own conversation settings" on public.conversation_settings
  for all to authenticated
  using (user_id = auth.uid() and public.is_orra_conversation_member(conversation_id))
  with check (user_id = auth.uid() and public.is_orra_conversation_member(conversation_id));

drop policy if exists "Users manage own message deletions" on public.message_deletions;
create policy "Users manage own message deletions" on public.message_deletions
  for all to authenticated
  using (user_id = auth.uid() and exists (
    select 1 from public.messages m where m.id = message_id
      and public.is_orra_conversation_member(m.conversation_id)
  ))
  with check (user_id = auth.uid() and exists (
    select 1 from public.messages m where m.id = message_id
      and public.is_orra_conversation_member(m.conversation_id)
  ));

drop policy if exists "Members update own messages" on public.messages;
create policy "Members update own messages" on public.messages
  for update to authenticated
  using (sender_id = auth.uid() and public.is_orra_conversation_member(conversation_id))
  with check (sender_id = auth.uid() and public.is_orra_conversation_member(conversation_id));

grant select, insert, update, delete on public.blocked_users to authenticated;
grant select, insert, update, delete on public.conversation_settings to authenticated;
grant select, insert, delete on public.message_deletions to authenticated;
grant update (body, edited_at, deleted_for_everyone, media_url, message_type) on public.messages to authenticated;
grant update (display_name, bio, phone_number, avatar_url, cover_url, updated_at) on public.profiles to authenticated;

create or replace function public.create_orra_group(group_title text, member_ids uuid[])
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  caller uuid := auth.uid();
  new_conversation uuid;
  member_id uuid;
begin
  if caller is null then raise exception 'Authentication required'; end if;
  if coalesce(length(trim(group_title)), 0) < 1 then raise exception 'Group name is required'; end if;
  if coalesce(array_length(member_ids, 1), 0) < 1 then raise exception 'Choose at least one group member'; end if;
  if exists (
    select 1 from unnest(member_ids) x
    where not exists (select 1 from public.profiles p where p.id = x)
  ) then raise exception 'One or more users do not exist'; end if;

  insert into public.conversations(title, is_group, created_by)
  values (trim(group_title), true, caller)
  returning id into new_conversation;

  insert into public.conversation_members(conversation_id, user_id)
  values (new_conversation, caller);

  foreach member_id in array member_ids loop
    if member_id <> caller then
      insert into public.conversation_members(conversation_id, user_id)
      values (new_conversation, member_id)
      on conflict do nothing;
    end if;
  end loop;
  return new_conversation;
end;
$$;

create or replace function public.orra_add_group_member(target_conversation uuid, new_member uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.is_orra_conversation_member(target_conversation) then
    raise exception 'You are not a member of this conversation';
  end if;
  if not exists (select 1 from public.conversations c where c.id = target_conversation and c.is_group) then
    raise exception 'This is not a group';
  end if;
  if not exists (select 1 from public.profiles p where p.id = new_member) then
    raise exception 'User not found';
  end if;
  insert into public.conversation_members(conversation_id, user_id)
  values (target_conversation, new_member)
  on conflict do nothing;
end;
$$;

create or replace function public.orra_leave_group(target_conversation uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if not exists (select 1 from public.conversations c where c.id = target_conversation and c.is_group) then
    raise exception 'This is not a group';
  end if;
  delete from public.conversation_members
  where conversation_id = target_conversation and user_id = auth.uid();
end;
$$;

grant execute on function public.create_orra_group(text, uuid[]) to authenticated;
grant execute on function public.orra_add_group_member(uuid, uuid) to authenticated;
grant execute on function public.orra_leave_group(uuid) to authenticated;

-- Private storage buckets. Media is served through authenticated downloads, not public URLs.
insert into storage.buckets(id, name, public, file_size_limit, allowed_mime_types)
values
  ('orra-profiles', 'orra-profiles', false, 5242880, array['image/jpeg','image/png','image/webp']),
  ('orra-chat-media', 'orra-chat-media', false, 20971520, array['image/jpeg','image/png','image/webp','audio/wav','audio/mpeg','audio/mp4','video/mp4','application/pdf'])
on conflict (id) do nothing;

drop policy if exists "ORRA users upload own profile media" on storage.objects;
create policy "ORRA users upload own profile media" on storage.objects
for insert to authenticated
with check (bucket_id = 'orra-profiles' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "ORRA users read profile media" on storage.objects;
create policy "ORRA users read profile media" on storage.objects
for select to authenticated
using (bucket_id = 'orra-profiles');

drop policy if exists "ORRA users update own profile media" on storage.objects;
create policy "ORRA users update own profile media" on storage.objects
for update to authenticated
using (bucket_id = 'orra-profiles' and (storage.foldername(name))[1] = auth.uid()::text)
with check (bucket_id = 'orra-profiles' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "ORRA users delete own profile media" on storage.objects;
create policy "ORRA users delete own profile media" on storage.objects
for delete to authenticated
using (bucket_id = 'orra-profiles' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "ORRA members upload chat media" on storage.objects;
create policy "ORRA members upload chat media" on storage.objects
for insert to authenticated
with check (
  bucket_id = 'orra-chat-media'
  and (storage.foldername(name))[1] = auth.uid()::text
  and exists (
    select 1 from public.conversation_members cm
    where cm.conversation_id::text = (storage.foldername(name))[2]
      and cm.user_id = auth.uid()
  )
);

drop policy if exists "ORRA members read chat media" on storage.objects;
create policy "ORRA members read chat media" on storage.objects
for select to authenticated
using (
  bucket_id = 'orra-chat-media'
  and exists (
    select 1 from public.conversation_members cm
    where cm.conversation_id::text = (storage.foldername(name))[2]
      and cm.user_id = auth.uid()
  )
);

drop policy if exists "ORRA owners delete chat media" on storage.objects;
create policy "ORRA owners delete chat media" on storage.objects
for delete to authenticated
using (bucket_id = 'orra-chat-media' and (storage.foldername(name))[1] = auth.uid()::text);

create or replace function public.orra_delete_conversation_for_me(target_conversation uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  delete from public.conversation_members
  where conversation_id = target_conversation and user_id = auth.uid();
end;
$$;

grant execute on function public.orra_delete_conversation_for_me(uuid) to authenticated;

create or replace function public.orra_users_blocked(first_user uuid, second_user uuid)
returns boolean
language sql stable security definer
set search_path = public
as $$
  select exists (
    select 1 from public.blocked_users
    where (blocker_id = first_user and blocked_id = second_user)
       or (blocker_id = second_user and blocked_id = first_user)
  );
$$;

drop policy if exists "Members read messages" on public.messages;
create policy "Members read messages" on public.messages
for select to authenticated
using (
  public.is_orra_conversation_member(conversation_id)
  and (
    exists (select 1 from public.conversations c where c.id = messages.conversation_id and c.is_group)
    or not exists (
      select 1 from public.conversation_members cm
      where cm.conversation_id = messages.conversation_id
        and cm.user_id <> auth.uid()
        and public.orra_users_blocked(auth.uid(), cm.user_id)
    )
  )
);

drop policy if exists "Members send messages as themselves" on public.messages;
create policy "Members send messages as themselves" on public.messages
for insert to authenticated
with check (
  sender_id = auth.uid()
  and public.is_orra_conversation_member(conversation_id)
  and (
    exists (select 1 from public.conversations c where c.id = messages.conversation_id and c.is_group)
    or not exists (
      select 1 from public.conversation_members cm
      where cm.conversation_id = messages.conversation_id
        and cm.user_id <> auth.uid()
        and public.orra_users_blocked(auth.uid(), cm.user_id)
    )
  )
);

grant execute on function public.orra_users_blocked(uuid, uuid) to authenticated;

create table if not exists public.message_events (
  id uuid primary key default gen_random_uuid(),
  message_id uuid not null references public.messages(id) on delete cascade,
  actor_id uuid not null references public.profiles(id) on delete cascade,
  event_type text not null check (event_type in ('edited','deleted_for_everyone')),
  created_at timestamptz not null default now()
);
alter table public.message_events enable row level security;
drop policy if exists "Members read message event history" on public.message_events;
create policy "Members read message event history" on public.message_events
for select to authenticated
using (exists (
  select 1 from public.messages m
  where m.id = message_id and public.is_orra_conversation_member(m.conversation_id)
));
grant select on public.message_events to authenticated;

create or replace function public.orra_delete_message_for_everyone(target_message uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if not exists (select 1 from public.messages m where m.id = target_message and m.sender_id = auth.uid()) then
    raise exception 'Only the sender can delete this message for everyone';
  end if;
  update public.messages set body = 'This message was deleted', deleted_for_everyone = true, media_url = null, message_type = 'text'
  where id = target_message and sender_id = auth.uid();
  insert into public.message_events(message_id, actor_id, event_type)
  values (target_message, auth.uid(), 'deleted_for_everyone');
end;
$$;

create or replace function public.orra_edit_message(target_message uuid, new_body text)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if coalesce(length(trim(new_body)), 0) < 1 or length(new_body) > 4000 then
    raise exception 'Message text must contain 1 to 4000 characters';
  end if;
  if not exists (select 1 from public.messages m where m.id = target_message
      and m.sender_id = auth.uid() and not m.deleted_for_everyone) then
    raise exception 'Only the sender can edit this message';
  end if;
  update public.messages set body = trim(new_body), edited_at = now()
  where id = target_message and sender_id = auth.uid();
  insert into public.message_events(message_id, actor_id, event_type)
  values (target_message, auth.uid(), 'edited');
end;
$$;

grant execute on function public.orra_delete_message_for_everyone(uuid) to authenticated;
grant execute on function public.orra_edit_message(uuid, text) to authenticated;


-- =====================================================================
-- orra_presence_v3.sql
-- =====================================================================
-- ORRA presence upgrade v3. Run after orra_chat_schema.sql and orra_features_v2.sql.
-- Presence is refreshed by the signed-in app while ORRA Chats is in the foreground.
create table if not exists public.user_presence (
  user_id uuid primary key references public.profiles(id) on delete cascade,
  is_online boolean not null default false,
  is_dnd boolean not null default false,
  is_typing boolean not null default false,
  typing_conversation uuid references public.conversations(id) on delete set null,
  last_seen timestamptz not null default now()
);

alter table public.user_presence enable row level security;

drop policy if exists "Users can view their conversation peers presence" on public.user_presence;
create policy "Users can view their conversation peers presence" on public.user_presence
  for select to authenticated
  using (
    user_id = auth.uid()
    or exists (
      select 1
      from public.conversation_members mine
      join public.conversation_members theirs
        on theirs.conversation_id = mine.conversation_id
      where mine.user_id = auth.uid()
        and theirs.user_id = user_presence.user_id
    )
  );

drop policy if exists "Users update their own presence" on public.user_presence;
create policy "Users update their own presence" on public.user_presence
  for insert to authenticated
  with check (user_id = auth.uid());

drop policy if exists "Users modify their own presence" on public.user_presence;
create policy "Users modify their own presence" on public.user_presence
  for update to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

grant select, insert, update on public.user_presence to authenticated;

create index if not exists user_presence_last_seen_idx
  on public.user_presence(last_seen desc);


-- =====================================================================
-- orra_internet_calls_v1.sql
-- =====================================================================
-- ORRA Internet calling v1. Run after orra_chat_schema.sql and orra_features_v2.sql.
-- Signaling only: media is peer-to-peer through WebRTC, never uploaded to Supabase.

create table if not exists public.orra_call_sessions (
  id uuid primary key default gen_random_uuid(),
  caller_id uuid not null references public.profiles(id) on delete cascade,
  callee_id uuid not null references public.profiles(id) on delete cascade,
  call_type text not null check (call_type in ('audio','video')),
  status text not null default 'ringing' check (status in ('ringing','accepted','rejected','ended','missed')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  ended_at timestamptz,
  check (caller_id <> callee_id)
);

create index if not exists orra_call_sessions_callee_status_idx
  on public.orra_call_sessions(callee_id,status,created_at desc);
create index if not exists orra_call_sessions_caller_created_idx
  on public.orra_call_sessions(caller_id,created_at desc);

create table if not exists public.orra_call_signals (
  id bigint generated always as identity primary key,
  call_id uuid not null references public.orra_call_sessions(id) on delete cascade,
  sender_id uuid not null references public.profiles(id) on delete cascade,
  signal_type text not null check (signal_type in ('offer','answer','ice','hangup')),
  payload jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);
create index if not exists orra_call_signals_call_id_idx
  on public.orra_call_signals(call_id,id);

alter table public.orra_call_sessions enable row level security;
alter table public.orra_call_signals enable row level security;

drop policy if exists "Participants read own ORRA calls" on public.orra_call_sessions;
create policy "Participants read own ORRA calls" on public.orra_call_sessions
for select to authenticated using (caller_id = auth.uid() or callee_id = auth.uid());

drop policy if exists "Users create ORRA calls" on public.orra_call_sessions;
create policy "Users create ORRA calls" on public.orra_call_sessions
for insert to authenticated with check (caller_id = auth.uid() and callee_id <> auth.uid());

drop policy if exists "Participants update ORRA calls" on public.orra_call_sessions;
create policy "Participants update ORRA calls" on public.orra_call_sessions
for update to authenticated
using (caller_id = auth.uid() or callee_id = auth.uid())
with check (caller_id = auth.uid() or callee_id = auth.uid());

drop policy if exists "Participants read ORRA call signals" on public.orra_call_signals;
create policy "Participants read ORRA call signals" on public.orra_call_signals
for select to authenticated using (
  exists (select 1 from public.orra_call_sessions c
    where c.id = call_id and (c.caller_id = auth.uid() or c.callee_id = auth.uid()))
);

drop policy if exists "Participants send ORRA call signals" on public.orra_call_signals;
create policy "Participants send ORRA call signals" on public.orra_call_signals
for insert to authenticated with check (
  sender_id = auth.uid() and exists (
    select 1 from public.orra_call_sessions c
    where c.id = call_id and c.status in ('ringing','accepted')
      and (c.caller_id = auth.uid() or c.callee_id = auth.uid())
  )
);

grant select,insert,update on public.orra_call_sessions to authenticated;
grant select,insert on public.orra_call_signals to authenticated;

create or replace function public.orra_finish_call(target_call uuid, final_status text default 'ended')
returns void language plpgsql security definer set search_path=public as $$
begin
  if final_status not in ('rejected','ended','missed') then
    raise exception 'Invalid final call status';
  end if;
  update public.orra_call_sessions
    set status=final_status, updated_at=now(), ended_at=now()
  where id=target_call and (caller_id=auth.uid() or callee_id=auth.uid())
    and status in ('ringing','accepted');
  if not found then raise exception 'Call not found or already finished'; end if;
end;
$$;
grant execute on function public.orra_finish_call(uuid,text) to authenticated;


-- =====================================================================
-- orra_reliability_v4.sql
-- =====================================================================
-- ORRA reliability upgrade v4.
-- Run after orra_chat_schema.sql, orra_features_v2.sql, orra_presence_v3.sql and orra_internet_calls_v1.sql.

-- Delivery and read receipts for every recipient (DMs and groups).
create table if not exists public.message_receipts (
  message_id uuid not null references public.messages(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  delivered_at timestamptz,
  read_at timestamptz,
  primary key (message_id,user_id)
);
create index if not exists message_receipts_user_read_idx
  on public.message_receipts(user_id,read_at);

create or replace function public.orra_create_message_receipts()
returns trigger language plpgsql security definer set search_path=public as $$
begin
  insert into public.message_receipts(message_id,user_id)
  select new.id, cm.user_id
  from public.conversation_members cm
  where cm.conversation_id=new.conversation_id and cm.user_id<>new.sender_id
  on conflict do nothing;
  return new;
end;
$$;

drop trigger if exists orra_message_receipts_after_insert on public.messages;
create trigger orra_message_receipts_after_insert
after insert on public.messages
for each row execute function public.orra_create_message_receipts();

alter table public.message_receipts enable row level security;
drop policy if exists "Conversation members view message receipts" on public.message_receipts;
create policy "Conversation members view message receipts" on public.message_receipts
for select to authenticated using (
  exists (select 1 from public.messages m
    where m.id=message_id and public.is_orra_conversation_member(m.conversation_id))
);
drop policy if exists "Recipients update their own receipts" on public.message_receipts;
create policy "Recipients update their own receipts" on public.message_receipts
for update to authenticated using (user_id=auth.uid())
with check (user_id=auth.uid());
grant select on public.message_receipts to authenticated;
grant update (delivered_at,read_at) on public.message_receipts to authenticated;

create or replace function public.orra_mark_conversation_delivered(target_conversation uuid)
returns void language plpgsql security definer set search_path=public as $$
begin
  if not public.is_orra_conversation_member(target_conversation) then
    raise exception 'You are not a member of this conversation';
  end if;
  update public.message_receipts r
    set delivered_at=coalesce(r.delivered_at,now())
  from public.messages m
  where r.message_id=m.id and r.user_id=auth.uid()
    and m.conversation_id=target_conversation and m.sender_id<>auth.uid()
    and r.delivered_at is null;
end;
$$;

create or replace function public.orra_mark_conversation_read(target_conversation uuid)
returns void language plpgsql security definer set search_path=public as $$
begin
  if not public.is_orra_conversation_member(target_conversation) then
    raise exception 'You are not a member of this conversation';
  end if;
  update public.message_receipts r
    set delivered_at=coalesce(r.delivered_at,now()), read_at=coalesce(r.read_at,now())
  from public.messages m
  where r.message_id=m.id and r.user_id=auth.uid()
    and m.conversation_id=target_conversation and m.sender_id<>auth.uid()
    and r.read_at is null;
end;
$$;
grant execute on function public.orra_mark_conversation_delivered(uuid) to authenticated;
grant execute on function public.orra_mark_conversation_read(uuid) to authenticated;

-- Remove default PUBLIC/anon access from internal SECURITY DEFINER helpers.
revoke all on function public.handle_new_orra_user() from public, anon, authenticated;
revoke all on function public.create_or_get_conversation(uuid) from public, anon;
revoke all on function public.is_orra_conversation_member(uuid) from public, anon;
grant execute on function public.is_orra_conversation_member(uuid) to authenticated;
revoke all on function public.orra_create_message_receipts() from public, anon, authenticated;


-- Permit common office and text document formats in the private attachment bucket.
update storage.buckets
set allowed_mime_types = array[
  'image/jpeg','image/png','image/webp',
  'audio/wav','audio/mpeg','audio/mp4','audio/3gpp',
  'video/mp4','application/pdf','text/plain','text/csv','application/zip',
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'application/vnd.ms-excel',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'application/vnd.ms-powerpoint',
  'application/vnd.openxmlformats-officedocument.presentationml.presentation'
]
where id='orra-chat-media';

-- Only the intended recipient may accept a ringing call; call participants cannot rewrite identities.
revoke update on public.orra_call_sessions from authenticated;
drop policy if exists "Participants update ORRA calls" on public.orra_call_sessions;
create or replace function public.orra_accept_call(target_call uuid)
returns void language plpgsql security definer set search_path=public as $$
begin
  update public.orra_call_sessions
    set status='accepted',updated_at=now()
  where id=target_call and callee_id=auth.uid() and status='ringing';
  if not found then raise exception 'Call is no longer available'; end if;
end;
$$;
grant execute on function public.orra_accept_call(uuid) to authenticated;

drop policy if exists "Participants send ORRA call signals" on public.orra_call_signals;
create policy "Participants send ORRA call signals" on public.orra_call_signals
for insert to authenticated with check (
  sender_id=auth.uid() and exists (
    select 1 from public.orra_call_sessions c
    where c.id=call_id and c.status in ('ringing','accepted')
      and (
        (signal_type='offer' and c.caller_id=auth.uid())
        or (signal_type='answer' and c.callee_id=auth.uid())
        or (signal_type in ('ice','hangup') and (c.caller_id=auth.uid() or c.callee_id=auth.uid()))
      )
  )
);


-- =====================================================================
-- orra_security_v5.sql
-- =====================================================================
-- ORRA security hardening v5.
-- Restrict every exposed SECURITY DEFINER RPC to signed-in users only.
revoke all on function public.handle_new_orra_user() from public, anon, authenticated;
revoke all on function public.orra_create_message_receipts() from public, anon, authenticated;

revoke all on function public.create_or_get_conversation(uuid) from public, anon;
revoke all on function public.is_orra_conversation_member(uuid) from public, anon;
revoke all on function public.create_orra_group(text, uuid[]) from public, anon;
revoke all on function public.orra_add_group_member(uuid, uuid) from public, anon;
revoke all on function public.orra_leave_group(uuid) from public, anon;
revoke all on function public.orra_delete_conversation_for_me(uuid) from public, anon;
revoke all on function public.orra_users_blocked(uuid, uuid) from public, anon;
revoke all on function public.orra_delete_message_for_everyone(uuid) from public, anon;
revoke all on function public.orra_edit_message(uuid, text) from public, anon;
revoke all on function public.orra_finish_call(uuid, text) from public, anon;
revoke all on function public.orra_accept_call(uuid) from public, anon;
revoke all on function public.orra_mark_conversation_delivered(uuid) from public, anon;
revoke all on function public.orra_mark_conversation_read(uuid) from public, anon;

grant execute on function public.create_or_get_conversation(uuid) to authenticated;
grant execute on function public.is_orra_conversation_member(uuid) to authenticated;
grant execute on function public.create_orra_group(text, uuid[]) to authenticated;
grant execute on function public.orra_add_group_member(uuid, uuid) to authenticated;
grant execute on function public.orra_leave_group(uuid) to authenticated;
grant execute on function public.orra_delete_conversation_for_me(uuid) to authenticated;
grant execute on function public.orra_users_blocked(uuid, uuid) to authenticated;
grant execute on function public.orra_delete_message_for_everyone(uuid) to authenticated;
grant execute on function public.orra_edit_message(uuid, text) to authenticated;
grant execute on function public.orra_finish_call(uuid, text) to authenticated;
grant execute on function public.orra_accept_call(uuid) to authenticated;
grant execute on function public.orra_mark_conversation_delivered(uuid) to authenticated;
grant execute on function public.orra_mark_conversation_read(uuid) to authenticated;

-- Prevent the block-check helper from being used to probe relationships between two other accounts.
create or replace function public.orra_users_blocked(first_user uuid, second_user uuid)
returns boolean language sql stable security definer set search_path=public as $$
  select case
    when auth.uid() is null then false
    when first_user <> auth.uid() and second_user <> auth.uid() then false
    else exists (
      select 1 from public.blocked_users
      where (blocker_id=first_user and blocked_id=second_user)
         or (blocker_id=second_user and blocked_id=first_user)
    )
  end;
$$;
revoke all on function public.orra_users_blocked(uuid, uuid) from public, anon;
grant execute on function public.orra_users_blocked(uuid, uuid) to authenticated;

-- Index foreign keys used for lookups and joins.
create index if not exists blocked_users_blocked_id_idx on public.blocked_users(blocked_id);
create index if not exists conversation_settings_user_id_idx on public.conversation_settings(user_id);
create index if not exists conversations_created_by_idx on public.conversations(created_by);
create index if not exists message_deletions_user_id_idx on public.message_deletions(user_id);
create index if not exists message_events_message_id_idx on public.message_events(message_id);
create index if not exists message_events_actor_id_idx on public.message_events(actor_id);
create index if not exists messages_reply_to_idx on public.messages(reply_to);
create index if not exists messages_sender_id_idx on public.messages(sender_id);
create index if not exists orra_call_signals_sender_id_idx on public.orra_call_signals(sender_id);
create index if not exists user_presence_typing_conversation_idx on public.user_presence(typing_conversation);


-- =====================================================================
-- orra_attachments_v6.sql
-- =====================================================================
-- ORRA attachment compatibility v6.
-- Keep chat files private and size-limited while allowing generic files selected by Android document providers.
update storage.buckets
set allowed_mime_types = array[
  'image/jpeg','image/png','image/webp',
  'audio/wav','audio/mpeg','audio/mp4','audio/3gpp',
  'video/mp4','application/pdf','text/plain','text/csv','application/zip',
  'application/octet-stream',
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'application/vnd.ms-excel',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'application/vnd.ms-powerpoint',
  'application/vnd.openxmlformats-officedocument.presentationml.presentation'
]
where id='orra-chat-media' and public=false;


-- =====================================================================
-- orra_direct_messages_v7.sql
-- =====================================================================
-- ORRA direct conversation correctness v7.
-- Never reuse a group conversation as a one-to-one DM.
create or replace function public.create_or_get_conversation(other_user_id uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  current_user_id uuid := auth.uid();
  existing_id uuid;
  new_id uuid;
begin
  if current_user_id is null then
    raise exception 'Authentication required';
  end if;
  if other_user_id is null or other_user_id = current_user_id then
    raise exception 'Choose another user';
  end if;
  if not exists (select 1 from public.profiles where id = other_user_id) then
    raise exception 'User not found';
  end if;

  select first_member.conversation_id into existing_id
  from public.conversation_members first_member
  join public.conversation_members second_member
    on second_member.conversation_id = first_member.conversation_id
  join public.conversations c
    on c.id = first_member.conversation_id and c.is_group = false
  where first_member.user_id = current_user_id
    and second_member.user_id = other_user_id
  limit 1;

  if existing_id is not null then
    return existing_id;
  end if;

  insert into public.conversations(is_group,created_by)
  values (false,current_user_id)
  returning id into new_id;
  insert into public.conversation_members(conversation_id, user_id)
  values (new_id, current_user_id), (new_id, other_user_id);
  return new_id;
end;
$$;
revoke all on function public.create_or_get_conversation(uuid) from public, anon;
grant execute on function public.create_or_get_conversation(uuid) to authenticated;


-- =====================================================================
-- orra_storage_upsert_v8.sql
-- =====================================================================
-- ORRA chat media upsert policy v8.
-- Supabase Storage upsert requires SELECT + INSERT + UPDATE; restrict updates to the uploader in a conversation they belong to.
drop policy if exists "ORRA owners update chat media" on storage.objects;
create policy "ORRA owners update chat media"
on storage.objects for update to authenticated
using (
  bucket_id='orra-chat-media'
  and (storage.foldername(name))[1]=(select auth.uid())::text
  and exists (
    select 1 from public.conversation_members cm
    where cm.conversation_id::text=(storage.foldername(objects.name))[2]
      and cm.user_id=(select auth.uid())
  )
)
with check (
  bucket_id='orra-chat-media'
  and (storage.foldername(name))[1]=(select auth.uid())::text
  and exists (
    select 1 from public.conversation_members cm
    where cm.conversation_id::text=(storage.foldername(objects.name))[2]
      and cm.user_id=(select auth.uid())
  )
);


-- =====================================================================
-- orra_account_backup_history_v10.sql
-- =====================================================================
-- ORRA account, chat archive and deleted-message history v10.
create or replace function public.handle_new_orra_user()
returns trigger language plpgsql security definer set search_path=public as $$
declare requested_name text;
begin
  requested_name := lower(coalesce(
    nullif(new.raw_user_meta_data->>'username',''),
    nullif(split_part(coalesce(new.email,''),'@',1),''),
    'user_' || substr(new.id::text,1,8)
  ));
  requested_name := regexp_replace(requested_name,'[^a-z0-9_.]','','g');
  if length(coalesce(requested_name,'')) < 3 then requested_name := 'user_' || substr(new.id::text,1,8); end if;
  insert into public.profiles(id,username,phone_number,phone_verified)
  values(new.id,requested_name,case when new.phone_confirmed_at is not null then new.phone else null end,new.phone_confirmed_at is not null) on conflict do nothing;
  if not exists(select 1 from public.profiles where id=new.id) then
    insert into public.profiles(id,username,phone_number,phone_verified)
    values(new.id,requested_name || '_' || substr(new.id::text,1,6),nullif(new.phone,''),new.phone_confirmed_at is not null)
    on conflict(id) do nothing;
  end if;
  return new;
end;
$$;
revoke all on function public.handle_new_orra_user() from public,anon,authenticated;

create index if not exists profiles_phone_number_idx on public.profiles(phone_number) where phone_number is not null;

alter table public.message_events add column if not exists message_snapshot jsonb;
alter table public.message_events drop constraint if exists message_events_event_type_check;
alter table public.message_events add constraint message_events_event_type_check
  check(event_type in ('edited','deleted_for_everyone','deleted_for_me'));

drop policy if exists "Members read message event history" on public.message_events;
create policy "Members read message event history" on public.message_events
for select to authenticated using (
  exists(select 1 from public.messages m where m.id=message_id and public.is_orra_conversation_member(m.conversation_id))
  and (event_type <> 'deleted_for_me' or actor_id=auth.uid())
);

create or replace function public.orra_delete_message_for_everyone(target_message uuid)
returns void language plpgsql security definer set search_path=public as $$
declare old_message public.messages%rowtype;
begin
  select * into old_message from public.messages where id=target_message and sender_id=auth.uid() for update;
  if not found then raise exception 'Only the sender can delete this message for everyone'; end if;
  if old_message.deleted_for_everyone then return; end if;
  insert into public.message_events(message_id,actor_id,event_type,message_snapshot)
  values(target_message,auth.uid(),'deleted_for_everyone',
    jsonb_build_object('body',old_message.body,'message_type',old_message.message_type,
      'media_url',old_message.media_url,'created_at',old_message.created_at,'sender_id',old_message.sender_id));
  update public.messages set body='This message was deleted',deleted_for_everyone=true,media_url=null,message_type='text'
  where id=target_message;
end;
$$;
revoke all on function public.orra_delete_message_for_everyone(uuid) from public,anon;
grant execute on function public.orra_delete_message_for_everyone(uuid) to authenticated;

create or replace function public.orra_edit_message(target_message uuid,new_body text)
returns void language plpgsql security definer set search_path=public as $$
declare old_message public.messages%rowtype;
begin
  if coalesce(length(trim(new_body)),0)<1 or length(new_body)>4000 then raise exception 'Message text must contain 1 to 4000 characters'; end if;
  select * into old_message from public.messages where id=target_message and sender_id=auth.uid() and not deleted_for_everyone for update;
  if not found then raise exception 'Only the sender can edit this message'; end if;
  insert into public.message_events(message_id,actor_id,event_type,message_snapshot)
  values(target_message,auth.uid(),'edited',
    jsonb_build_object('body',old_message.body,'message_type',old_message.message_type,
      'media_url',old_message.media_url,'created_at',old_message.created_at,'sender_id',old_message.sender_id));
  update public.messages set body=trim(new_body),edited_at=now() where id=target_message;
end;
$$;
revoke all on function public.orra_edit_message(uuid,text) from public,anon;
grant execute on function public.orra_edit_message(uuid,text) to authenticated;

create or replace function public.orra_record_delete_for_me()
returns trigger language plpgsql security definer set search_path=public as $$
declare old_message public.messages%rowtype;
begin
  if new.user_id <> auth.uid() then raise exception 'Cannot delete another users message'; end if;
  select * into old_message from public.messages where id=new.message_id;
  if not found then raise exception 'Message not found'; end if;
  if not public.is_orra_conversation_member(old_message.conversation_id) then raise exception 'Not a conversation member'; end if;
  insert into public.message_events(message_id,actor_id,event_type,message_snapshot)
  values(new.message_id,new.user_id,'deleted_for_me',
    jsonb_build_object('body',old_message.body,'message_type',old_message.message_type,
      'media_url',old_message.media_url,'created_at',old_message.created_at,'sender_id',old_message.sender_id))
  on conflict do nothing;
  return new;
end;
$$;
drop trigger if exists orra_record_delete_for_me_after_insert on public.message_deletions;
create trigger orra_record_delete_for_me_after_insert after insert on public.message_deletions
for each row execute function public.orra_record_delete_for_me();
revoke all on function public.orra_record_delete_for_me() from public,anon,authenticated;

create table if not exists public.orra_chat_backups(
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references public.profiles(id) on delete cascade,
  snapshot jsonb not null,
  created_at timestamptz not null default now()
);
create index if not exists orra_chat_backups_owner_created_idx on public.orra_chat_backups(owner_id,created_at desc);
alter table public.orra_chat_backups enable row level security;
drop policy if exists "Users read own ORRA backups" on public.orra_chat_backups;
create policy "Users read own ORRA backups" on public.orra_chat_backups for select to authenticated using(owner_id=auth.uid());
drop policy if exists "Users create own ORRA backups" on public.orra_chat_backups;
create policy "Users create own ORRA backups" on public.orra_chat_backups for insert to authenticated with check(owner_id=auth.uid());
drop policy if exists "Users delete own ORRA backups" on public.orra_chat_backups;
create policy "Users delete own ORRA backups" on public.orra_chat_backups for delete to authenticated using(owner_id=auth.uid());
grant select,insert,delete on public.orra_chat_backups to authenticated;

create or replace function public.orra_create_chat_backup()
returns uuid language plpgsql security invoker set search_path=public as $$
declare backup_id uuid; caller uuid := auth.uid();
begin
  if caller is null then raise exception 'Authentication required'; end if;
  insert into public.orra_chat_backups(owner_id,snapshot)
  values(caller,jsonb_build_object(
    'format','orra-chat-backup-v1','created_at',now(),
    'conversations',coalesce((select jsonb_agg(to_jsonb(c) order by c.created_at)
      from public.conversations c join public.conversation_members cm on cm.conversation_id=c.id where cm.user_id=caller),'[]'::jsonb),
    'memberships',coalesce((select jsonb_agg(to_jsonb(cm)) from public.conversation_members cm
      where cm.conversation_id in (select cm2.conversation_id from public.conversation_members cm2 where cm2.user_id=caller)),'[]'::jsonb),
    'messages',coalesce((select jsonb_agg(to_jsonb(m) order by m.created_at)
      from public.messages m join public.conversation_members cm on cm.conversation_id=m.conversation_id where cm.user_id=caller),'[]'::jsonb),
    'message_events',coalesce((select jsonb_agg(to_jsonb(e) order by e.created_at)
      from public.message_events e join public.messages m on m.id=e.message_id
      join public.conversation_members cm on cm.conversation_id=m.conversation_id
      where cm.user_id=caller and (e.event_type <> 'deleted_for_me' or e.actor_id=caller)),'[]'::jsonb)
  )) returning id into backup_id;
  return backup_id;
end;
$$;
revoke all on function public.orra_create_chat_backup() from public,anon;
grant execute on function public.orra_create_chat_backup() to authenticated;

update storage.buckets set file_size_limit=104857600,allowed_mime_types=null
where id='orra-chat-media' and public=false;

-- Restore a selected snapshot owned by the caller. This SECURITY DEFINER RPC checks ownership
-- before restoring only conversations already present in that caller's backup.
create or replace function public.orra_restore_chat_backup(target_backup uuid)
returns integer
language plpgsql
security definer
set search_path=public
as $$
declare
  caller uuid := auth.uid();
  saved jsonb;
  restored_count integer := 0;
begin
  if caller is null then raise exception 'Authentication required'; end if;
  select snapshot into saved from public.orra_chat_backups
    where id=target_backup and owner_id=caller;
  if not found then raise exception 'Backup not found for this account'; end if;

  insert into public.conversations(id,title,is_group,created_by,created_at)
  select (v->>'id')::uuid,coalesce(v->>'title',''),coalesce((v->>'is_group')::boolean,false),
    nullif(v->>'created_by','')::uuid,coalesce((v->>'created_at')::timestamptz,now())
  from jsonb_array_elements(coalesce(saved->'conversations','[]'::jsonb)) v
  on conflict(id) do nothing;

  -- Restore only the backup owner's membership. Never silently re-add other people
  -- who may have intentionally left the group after this backup was created.
  insert into public.conversation_members(conversation_id,user_id,joined_at)
  select (v->>'id')::uuid,caller,now()
  from jsonb_array_elements(coalesce(saved->'conversations','[]'::jsonb)) v
  on conflict(conversation_id,user_id) do nothing;

  insert into public.messages(id,conversation_id,sender_id,body,created_at,message_type,media_url,edited_at,deleted_for_everyone,reply_to)
  select (v->>'id')::uuid,(v->>'conversation_id')::uuid,(v->>'sender_id')::uuid,
    coalesce(v->>'body',''),coalesce((v->>'created_at')::timestamptz,now()),
    coalesce(v->>'message_type','text'),nullif(v->>'media_url',''),
    nullif(v->>'edited_at','')::timestamptz,coalesce((v->>'deleted_for_everyone')::boolean,false),
    nullif(v->>'reply_to','')::uuid
  from jsonb_array_elements(coalesce(saved->'messages','[]'::jsonb)) v
  where exists (
    select 1 from jsonb_array_elements(coalesce(saved->'conversations','[]'::jsonb)) cv
    where cv->>'id'=v->>'conversation_id'
  )
  on conflict(id) do nothing;
  get diagnostics restored_count = row_count;

  insert into public.message_events(id,message_id,actor_id,event_type,created_at,message_snapshot)
  select (v->>'id')::uuid,(v->>'message_id')::uuid,(v->>'actor_id')::uuid,
    v->>'event_type',coalesce((v->>'created_at')::timestamptz,now()),v->'message_snapshot'
  from jsonb_array_elements(coalesce(saved->'message_events','[]'::jsonb)) v
  on conflict(id) do nothing;

  return restored_count;
end;
$$;
revoke all on function public.orra_restore_chat_backup(uuid) from public,anon;
grant execute on function public.orra_restore_chat_backup(uuid) to authenticated;
-- Only verified phone numbers are discoverable from the keypad.
alter table public.profiles add column if not exists phone_verified boolean not null default false;

create or replace function public.orra_sync_verified_phone()
returns trigger
language plpgsql
security definer
set search_path=public
as $$
begin
  if new.phone is not null then
    update public.profiles
      set phone_number=case when new.phone_confirmed_at is not null then new.phone else null end,
          phone_verified=(new.phone_confirmed_at is not null),
          updated_at=now()
    where id=new.id;
  end if;
  return new;
end;
$$;
drop trigger if exists orra_sync_verified_phone_after_auth_update on auth.users;
create trigger orra_sync_verified_phone_after_auth_update
after update of phone,phone_confirmed_at on auth.users
for each row execute function public.orra_sync_verified_phone();
revoke all on function public.orra_sync_verified_phone() from public,anon,authenticated;
-- Phone numbers are written only by the trusted Auth verification trigger.
update public.profiles set phone_number=null where not phone_verified;
revoke update(phone_number,phone_verified) on public.profiles from authenticated;

-- Cache auth.uid() once per statement in exposed RLS policies.
do $$
declare p record; using_clause text; check_clause text;
begin
  for p in
    select schemaname,tablename,policyname,qual,with_check
    from pg_policies
    where schemaname in ('public','storage')
      and (coalesce(qual,'') like '%auth.uid()%' or coalesce(with_check,'') like '%auth.uid()%')
  loop
    using_clause := case when p.qual is not null
      then ' using (' || replace(p.qual,'auth.uid()','(select auth.uid())') || ')' else '' end;
    check_clause := case when p.with_check is not null
      then ' with check (' || replace(p.with_check,'auth.uid()','(select auth.uid())') || ')' else '' end;
    execute format('alter policy %I on %I.%I%s%s',
      p.policyname,p.schemaname,p.tablename,using_clause,check_clause);
  end loop;
end $$;

-- Remove inherited broad grants and permit only the fields the app may edit.
revoke all on table public.orra_chat_backups from anon,public,authenticated;
grant select,insert,delete on table public.orra_chat_backups to authenticated;
revoke all on table public.profiles from anon,public,authenticated;
grant select on table public.profiles to authenticated;
grant update(display_name,bio,avatar_url,cover_url,updated_at) on table public.profiles to authenticated;


-- =====================================================================
-- orra_sessions_push_v11.sql
-- =====================================================================
-- ORRA v11: device sessions (Devices screen) + push tokens (FCM).
-- Run in Supabase SQL Editor after orra_account_backup_history_v10.sql.

-- ===================== Device sessions =====================
create table if not exists public.device_sessions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  device_id text not null,
  device_name text not null default 'Unknown device',
  os_version text,
  app_version text,
  created_at timestamptz not null default now(),
  last_active timestamptz not null default now(),
  revoked boolean not null default false,
  unique (user_id, device_id)
);
create index if not exists device_sessions_user_idx on public.device_sessions(user_id, last_active desc);
alter table public.device_sessions enable row level security;

drop policy if exists "sessions_select_own" on public.device_sessions;
create policy "sessions_select_own" on public.device_sessions
  for select to authenticated using (auth.uid() = user_id);
grant select on public.device_sessions to authenticated;
-- Writes happen only through the functions below.

-- p_reactivate = true only right after a fresh sign-in on that device.
create or replace function public.register_session(
  p_device_id text, p_device_name text, p_os_version text, p_app_version text,
  p_reactivate boolean default false
) returns public.device_sessions
language plpgsql security definer set search_path = public as $$
declare r public.device_sessions;
begin
  if auth.uid() is null then raise exception 'not authenticated'; end if;
  insert into public.device_sessions(user_id, device_id, device_name, os_version, app_version)
  values (auth.uid(), p_device_id, coalesce(nullif(p_device_name,''),'Unknown device'), p_os_version, p_app_version)
  on conflict (user_id, device_id) do update
    set device_name = excluded.device_name,
        os_version  = excluded.os_version,
        app_version = excluded.app_version,
        last_active = now(),
        revoked     = case when p_reactivate then false else public.device_sessions.revoked end,
        created_at  = case when p_reactivate then now() else public.device_sessions.created_at end
  returning * into r;
  return r;
end $$;

create or replace function public.is_session_active(p_device_id text)
returns boolean language sql security definer set search_path = public stable as $$
  select exists (select 1 from public.device_sessions
                 where user_id = auth.uid() and device_id = p_device_id and revoked = false);
$$;

create or replace function public.terminate_session(p_session_id uuid)
returns void language sql security definer set search_path = public as $$
  update public.device_sessions set revoked = true
  where id = p_session_id and user_id = auth.uid();
$$;

create or replace function public.terminate_other_sessions(p_current_device_id text)
returns void language sql security definer set search_path = public as $$
  update public.device_sessions set revoked = true
  where user_id = auth.uid() and device_id <> p_current_device_id;
$$;

revoke all on function public.register_session(text,text,text,text,boolean) from public, anon;
revoke all on function public.is_session_active(text) from public, anon;
revoke all on function public.terminate_session(uuid) from public, anon;
revoke all on function public.terminate_other_sessions(text) from public, anon;
grant execute on function public.register_session(text,text,text,text,boolean) to authenticated;
grant execute on function public.is_session_active(text) to authenticated;
grant execute on function public.terminate_session(uuid) to authenticated;
grant execute on function public.terminate_other_sessions(text) to authenticated;

-- ===================== Push tokens (FCM) =====================
create table if not exists public.push_tokens (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  device_id text not null,
  fcm_token text not null,
  platform text not null default 'android',
  updated_at timestamptz not null default now(),
  unique (user_id, device_id)
);
alter table public.push_tokens enable row level security;
-- No client policies: tokens are written only through save_push_token().

create or replace function public.save_push_token(p_device_id text, p_token text)
returns void language sql security definer set search_path = public as $$
  insert into public.push_tokens(user_id, device_id, fcm_token)
  values (auth.uid(), p_device_id, p_token)
  on conflict (user_id, device_id) do update
    set fcm_token = excluded.fcm_token, updated_at = now();
$$;
revoke all on function public.save_push_token(text,text) from public, anon;
grant execute on function public.save_push_token(text,text) to authenticated;

-- Targets for a new chat message: members except the sender, not muted, session not revoked.
create or replace function public.orra_push_targets_for_conversation(p_conversation uuid, p_sender uuid)
returns table(user_id uuid, fcm_token text, device_id text)
language sql security definer set search_path = public stable as $$
  select t.user_id, t.fcm_token, t.device_id
  from public.conversation_members m
  join public.push_tokens t on t.user_id = m.user_id
  left join public.conversation_settings s
    on s.conversation_id = m.conversation_id and s.user_id = m.user_id
  where m.conversation_id = p_conversation
    and m.user_id <> p_sender
    and (s.muted_until is null or s.muted_until <= now())
    and not exists (select 1 from public.device_sessions d
                    where d.user_id = t.user_id and d.device_id = t.device_id and d.revoked);
$$;

create or replace function public.orra_push_targets_for_user(p_user uuid)
returns table(user_id uuid, fcm_token text, device_id text)
language sql security definer set search_path = public stable as $$
  select t.user_id, t.fcm_token, t.device_id
  from public.push_tokens t
  where t.user_id = p_user
    and not exists (select 1 from public.device_sessions d
                    where d.user_id = t.user_id and d.device_id = t.device_id and d.revoked);
$$;

revoke all on function public.orra_push_targets_for_conversation(uuid,uuid) from public, anon, authenticated;
revoke all on function public.orra_push_targets_for_user(uuid) from public, anon, authenticated;
grant execute on function public.orra_push_targets_for_conversation(uuid,uuid) to service_role;
grant execute on function public.orra_push_targets_for_user(uuid) to service_role;

-- After deploying the orra-push-send Edge Function, create two Database Webhooks
-- (Dashboard > Database > Webhooks): table public.messages (INSERT) and
-- table public.orra_call_sessions (INSERT), both POST to
--   https://<project-ref>.supabase.co/functions/v1/orra-push-send
-- with HTTP header  x-orra-push-secret: <same value as the ORRA_PUSH_SECRET secret>.

-- ===================== Photos and videos that open once or twice =====================
-- The app marks limited media by prefixing the message body with "orra:viewN:".
create table if not exists public.message_views (
  message_id uuid not null references public.messages(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  views integer not null default 0,
  primary key (message_id, user_id)
);
alter table public.message_views enable row level security;
drop policy if exists "message_views_select_own" on public.message_views;
create policy "message_views_select_own" on public.message_views
  for select to authenticated using (auth.uid() = user_id);
grant select on public.message_views to authenticated;

-- Returns -1 (unlimited), -2 (no views left) or the number of views remaining.
create or replace function public.consume_media_view(p_message uuid)
returns integer language plpgsql security definer set search_path = public as $$
declare
  v_body text; v_conv uuid; v_sender uuid; v_limit integer := 0; v_views integer;
begin
  select body, conversation_id, sender_id into v_body, v_conv, v_sender from public.messages where id = p_message;
  if v_conv is null or not exists (
    select 1 from public.conversation_members where conversation_id = v_conv and user_id = auth.uid()
  ) then raise exception 'not allowed'; end if;
  v_limit := coalesce(nullif(substring(v_body from '^orra:view([0-9]+):'), '')::integer, 0);
  if v_limit = 0 or v_sender = auth.uid() then return -1; end if;
  insert into public.message_views(message_id, user_id, views) values (p_message, auth.uid(), 1)
    on conflict (message_id, user_id) do update set views = public.message_views.views + 1
    where public.message_views.views < v_limit
    returning views into v_views;
  if v_views is null then return -2; end if;
  return v_limit - v_views;
end $$;
revoke all on function public.consume_media_view(uuid) from public, anon;
grant execute on function public.consume_media_view(uuid) to authenticated;


-- =====================================================================
-- orra_fix_v12.sql  (safe to run more than once)
-- =====================================================================
-- Receipts for messages sent before the receipts trigger existed (so ticks work on old messages).
insert into public.message_receipts(message_id, user_id)
select m.id, cm.user_id
from public.messages m
join public.conversation_members cm on cm.conversation_id = m.conversation_id
where cm.user_id <> m.sender_id
on conflict do nothing;

-- Make sure the profile avatar/cover bucket accepts the common image content types.
update storage.buckets
set allowed_mime_types = array['image/jpeg','image/jpg','image/png','image/webp','application/octet-stream']
where id = 'orra-profiles';

notify pgrst, 'reload schema';
