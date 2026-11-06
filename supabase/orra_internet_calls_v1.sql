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
