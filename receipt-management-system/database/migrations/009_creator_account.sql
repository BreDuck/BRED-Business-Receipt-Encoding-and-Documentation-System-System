-- RMS 009: assign the BreDuck Creator account and keep that role protected.

update profiles
set role = 'creator'
where lower(btrim(email)) = 'clarkhilario.bread@gmail.com';

create or replace function enforce_creator_account_role()
returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if lower(btrim(coalesce(new.email, ''))) = 'clarkhilario.bread@gmail.com' then
    new.role := 'creator';
  end if;
  return new;
end $$;

drop trigger if exists enforce_creator_account_role on profiles;
create trigger enforce_creator_account_role
before insert or update of email, role on profiles
for each row execute function enforce_creator_account_role();
