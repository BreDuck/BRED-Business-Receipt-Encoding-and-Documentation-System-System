-- Behavioural checks for the RMS schema. Every check prints "ok - ..." or aborts with an error.
create schema tests;
grant usage on schema tests to authenticated;

create function tests.expect_error(stmt text, needle text) returns void language plpgsql as $$
begin
  begin
    execute stmt;
  exception when others then
    if position(lower(needle) in lower(sqlerrm)) = 0 then
      raise exception 'FAIL expected error containing "%" but got "%" for: %', needle, sqlerrm, stmt;
    end if;
    raise notice 'ok - raises "%"', needle;
    return;
  end;
  raise exception 'FAIL no error raised (wanted "%") for: %', needle, stmt;
end $$;

create function tests.expect_true(cond boolean, msg text) returns void language plpgsql as $$
begin
  if cond is not true then raise exception 'FAIL %', msg; end if;
  raise notice 'ok - %', msg;
end $$;

create function tests.login(u uuid) returns void language sql as
$$ select set_config('request.jwt.claim.sub', coalesce(u::text, ''), false) $$;
grant execute on all functions in schema tests to authenticated;

-- fixtures (as superuser)
insert into auth.users(id, email) values
  ('a0000000-0000-0000-0000-000000000001', 'admin@x.test'),
  ('e0000000-0000-0000-0000-000000000002', 'enc@x.test'),
  ('f0000000-0000-0000-0000-000000000003', 'off@x.test');
insert into profiles(id, full_name, email, role, is_active) values
  ('a0000000-0000-0000-0000-000000000001', 'Ada Admin', 'admin@x.test', 'admin', true),
  ('e0000000-0000-0000-0000-000000000002', 'Eli Encoder', 'enc@x.test', 'encoder', true),
  ('f0000000-0000-0000-0000-000000000003', 'Off Boarded', 'off@x.test', 'encoder', false);
insert into suppliers(id, business_name, tin) values
  ('11111111-0000-0000-0000-000000000001', 'Acme Hardware', '123-456-789-000'),
  ('11111111-0000-0000-0000-000000000002', 'Beta Supplies', '987-654-321-000');
insert into projects(id, name, code) values ('22222222-0000-0000-0000-000000000001', 'Bridge', 'BR-01');

create function tests.payload(no text, supplier uuid, price text default '100.00', discount text default '0') returns jsonb
language sql as $$
  select jsonb_build_object('receipt_no', no, 'receipt_date', '2026-11-01', 'receipt_type', 'official_receipt',
    'supplier_id', supplier, 'project_id', '22222222-0000-0000-0000-000000000001',
    'discount', discount::numeric, 'vat', 0, 'additional_charges', 0,
    'items', jsonb_build_array(jsonb_build_object('description', 'Nails', 'quantity', 2, 'unit_price', price::numeric),
                               jsonb_build_object('description', 'Hammer', 'quantity', 1, 'unit_price', 50)),
    'attachments', jsonb_build_array(jsonb_build_object('file_name', 'scan.pdf', 'storage_path', 'x/' || lower(btrim(no)) || '-' || supplier || '.pdf')))
$$;
grant execute on function tests.payload(text, uuid, text, text) to authenticated;

set role authenticated;

