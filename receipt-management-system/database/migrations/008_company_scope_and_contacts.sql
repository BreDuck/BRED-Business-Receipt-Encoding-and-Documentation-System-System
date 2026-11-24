-- RMS 008: company-scoped data and private bug reports.

do $$
declare
  v_company_id uuid;
begin
  select id into v_company_id from companies where name = 'Legacy Company' order by id limit 1;
  if v_company_id is null then
    insert into companies (id, name) values (gen_random_uuid(), 'Legacy Company') returning id into v_company_id;
  end if;

  alter table profiles add column if not exists company_id uuid;
  alter table suppliers add column if not exists company_id uuid;
  alter table projects add column if not exists company_id uuid;
  alter table receipts add column if not exists company_id uuid;
  alter table activity_log add column if not exists company_id uuid;

  update profiles set company_id = v_company_id where company_id is null;
  update suppliers set company_id = v_company_id where company_id is null;
  update projects set company_id = v_company_id where company_id is null;
  update receipts set company_id = v_company_id where company_id is null;
  update activity_log set company_id = v_company_id where company_id is null;
end $$;

create or replace function current_company_id() returns uuid
language sql stable security definer set search_path = public as
$$ select company_id from profiles
   where id = auth.uid() and is_active and account_status = 'APPROVED' $$;

create or replace function is_creator() returns boolean
language sql stable security definer set search_path = public as
$$ select exists (
  select 1 from profiles
  where id = auth.uid() and is_active and account_status = 'APPROVED' and role = 'creator'
) $$;

create or replace function is_admin() returns boolean
language sql stable security definer set search_path = public as
$$ select exists (
  select 1 from profiles
  where id = auth.uid() and is_active and account_status = 'APPROVED' and role in ('admin', 'creator')
) $$;

do $$
declare v_policy record;
begin
  for v_policy in
    select tablename, policyname from pg_policies
    where schemaname = 'public'
      and tablename = any(array[
        'companies', 'profiles', 'suppliers', 'projects', 'receipts',
        'receipt_items', 'receipt_attachments', 'activity_log'
      ])
  loop
    execute format('drop policy %I on public.%I', v_policy.policyname, v_policy.tablename);
  end loop;
end $$;

alter table profiles alter column company_id set not null;
alter table suppliers alter column company_id set default current_company_id();
alter table suppliers alter column company_id set not null;
alter table projects alter column company_id set default current_company_id();
alter table projects alter column company_id set not null;
alter table receipts alter column company_id set default current_company_id();
alter table receipts alter column company_id set not null;
alter table activity_log alter column company_id set default current_company_id();

do $$
begin
  alter table profiles drop constraint if exists profiles_company_id_fkey;
  alter table profiles add constraint profiles_company_id_fkey
    foreign key (company_id) references companies(id) on delete restrict;
  if not exists (select 1 from pg_constraint where conname = 'suppliers_company_id_fkey') then
    alter table suppliers add constraint suppliers_company_id_fkey
      foreign key (company_id) references companies(id) on delete restrict;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'projects_company_id_fkey') then
    alter table projects add constraint projects_company_id_fkey
      foreign key (company_id) references companies(id) on delete restrict;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'receipts_company_id_fkey') then
    alter table receipts add constraint receipts_company_id_fkey
      foreign key (company_id) references companies(id) on delete restrict;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'activity_log_company_id_fkey') then
    alter table activity_log add constraint activity_log_company_id_fkey
      foreign key (company_id) references companies(id) on delete set null;
  end if;
end $$;

drop index if exists suppliers_tin_key;
create unique index if not exists suppliers_company_tin_key
  on suppliers (company_id, lower(btrim(tin)));
drop index if exists projects_code_key;
create unique index if not exists projects_company_code_key
  on projects (company_id, lower(btrim(code)));
