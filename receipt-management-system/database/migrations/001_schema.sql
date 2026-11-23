-- RMS 001: core tables. Run first.
create type user_role as enum ('admin', 'encoder');
create type receipt_type as enum ('official_receipt', 'sales_invoice', 'cash_invoice', 'delivery_receipt', 'other');

create table profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  full_name text not null,
  email text not null,
  role user_role not null default 'encoder',
  is_active boolean not null default true,
  created_at timestamptz not null default now()
);

create table suppliers (
  id uuid primary key default gen_random_uuid(),
  business_name text not null check (btrim(business_name) <> ''),
  tin text not null check (btrim(tin) <> ''),
  address text not null default '',
  contact_number text not null default '',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create unique index suppliers_tin_key on suppliers (lower(btrim(tin)));

create table projects (
  id uuid primary key default gen_random_uuid(),
  name text not null check (btrim(name) <> ''),
  code text not null check (btrim(code) <> ''),
  fund_source text not null default '',
  created_at timestamptz not null default now()
);
create unique index projects_code_key on projects (lower(btrim(code)));

create table receipts (
  id uuid primary key default gen_random_uuid(),
  receipt_no text not null check (btrim(receipt_no) <> ''),
  receipt_date date not null,
  receipt_type receipt_type not null default 'official_receipt',
  supplier_id uuid not null references suppliers (id) on delete restrict,
  project_id uuid references projects (id) on delete restrict,
  discount numeric(14, 2) not null default 0 check (discount >= 0),
  vat numeric(14, 2) not null default 0 check (vat >= 0),
  additional_charges numeric(14, 2) not null default 0 check (additional_charges >= 0),
  encoded_by uuid references profiles (id) on delete set null,
  created_at timestamptz not null default now(),
  modified_at timestamptz not null default now()
);
-- Same receipt number from the same supplier (same TIN) is a duplicate.
create unique index receipts_supplier_no_key on receipts (supplier_id, lower(btrim(receipt_no)));
create index receipts_date_idx on receipts (receipt_date desc);
create index receipts_project_idx on receipts (project_id);

create table receipt_items (
  id uuid primary key default gen_random_uuid(),
  receipt_id uuid not null references receipts (id) on delete cascade,
  line_no integer not null,
  description text not null check (btrim(description) <> ''),
  quantity numeric(14, 3) not null check (quantity > 0),
  unit_price numeric(14, 2) not null check (unit_price >= 0)
);
create index receipt_items_receipt_idx on receipt_items (receipt_id);

create table receipt_attachments (
  id uuid primary key default gen_random_uuid(),
  receipt_id uuid not null references receipts (id) on delete cascade,
  file_name text not null,
  storage_path text not null unique
);
create index receipt_attachments_receipt_idx on receipt_attachments (receipt_id);

create table activity_log (
  id bigint generated always as identity primary key,
  at timestamptz not null default now(),
  user_id uuid,
  user_name text,
  action text not null,
  detail text not null default ''
);
create index activity_log_at_idx on activity_log (at desc);
