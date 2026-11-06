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
  values(new.id,requested_name,nullif(new.phone,''),new.phone_confirmed_at is not null) on conflict do nothing;
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
      set phone_number=new.phone,
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
