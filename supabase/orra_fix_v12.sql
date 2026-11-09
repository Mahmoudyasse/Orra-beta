
-- =====================================================================
-- orra_fix_v12.sql  (safe to run more than once)
-- =====================================================================
-- Receipts for messages sent before the receipts trigger existed (so ticks work on old messages).
insert into public.message_receipts(message_id, user_id)
select m.id, cm.user_id
from public.messages m
join public.conversation_members cm on cm.conversation_id = m.conversation_id
where cm.user_id <> m.sender_id
on conflict do nothing;

-- Make sure the profile avatar/cover bucket accepts the common image content types.
update storage.buckets
set allowed_mime_types = array['image/jpeg','image/jpg','image/png','image/webp','application/octet-stream']
where id = 'orra-profiles';

notify pgrst, 'reload schema';
