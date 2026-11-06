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
    and m.conversation_id=target_conversation and m.sender_id<>auth.uid();
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
    and m.conversation_id=target_conversation and m.sender_id<>auth.uid();
end;
$$;
grant execute on function public.orra_mark_conversation_delivered(uuid) to authenticated;
grant execute on function public.orra_mark_conversation_read(uuid) to authenticated;

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