create index if not exists receipts_company_date_idx on receipts (company_id, receipt_date desc);
create index if not exists profiles_company_idx on profiles (company_id);
create index if not exists activity_log_company_at_idx on activity_log (company_id, at desc);

create or replace function log_activity(p_action text, p_detail text) returns void
language plpgsql security definer set search_path = public as $$
begin
  insert into activity_log (user_id, user_name, company_id, action, detail)
  select auth.uid(), full_name, company_id, p_action, coalesce(p_detail, '')
  from profiles where id = auth.uid();
end $$;

create or replace function trg_log_master_data() returns trigger
language plpgsql security definer set search_path = public as $$
declare
  v_row jsonb := to_jsonb(case when tg_op = 'DELETE' then old else new end);
  v_company_id uuid := (v_row->>'company_id')::uuid;
begin
  insert into activity_log (user_id, user_name, company_id, action, detail)
  select auth.uid(), p.full_name, v_company_id, tg_table_name || '.' || lower(tg_op),
    coalesce(v_row->>'business_name', v_row->>'name') || ' ('
      || coalesce(v_row->>'tin', v_row->>'code') || ')'
  from profiles p where p.id = auth.uid();
  if tg_op = 'DELETE' then return old; end if;
  return new;
end $$;

drop policy if exists companies_read on companies;
drop policy if exists companies_select on companies;
create policy companies_select on companies for select to authenticated
  using (is_active_user() and (is_creator() or id = current_company_id()));
alter table companies enable row level security;
grant select on companies to authenticated;

drop policy if exists profiles_read on profiles;
drop policy if exists profiles_read_own_or_admin on profiles;
drop policy if exists profiles_read_own_or_admin on profiles;
create policy profiles_read_own_or_admin on profiles for select to authenticated
  using (id = auth.uid() or is_creator()
    or (is_admin() and company_id = current_company_id()));

drop policy if exists suppliers_read on suppliers;
drop policy if exists suppliers_insert on suppliers;
drop policy if exists suppliers_update on suppliers;
drop policy if exists suppliers_delete on suppliers;
create policy suppliers_read on suppliers for select to authenticated
  using (is_active_user() and (is_creator() or company_id = current_company_id()));
create policy suppliers_insert on suppliers for insert to authenticated
  with check (is_active_user() and company_id = current_company_id());
create policy suppliers_update on suppliers for update to authenticated
  using (is_active_user() and (is_creator() or company_id = current_company_id()))
  with check (is_active_user() and (is_creator() or company_id = current_company_id()));
create policy suppliers_delete on suppliers for delete to authenticated
  using (is_admin() and (is_creator() or company_id = current_company_id()));

drop policy if exists projects_read on projects;
drop policy if exists projects_insert on projects;
drop policy if exists projects_update on projects;
drop policy if exists projects_delete on projects;
create policy projects_read on projects for select to authenticated
  using (is_active_user() and (is_creator() or company_id = current_company_id()));
create policy projects_insert on projects for insert to authenticated
  with check (is_active_user() and company_id = current_company_id());
create policy projects_update on projects for update to authenticated
  using (is_active_user() and (is_creator() or company_id = current_company_id()))
  with check (is_active_user() and (is_creator() or company_id = current_company_id()));
create policy projects_delete on projects for delete to authenticated
  using (is_admin() and (is_creator() or company_id = current_company_id()));

drop policy if exists receipts_read on receipts;
create policy receipts_read on receipts for select to authenticated
  using (is_active_user() and (is_creator() or company_id = current_company_id()));
drop policy if exists receipt_items_read on receipt_items;
create policy receipt_items_read on receipt_items for select to authenticated
  using (is_active_user() and exists (
    select 1 from receipts r where r.id = receipt_id
      and (is_creator() or r.company_id = current_company_id())
  ));
drop policy if exists receipt_attachments_read on receipt_attachments;
create policy receipt_attachments_read on receipt_attachments for select to authenticated
  using (is_active_user() and exists (
    select 1 from receipts r where r.id = receipt_id
      and (is_creator() or r.company_id = current_company_id())
  ));
