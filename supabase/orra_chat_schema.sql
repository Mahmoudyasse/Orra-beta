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
  on conflict (id) do nothing;

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
