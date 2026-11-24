-- Minimal stand-ins for the parts of Supabase that the migrations rely on (local validation only).
create role anon nologin;
create role authenticated nologin;
create role service_role nologin bypassrls;
create schema auth;
create table auth.users (id uuid primary key, email text);
create table public.companies (
  id uuid primary key,
  name text not null,
  is_active boolean not null default true
);
create function auth.uid() returns uuid language sql stable as
$$ select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
create schema storage;
create table storage.buckets (id text primary key, name text not null, public boolean default false);
create table storage.objects (id uuid primary key default gen_random_uuid(), bucket_id text references storage.buckets(id), name text not null, owner uuid);
alter table storage.objects enable row level security;
grant usage on schema auth, storage to anon, authenticated;
grant execute on function auth.uid() to anon, authenticated;
grant select, insert, update, delete on storage.objects to authenticated;
grant select on storage.buckets to authenticated;
grant usage on schema public to anon, authenticated;
grant usage on schema public to service_role;
-- Supabase gives anon/authenticated EXECUTE on every new function in public by default; mimic it so the migrations must revoke explicitly.
alter default privileges in schema public grant execute on functions to anon, authenticated;
