-- RMS 013: publish one globally visible app release; only the Creator may change it.
create table if not exists app_releases (
  release_key text primary key check (release_key = 'current'),
  version text not null check (char_length(btrim(version)) between 1 and 64),
  updated_at timestamptz not null default now()
);

insert into app_releases (release_key, version)
values ('current', '0.1.0-SNAPSHOT')
on conflict (release_key) do nothing;

alter table app_releases enable row level security;

drop policy if exists app_releases_public_read on app_releases;
create policy app_releases_public_read on app_releases
  for select to anon, authenticated using (true);

drop policy if exists app_releases_creator_update on app_releases;
create policy app_releases_creator_update on app_releases
  for update to authenticated using (is_creator()) with check (is_creator());

create or replace function set_app_release_metadata()
returns trigger
language plpgsql set search_path = public as $$
begin
  new.updated_at := now();
  return new;
end $$;

drop trigger if exists app_releases_set_metadata on app_releases;
create trigger app_releases_set_metadata
before update on app_releases for each row execute function set_app_release_metadata();

grant select on app_releases to anon, authenticated;
grant update (version) on app_releases to authenticated;

notify pgrst, 'reload schema';