drop policy if exists activity_log_read on activity_log;
create policy activity_log_read on activity_log for select to authenticated
  using (is_admin() and (is_creator() or company_id = current_company_id()));

create or replace function register_account_profile(p_id uuid, p_full_name text, p_email text, p_company_id uuid)
returns void
language plpgsql security definer set search_path = public as $$
begin
  if p_id is null or btrim(coalesce(p_full_name, '')) = '' or btrim(coalesce(p_email, '')) = '' then
    raise exception 'Registration details are required.';
  end if;
  if p_company_id is null or not exists (select 1 from companies where id = p_company_id and is_active) then
    raise exception 'Choose an available company.';
  end if;
  insert into profiles (id, full_name, email, role, is_active, account_status, company_id)
  values (p_id, btrim(p_full_name), lower(btrim(p_email)), 'encoder', false, 'PENDING', p_company_id);
  insert into activity_log (user_id, user_name, company_id, action, detail)
  values (p_id, btrim(p_full_name), p_company_id, 'account.register', lower(btrim(p_email)));
end $$;

create or replace function register_company_admin_profile(p_id uuid, p_full_name text, p_email text, p_company_name text)
returns uuid
language plpgsql security definer set search_path = public as $$
declare v_company_id uuid;
begin
  if p_id is null or btrim(coalesce(p_full_name, '')) = '' or btrim(coalesce(p_email, '')) = ''
     or btrim(coalesce(p_company_name, '')) = '' then
    raise exception 'Registration details and company name are required.';
  end if;
  insert into companies (id, name, is_active)
  values (gen_random_uuid(), btrim(p_company_name), true) returning id into v_company_id;
  insert into profiles (id, full_name, email, role, is_active, account_status, company_id)
  values (p_id, btrim(p_full_name), lower(btrim(p_email)), 'admin', true, 'APPROVED', v_company_id);
  insert into activity_log (user_id, user_name, company_id, action, detail)
  values (p_id, btrim(p_full_name), v_company_id, 'company.create', btrim(p_company_name));
  return v_company_id;
end $$;

create or replace function review_account(p_admin_id uuid, p_target_id uuid, p_action text)
returns void
language plpgsql security definer set search_path = public as $$
declare v_admin profiles; v_target profiles; v_status text;
begin
  select * into v_admin from profiles
    where id = p_admin_id and role in ('admin', 'creator') and is_active and account_status = 'APPROVED';
  if not found then raise exception 'Administrator approval required.'; end if;
  if p_admin_id = p_target_id then raise exception 'Administrators cannot review their own account.'; end if;
  if upper(coalesce(p_action, '')) not in ('APPROVE', 'REJECT') then raise exception 'Choose approve or reject.'; end if;
  select * into v_target from profiles where id = p_target_id for update;
  if not found then raise exception 'Registration not found.'; end if;
  if v_admin.role <> 'creator' and v_admin.company_id <> v_target.company_id then
    raise exception 'You cannot review another company''s registration.';
  end if;
  if v_target.account_status <> 'PENDING' then raise exception 'This registration is no longer pending.'; end if;
  v_status := case when upper(p_action) = 'APPROVE' then 'APPROVED' else 'REJECTED' end;
  update profiles set account_status = v_status, is_active = (v_status = 'APPROVED') where id = p_target_id;
  insert into activity_log (user_id, user_name, company_id, action, detail)
  values (p_admin_id, v_admin.full_name, v_admin.company_id,
    case when v_status = 'APPROVED' then 'account.approve' else 'account.reject' end,
    v_target.email || ' (' || v_target.id::text || ')');
end $$;

