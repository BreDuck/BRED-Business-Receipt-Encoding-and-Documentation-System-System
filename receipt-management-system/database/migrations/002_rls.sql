-- RMS 002: helper functions, Row Level Security, grants.
create or replace function is_active_user() returns boolean
language sql stable security definer set search_path = public as
$$ select exists (select 1 from profiles where id = auth.uid() and is_active) $$;

create or replace function is_admin() returns boolean
language sql stable security definer set search_path = public as
$$ select exists (select 1 from profiles where id = auth.uid() and is_active and role = 'admin') $$;

alter table profiles enable row level security;
alter table suppliers enable row level security;
alter table projects enable row level security;
alter table receipts enable row level security;
alter table receipt_items enable row level security;
alter table receipt_attachments enable row level security;
alter table activity_log enable row level security;

-- A user can always read their own profile (so login can say "inactive"); active users read everyone.
create policy profiles_read on profiles for select to authenticated using (id = auth.uid() or is_active_user());

create policy suppliers_read on suppliers for select to authenticated using (is_active_user());
create policy suppliers_insert on suppliers for insert to authenticated with check (is_active_user());
create policy suppliers_update on suppliers for update to authenticated using (is_active_user()) with check (is_active_user());
create policy suppliers_delete on suppliers for delete to authenticated using (is_admin());

create policy projects_read on projects for select to authenticated using (is_active_user());
create policy projects_insert on projects for insert to authenticated with check (is_active_user());
create policy projects_update on projects for update to authenticated using (is_active_user()) with check (is_active_user());
create policy projects_delete on projects for delete to authenticated using (is_admin());

-- Receipts and their children are written ONLY through the save_receipt / delete_receipt RPCs.
create policy receipts_read on receipts for select to authenticated using (is_active_user());
create policy receipt_items_read on receipt_items for select to authenticated using (is_active_user());
create policy receipt_attachments_read on receipt_attachments for select to authenticated using (is_active_user());

create policy activity_log_read on activity_log for select to authenticated using (is_admin());

revoke all on all tables in schema public from anon, authenticated;
grant select on profiles, receipts, receipt_items, receipt_attachments, activity_log to authenticated;
grant select, insert, update, delete on suppliers, projects to authenticated;

-- Server-side administrative operations (the Edge Function) use the
-- service_role key. Keep direct profile writes disabled for authenticated
-- users, while allowing the server function to create profiles and audit rows.
grant usage on schema public to service_role;
grant all on all tables in schema public to service_role;
grant usage, select on all sequences in schema public to service_role;
