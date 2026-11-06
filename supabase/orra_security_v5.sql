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
