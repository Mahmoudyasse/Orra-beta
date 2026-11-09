
-- =====================================================================
-- orra_reactions_delivery_v13.sql  (safe to run more than once)
-- =====================================================================
-- Emoji reactions: one reaction per person per message (sending the same emoji again removes it).
create table if not exists public.message_reactions (
  message_id uuid not null references public.messages(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  emoji text not null check (char_length(emoji) between 1 and 16),
  created_at timestamptz not null default now(),
  primary key (message_id, user_id)
);
create index if not exists message_reactions_user_idx on public.message_reactions(user_id);
alter table public.message_reactions enable row level security;
drop policy if exists "Members read message reactions" on public.message_reactions;
create policy "Members read message reactions" on public.message_reactions
for select to authenticated using (
  exists (select 1 from public.messages m
    where m.id = message_id and public.is_orra_conversation_member(m.conversation_id))
);
revoke all on table public.message_reactions from anon, public, authenticated;
grant select on table public.message_reactions to authenticated;

create or replace function public.orra_set_reaction(target_message uuid, new_emoji text)
returns void language plpgsql security definer set search_path = public as $$
declare caller uuid := auth.uid();
begin
  if caller is null then raise exception 'Authentication required'; end if;
  if not exists (select 1 from public.messages m
      where m.id = target_message and public.is_orra_conversation_member(m.conversation_id)) then
    raise exception 'You are not a member of this conversation';
  end if;
  if new_emoji is null or length(trim(new_emoji)) = 0 or exists (
      select 1 from public.message_reactions r
      where r.message_id = target_message and r.user_id = caller and r.emoji = new_emoji) then
    delete from public.message_reactions where message_id = target_message and user_id = caller;
  else
    insert into public.message_reactions(message_id, user_id, emoji)
    values (target_message, caller, left(new_emoji, 16))
    on conflict (message_id, user_id) do update set emoji = excluded.emoji, created_at = now();
  end if;
end;
$$;
revoke all on function public.orra_set_reaction(uuid, text) from public, anon;
grant execute on function public.orra_set_reaction(uuid, text) to authenticated;

-- Delivered tick: every message addressed to me becomes "delivered" as soon as I am online in the app.
create or replace function public.orra_mark_all_delivered()
returns void language sql security definer set search_path = public as $$
  update public.message_receipts
     set delivered_at = coalesce(delivered_at, now())
   where user_id = auth.uid() and delivered_at is null;
$$;
revoke all on function public.orra_mark_all_delivered() from public, anon;
grant execute on function public.orra_mark_all_delivered() to authenticated;

notify pgrst, 'reload schema';
