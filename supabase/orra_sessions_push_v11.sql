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
