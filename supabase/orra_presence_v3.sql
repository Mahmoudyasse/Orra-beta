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