-- ===== encoder =====
select tests.login('e0000000-0000-0000-0000-000000000002');
select tests.expect_true((select save_receipt(tests.payload('OR-001', '11111111-0000-0000-0000-000000000001'))) is not null, 'encoder can save a receipt');
select tests.expect_true((select count(*) from receipt_items) = 2 and (select count(*) from receipt_attachments) = 1, 'items and attachment rows were stored with the receipt');
select tests.expect_true((select encoded_by from receipts where receipt_no = 'OR-001') = 'e0000000-0000-0000-0000-000000000002', 'encoded_by is the caller');
select tests.expect_true((select save_receipt(jsonb_set(tests.payload('OR-001', '11111111-0000-0000-0000-000000000001', '300'), '{id}', to_jsonb((select id from receipts where receipt_no = 'OR-001')::text)))) = (select id from receipts where receipt_no = 'OR-001'), 'editing a receipt keeps its id and does not clash with itself');
select tests.expect_true((select count(*) from receipt_items where receipt_id = (select id from receipts where receipt_no = 'OR-001')) = 2 and (select unit_price from receipt_items where description = 'Nails') = 300, 'editing replaces the items');
select tests.expect_error($$select save_receipt(tests.payload('  or-001 ', '11111111-0000-0000-0000-000000000001'))$$, 'Duplicate receipt');
select tests.expect_true((select save_receipt(tests.payload('OR-001', '11111111-0000-0000-0000-000000000002'))) is not null, 'same receipt no. is fine for a different supplier');
select tests.expect_error($$select save_receipt(tests.payload('OR-NEG', '11111111-0000-0000-0000-000000000001', '-5'))$$, 'negative');
select tests.expect_error($$select save_receipt(tests.payload('OR-DISC', '11111111-0000-0000-0000-000000000001', '100', '999'))$$, 'Discount');
select tests.expect_error($$select save_receipt(jsonb_set(tests.payload('OR-EMPTY', '11111111-0000-0000-0000-000000000001'), '{items}', '[]'))$$, 'at least one item');
select tests.expect_error($$select log_activity('receipt.delete', 'forged')$$, 'permission denied');
select tests.expect_error($$insert into receipts(receipt_no, receipt_date, supplier_id) values ('DIRECT', current_date, '11111111-0000-0000-0000-000000000001')$$, 'permission denied');
select tests.expect_error($$select delete_receipt((select id from receipts limit 1))$$, 'administrator');
select tests.expect_error($$select admin_update_profile('e0000000-0000-0000-0000-000000000002', 'X', 'admin', true)$$, 'administrator');
select tests.expect_true((select count(*) from activity_log) = 0, 'encoder cannot read the activity log');
delete from suppliers where id = '11111111-0000-0000-0000-000000000002';
select tests.expect_true((select count(*) from suppliers) = 2, 'encoder delete of a supplier removes nothing');
insert into suppliers(business_name, tin) values ('Gamma', ' 555 ');
select tests.expect_error($$insert into suppliers(business_name, tin) values ('Gamma 2', '555')$$, 'duplicate key');
insert into storage.objects(bucket_id, name) values ('receipts', 'x/or-001-11111111-0000-0000-0000-000000000001.pdf');
select tests.expect_true((select count(*) from storage.objects) = 1, 'encoder can upload to the receipts bucket');
delete from storage.objects where bucket_id = 'receipts';
select tests.expect_true((select count(*) from storage.objects) = 1, 'encoder cannot delete stored files');

-- ===== inactive user =====
select tests.login('f0000000-0000-0000-0000-000000000003');
select tests.expect_true((select count(*) from receipts) = 0, 'inactive user sees no receipts');
select tests.expect_error($$select save_receipt(tests.payload('OR-OFF', '11111111-0000-0000-0000-000000000001'))$$, 'not allowed');
select tests.expect_true((select count(*) from profiles) = 1, 'inactive user can still read only their own profile');

-- ===== admin =====
select tests.login('a0000000-0000-0000-0000-000000000001');
select tests.expect_true((select count(*) from activity_log where action = 'receipt.create') = 2, 'admin sees receipt.create log rows');
select tests.expect_true((select count(*) from activity_log where action like 'suppliers.%') >= 1, 'supplier changes are logged by trigger');
select tests.expect_error($$delete from suppliers where id = '11111111-0000-0000-0000-000000000001'$$, 'violates foreign key');
select tests.expect_error($$select admin_update_profile('a0000000-0000-0000-0000-000000000001', 'Ada', 'encoder', true)$$, 'last active administrator');
select tests.expect_error($$select admin_update_profile('a0000000-0000-0000-0000-000000000001', 'Ada', 'admin', false)$$, 'last active administrator');
select admin_update_profile('e0000000-0000-0000-0000-000000000002', 'Eli E.', 'encoder', true);
select tests.expect_true((select full_name from profiles where id = 'e0000000-0000-0000-0000-000000000002') = 'Eli E.', 'admin can rename a user');
select tests.expect_true((select array_length(delete_receipt((select id from receipts where receipt_no = 'OR-001' and supplier_id = '11111111-0000-0000-0000-000000000001')), 1)) = 1, 'admin delete_receipt returns the storage paths');
select tests.expect_true((select count(*) from receipts) = 1 and (select count(*) from receipt_items) = 2, 'delete cascades items of the deleted receipt only');
delete from storage.objects where bucket_id = 'receipts';
select tests.expect_true((select count(*) from storage.objects) = 0, 'admin can delete stored files');
select tests.expect_true((select count(*) from activity_log where action = 'receipt.delete') = 1, 'receipt.delete is logged');

reset role;
select 'ALL ASSERTIONS PASSED' as result;
