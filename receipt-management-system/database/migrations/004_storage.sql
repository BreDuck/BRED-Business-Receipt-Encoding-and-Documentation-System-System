-- RMS 004: private Storage bucket for receipt scans.
insert into storage.buckets (id, name, public) values ('receipts', 'receipts', false) on conflict (id) do nothing;

drop policy if exists receipts_files_read on storage.objects;
drop policy if exists receipts_files_insert on storage.objects;
drop policy if exists receipts_files_delete on storage.objects;

create policy receipts_files_read on storage.objects for select to authenticated
  using (bucket_id = 'receipts' and public.is_active_user());
create policy receipts_files_insert on storage.objects for insert to authenticated
  with check (bucket_id = 'receipts' and public.is_active_user());
-- Only administrators remove stored files (done when a receipt is deleted).
create policy receipts_files_delete on storage.objects for delete to authenticated
  using (bucket_id = 'receipts' and public.is_admin());
