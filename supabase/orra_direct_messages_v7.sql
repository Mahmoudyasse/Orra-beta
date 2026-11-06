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
