-- ORRA attachment compatibility v6.
-- Keep chat files private and size-limited while allowing generic files selected by Android document providers.
update storage.buckets
set allowed_mime_types = array[
  'image/jpeg','image/png','image/webp',
  'audio/wav','audio/mpeg','audio/mp4','audio/3gpp',
  'video/mp4','application/pdf','text/plain','text/csv','application/zip',
  'application/octet-stream',
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'application/vnd.ms-excel',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'application/vnd.ms-powerpoint',
  'application/vnd.openxmlformats-officedocument.presentationml.presentation'
]
where id='orra-chat-media' and public=false;
