-- ORRA chat media upsert policy v8.
-- Supabase Storage upsert requires SELECT + INSERT + UPDATE; restrict updates to the uploader in a conversation they belong to.
drop policy if exists "ORRA owners update chat media" on storage.objects;
create policy "ORRA owners update chat media"
on storage.objects for update to authenticated
using (
  bucket_id='orra-chat-media'
  and (storage.foldername(name))[1]=(select auth.uid())::text
  and exists (
    select 1 from public.conversation_members cm
    where cm.conversation_id::text=(storage.foldername(objects.name))[2]
      and cm.user_id=(select auth.uid())
  )
)
with check (
  bucket_id='orra-chat-media'
  and (storage.foldername(name))[1]=(select auth.uid())::text
  and exists (
    select 1 from public.conversation_members cm
    where cm.conversation_id::text=(storage.foldername(objects.name))[2]
      and cm.user_id=(select auth.uid())
  )
);