create or replace function admin_update_profile(p_id uuid, p_full_name text, p_role user_role, p_active boolean)
returns void
language plpgsql security definer set search_path = public as $$
declare v_actor profiles; v_target profiles;
begin
  select * into v_actor from profiles where id = auth.uid()
    and role in ('admin', 'creator') and is_active and account_status = 'APPROVED';
  if not found then raise exception 'Administrator role required.'; end if;
  if btrim(coalesce(p_full_name, '')) = '' then raise exception 'Full name is required.'; end if;
  if p_role = 'creator' and v_actor.role <> 'creator' then raise exception 'Only the Creator can assign the Creator role.'; end if;
  select * into v_target from profiles where id = p_id for update;
  if not found then raise exception 'User not found.'; end if;
  if v_actor.role <> 'creator' and v_actor.company_id <> v_target.company_id then
    raise exception 'You cannot manage another company''s users.';
  end if;
  if p_active and v_target.account_status <> 'APPROVED' then
    raise exception 'Only approved registrations can be activated.';
  end if;
  if v_target.role = 'admin' and v_target.is_active and (p_role <> 'admin' or not p_active)
     and (select count(*) from profiles where company_id = v_target.company_id
          and role = 'admin' and is_active and account_status = 'APPROVED') <= 1 then
    raise exception 'Cannot remove the last active administrator.';
  end if;
  if v_target.role = 'creator' and v_target.is_active and (p_role <> 'creator' or not p_active)
     and (select count(*) from profiles where role = 'creator' and is_active and account_status = 'APPROVED') <= 1 then
    raise exception 'Cannot remove the last active Creator.';
  end if;
  update profiles set full_name = btrim(p_full_name), role = p_role, is_active = p_active where id = p_id;
  insert into activity_log (user_id, user_name, company_id, action, detail)
  values (auth.uid(), v_actor.full_name, v_actor.company_id, 'user.update', v_target.email);
end $$;

-- Company context for receipts and their child rows is enforced inside the SECURITY DEFINER RPC.
create or replace function save_receipt(p jsonb) returns uuid
language plpgsql security definer set search_path = public as $$
declare
  v_id uuid := nullif(p->>'id', '')::uuid;
  v_company_id uuid;
  v_existing boolean;
  v_no text := btrim(coalesce(p->>'receipt_no', ''));
  v_supplier uuid := nullif(p->>'supplier_id', '')::uuid;
  v_project uuid := nullif(p->>'project_id', '')::uuid;
  v_discount numeric := coalesce((p->>'discount')::numeric, 0);
  v_vat numeric := coalesce((p->>'vat')::numeric, 0);
  v_charges numeric := coalesce((p->>'additional_charges')::numeric, 0);
  v_subtotal numeric := 0;
  v_item jsonb;
  v_att jsonb;
  v_line integer := 0;
