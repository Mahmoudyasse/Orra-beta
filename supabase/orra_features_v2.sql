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
