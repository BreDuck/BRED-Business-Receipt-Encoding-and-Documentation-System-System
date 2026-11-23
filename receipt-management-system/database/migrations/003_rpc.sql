-- RMS 003: server-side operations. All are SECURITY DEFINER with a pinned search_path.

create or replace function log_activity(p_action text, p_detail text) returns void
language plpgsql security definer set search_path = public as $$
begin
  insert into activity_log (user_id, user_name, action, detail)
  select auth.uid(), (select full_name from profiles where id = auth.uid()), p_action, coalesce(p_detail, '');
end $$;

create or replace function save_receipt(p jsonb) returns uuid
language plpgsql security definer set search_path = public as $$
declare
  v_id uuid := nullif(p->>'id', '')::uuid;
  v_existing boolean;
  v_no text := btrim(coalesce(p->>'receipt_no', ''));
  v_supplier uuid := nullif(p->>'supplier_id', '')::uuid;
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
  if v_supplier is null or not exists (select 1 from suppliers where id = v_supplier) then
    raise exception 'Choose a supplier.';
  end if;
  if jsonb_typeof(p->'items') is distinct from 'array' or jsonb_array_length(p->'items') = 0 then
    raise exception 'Add at least one item.';
  end if;
  if v_discount < 0 or v_vat < 0 or v_charges < 0 then
    raise exception 'Discount, VAT and additional charges cannot be negative.';
  end if;
  for v_item in select * from jsonb_array_elements(p->'items') loop
    if btrim(coalesce(v_item->>'description', '')) = '' then raise exception 'Every item needs a description.'; end if;
    if coalesce((v_item->>'quantity')::numeric, 0) <= 0 then raise exception 'Item quantity must be greater than zero.'; end if;
    if coalesce((v_item->>'unit_price')::numeric, -1) < 0 then raise exception 'Item unit price cannot be negative.'; end if;
    v_subtotal := v_subtotal + round((v_item->>'quantity')::numeric * (v_item->>'unit_price')::numeric, 2);
  end loop;
  if v_discount > v_subtotal then raise exception 'Discount cannot be more than the subtotal.'; end if;

  v_existing := v_id is not null and exists (select 1 from receipts where id = v_id);
  begin
    if v_existing then
      update receipts set receipt_no = v_no, receipt_date = (p->>'receipt_date')::date,
             receipt_type = coalesce(nullif(p->>'receipt_type', ''), 'official_receipt')::receipt_type,
             supplier_id = v_supplier, project_id = nullif(p->>'project_id', '')::uuid,
             discount = v_discount, vat = v_vat, additional_charges = v_charges, modified_at = now()
       where id = v_id;
    else
      insert into receipts (id, receipt_no, receipt_date, receipt_type, supplier_id, project_id, discount, vat, additional_charges, encoded_by)
      values (coalesce(v_id, gen_random_uuid()), v_no, (p->>'receipt_date')::date,
              coalesce(nullif(p->>'receipt_type', ''), 'official_receipt')::receipt_type,
              v_supplier, nullif(p->>'project_id', '')::uuid, v_discount, v_vat, v_charges, auth.uid())
      returning id into v_id;
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

  perform log_activity(case when v_existing then 'receipt.update' else 'receipt.create' end, v_no);
  return v_id;
end $$;

create or replace function delete_receipt(p_id uuid) returns text[]
language plpgsql security definer set search_path = public as $$
declare v_paths text[]; v_no text;
begin
  if not is_admin() then raise exception 'Administrator role required.'; end if;
  select receipt_no into v_no from receipts where id = p_id;
  if v_no is null then raise exception 'Receipt not found.'; end if;
  select coalesce(array_agg(storage_path), '{}') into v_paths from receipt_attachments where receipt_id = p_id;
  delete from receipts where id = p_id;
  perform log_activity('receipt.delete', v_no);
  return v_paths;
end $$;

create or replace function admin_update_profile(p_id uuid, p_full_name text, p_role user_role, p_active boolean) returns void
language plpgsql security definer set search_path = public as $$
declare v_target profiles;
begin
  if not is_admin() then raise exception 'Administrator role required.'; end if;
  if btrim(coalesce(p_full_name, '')) = '' then raise exception 'Full name is required.'; end if;
  select * into v_target from profiles where id = p_id for update;
  if not found then raise exception 'User not found.'; end if;
  if v_target.role = 'admin' and v_target.is_active and (p_role <> 'admin' or not p_active)
     and (select count(*) from profiles where role = 'admin' and is_active) <= 1 then
    raise exception 'Cannot remove the last active administrator.';
  end if;
  update profiles set full_name = btrim(p_full_name), role = p_role, is_active = p_active where id = p_id;
  perform log_activity('user.update', v_target.email);
end $$;

-- Log supplier / project changes automatically.
create or replace function trg_log_master_data() returns trigger
language plpgsql security definer set search_path = public as $$
declare v_row jsonb := to_jsonb(case when tg_op = 'DELETE' then old else new end);
begin
  perform log_activity(tg_table_name || '.' || lower(tg_op),
    coalesce(v_row->>'business_name', v_row->>'name') || ' (' || coalesce(v_row->>'tin', v_row->>'code') || ')');
  if tg_op = 'DELETE' then return old; end if;
  return new;
end $$;

create or replace function trg_touch_supplier() returns trigger language plpgsql as
$$ begin new.updated_at := now(); return new; end $$;

create trigger suppliers_log after insert or update or delete on suppliers for each row execute function trg_log_master_data();
create trigger projects_log after insert or update or delete on projects for each row execute function trg_log_master_data();
create trigger suppliers_touch before update on suppliers for each row execute function trg_touch_supplier();

revoke all on function log_activity(text, text), save_receipt(jsonb), delete_receipt(uuid),
  admin_update_profile(uuid, text, user_role, boolean) from public, anon, authenticated;
grant execute on function save_receipt(jsonb), delete_receipt(uuid), admin_update_profile(uuid, text, user_role, boolean) to authenticated;