begin
  if not is_active_user() then raise exception 'Not allowed.'; end if;
  if v_no = '' then raise exception 'Receipt number is required.'; end if;
  if p->>'receipt_date' is null then raise exception 'Receipt date is required.'; end if;
  if v_supplier is null then raise exception 'Choose a supplier.'; end if;

  v_existing := v_id is not null and exists (select 1 from receipts where id = v_id);
  if v_existing then
    select company_id into v_company_id from receipts where id = v_id;
    if not is_creator() and v_company_id <> current_company_id() then raise exception 'Not allowed.'; end if;
  else
    select company_id into v_company_id from suppliers where id = v_supplier;
    if v_company_id is null then raise exception 'Choose a supplier in your company.'; end if;
    if not is_creator() and v_company_id <> current_company_id() then raise exception 'Not allowed.'; end if;
  end if;
  if not exists (select 1 from suppliers where id = v_supplier and company_id = v_company_id) then
    raise exception 'Choose a supplier in the receipt''s company.';
  end if;
  if v_project is not null and not exists (
    select 1 from projects where id = v_project and company_id = v_company_id
  ) then raise exception 'Choose a project in the receipt''s company.'; end if;
  if exists (
    select 1 from receipts r
      join suppliers existing_supplier on existing_supplier.id = r.supplier_id
      join suppliers selected_supplier on selected_supplier.id = v_supplier
    where r.company_id = v_company_id
      and lower(btrim(r.receipt_no)) = lower(btrim(v_no))
      and lower(btrim(existing_supplier.tin)) = lower(btrim(selected_supplier.tin))
      and (not v_existing or r.id <> v_id)
  ) then raise exception 'Duplicate receipt: % already exists for this supplier.', v_no; end if;
  if jsonb_typeof(p->'items') is distinct from 'array' or jsonb_array_length(p->'items') = 0 then
    raise exception 'Add at least one item.';
  end if;
  if v_discount < 0 or v_vat < 0 or v_charges < 0 then raise exception 'Discount, VAT and additional charges cannot be negative.'; end if;
  for v_item in select * from jsonb_array_elements(p->'items') loop
    if btrim(coalesce(v_item->>'description', '')) = '' then raise exception 'Every item needs a description.'; end if;
    if coalesce((v_item->>'quantity')::numeric, 0) <= 0 then raise exception 'Item quantity must be greater than zero.'; end if;
    if coalesce((v_item->>'unit_price')::numeric, -1) < 0 then raise exception 'Item unit price cannot be negative.'; end if;
    v_subtotal := v_subtotal + round((v_item->>'quantity')::numeric * (v_item->>'unit_price')::numeric, 2);
  end loop;
  if v_discount > v_subtotal then raise exception 'Discount cannot be more than the subtotal.'; end if;
  begin
    if v_existing then
      update receipts set receipt_no = v_no, receipt_date = (p->>'receipt_date')::date,
        receipt_type = coalesce(nullif(p->>'receipt_type', ''), 'official_receipt')::receipt_type,
        supplier_id = v_supplier, project_id = v_project, discount = v_discount, vat = v_vat,
        additional_charges = v_charges, modified_at = now() where id = v_id;
    else
      insert into receipts (id, company_id, receipt_no, receipt_date, receipt_type, supplier_id, project_id,
        discount, vat, additional_charges, encoded_by)
      values (coalesce(v_id, gen_random_uuid()), v_company_id, v_no, (p->>'receipt_date')::date,
        coalesce(nullif(p->>'receipt_type', ''), 'official_receipt')::receipt_type, v_supplier, v_project,
        v_discount, v_vat, v_charges, auth.uid()) returning id into v_id;
    end if;
  exception when unique_violation then
    raise exception 'Duplicate receipt: % already exists for this supplier.', v_no;
  end;
  delete from receipt_items where receipt_id = v_id;
  for v_item in select * from jsonb_array_elements(p->'items') loop
    v_line := v_line + 1;
    insert into receipt_items (receipt_id, line_no, description, quantity, unit_price)
    values (v_id, v_line, btrim(v_item->>'description'), (v_item->>'quantity')::numeric, (v_item->>'unit_price')::numeric);
  end loop;
  delete from receipt_attachments where receipt_id = v_id;
  for v_att in select * from jsonb_array_elements(coalesce(p->'attachments', '[]'::jsonb)) loop
    insert into receipt_attachments (receipt_id, file_name, storage_path)
    values (v_id, v_att->>'file_name', v_att->>'storage_path');
  end loop;
  insert into activity_log (user_id, user_name, company_id, action, detail)
  select auth.uid(), full_name, v_company_id, case when v_existing then 'receipt.update' else 'receipt.create' end, v_no
  from profiles where id = auth.uid();
  return v_id;
end $$;

create or replace function delete_receipt(p_id uuid) returns text[]
language plpgsql security definer set search_path = public as $$
declare v_paths text[]; v_no text; v_company_id uuid;
begin
  if not is_admin() then raise exception 'Administrator role required.'; end if;
  select receipt_no, company_id into v_no, v_company_id from receipts
    where id = p_id and (is_creator() or company_id = current_company_id());
  if v_no is null then raise exception 'Receipt not found.'; end if;
  select coalesce(array_agg(storage_path), '{}') into v_paths from receipt_attachments where receipt_id = p_id;
  delete from receipts where id = p_id;
  insert into activity_log (user_id, user_name, company_id, action, detail, source_key)
  select auth.uid(), full_name, v_company_id, 'receipt.delete', v_no, 'receipt.delete:' || p_id::text
    from profiles where id = auth.uid();
  return v_paths;
end $$;

create table if not exists bug_reports (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null default current_company_id() references companies(id) on delete restrict,
  reporter_id uuid not null default auth.uid() references profiles(id) on delete restrict,
  title text not null check (btrim(title) <> ''),
  description text not null check (btrim(description) <> ''),
  attachment_path text,
  created_at timestamptz not null default now()
);
alter table bug_reports enable row level security;
create policy bug_reports_insert on bug_reports for insert to authenticated
  with check (is_active_user() and reporter_id = auth.uid() and company_id = current_company_id());
create policy bug_reports_creator_read on bug_reports for select to authenticated
  using (is_creator() or reporter_id = auth.uid());
grant insert, select on bug_reports to authenticated;
grant all on bug_reports to service_role;
create index if not exists bug_reports_created_at_idx on bug_reports (created_at desc);

insert into storage.buckets (id, name, public) values ('bug-reports', 'bug-reports', false)
  on conflict (id) do nothing;
drop policy if exists receipts_files_read on storage.objects;
drop policy if exists receipts_files_insert on storage.objects;
drop policy if exists receipts_files_delete on storage.objects;
create policy receipts_files_read on storage.objects for select to authenticated
  using (bucket_id = 'receipts' and is_active_user() and exists (
    select 1 from receipts r where r.id::text = split_part(name, '/', 1)
      and (is_creator() or r.company_id = current_company_id())
  ));
create policy receipts_files_insert on storage.objects for insert to authenticated
  with check (bucket_id = 'receipts' and is_active_user());
create policy receipts_files_delete on storage.objects for delete to authenticated
  using (bucket_id = 'receipts' and is_admin() and exists (
    select 1 from receipts r where r.id::text = split_part(name, '/', 1)
      and (is_creator() or r.company_id = current_company_id())
  ) or bucket_id = 'receipts' and is_admin() and exists (
    select 1 from activity_log l
      where l.source_key = 'receipt.delete:' || split_part(name, '/', 1)
        and l.action = 'receipt.delete'
        and (is_creator() or l.company_id = current_company_id())
  ));
create policy bug_report_files_insert on storage.objects for insert to authenticated
  with check (bucket_id = 'bug-reports' and is_active_user()
    and split_part(name, '/', 1) = auth.uid()::text);
create policy bug_report_files_read on storage.objects for select to authenticated
  using (bucket_id = 'bug-reports' and is_active_user()
    and (is_creator() or split_part(name, '/', 1) = auth.uid()::text));
create policy bug_report_files_delete on storage.objects for delete to authenticated
  using (bucket_id = 'bug-reports' and is_creator());

revoke all on function register_account_profile(uuid, text, text, uuid),
  register_company_admin_profile(uuid, text, text, text), review_account(uuid, uuid, text)
  from public, anon, authenticated;
grant execute on function register_account_profile(uuid, text, text, uuid),
  register_company_admin_profile(uuid, text, text, text), review_account(uuid, uuid, text) to service_role;
revoke all on function admin_update_profile(uuid, text, user_role, boolean) from public, anon, authenticated;
grant execute on function admin_update_profile(uuid, text, user_role, boolean) to authenticated;
revoke all on function save_receipt(jsonb), delete_receipt(uuid) from public, anon, authenticated;
grant execute on function save_receipt(jsonb), delete_receipt(uuid) to authenticated;
revoke all on function log_activity(text, text) from public, anon, authenticated;
