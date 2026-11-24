-- RMS 011: Creator-only company management, global message history, and persistent broadcasts.

drop policy if exists companies_creator_update on companies;
create policy companies_creator_update on companies for update to authenticated
  using (is_creator()) with check (is_creator());
drop policy if exists companies_creator_delete on companies;
create policy companies_creator_delete on companies for delete to authenticated
  using (is_creator());
grant update, delete on companies to authenticated;

alter table custom_messages
  add column if not exists sender_name text not null default '',
  add column if not exists sender_email text not null default '',
  add column if not exists recipient_name text not null default '',
  add column if not exists is_broadcast boolean not null default false;

update custom_messages m
set sender_name = coalesce(nullif(m.sender_name, ''), sender.full_name, 'Deleted user'),
    sender_email = coalesce(nullif(m.sender_email, ''), sender.email, ''),
    recipient_name = coalesce(nullif(m.recipient_name, ''), recipient.full_name, 'Deleted user')
from profiles sender, profiles recipient
where sender.id = m.sender_id and recipient.id = m.recipient_id;

create or replace function snapshot_custom_message_names()
returns trigger
language plpgsql security definer set search_path = public as $$
declare
  v_name text;
  v_email text;
begin
  if new.sender_name = '' and new.sender_id is not null then
    select full_name, email into v_name, v_email from profiles where id = new.sender_id;
    new.sender_name := coalesce(v_name, '');
    new.sender_email := coalesce(v_email, '');
  end if;
  if new.recipient_name = '' and new.recipient_id is not null then
    select full_name into v_name from profiles where id = new.recipient_id;
    new.recipient_name := coalesce(v_name, '');
  end if;
  return new;
end $$;

drop trigger if exists snapshot_custom_message_names on custom_messages;
create trigger snapshot_custom_message_names
before insert on custom_messages for each row execute function snapshot_custom_message_names();

alter table custom_messages drop constraint if exists custom_messages_sender_id_fkey;
alter table custom_messages drop constraint if exists custom_messages_recipient_id_fkey;
alter table custom_messages alter column sender_id drop not null;
alter table custom_messages alter column recipient_id drop not null;
alter table custom_messages add constraint custom_messages_sender_id_fkey
  foreign key (sender_id) references profiles(id) on delete set null;
alter table custom_messages add constraint custom_messages_recipient_id_fkey
  foreign key (recipient_id) references profiles(id) on delete set null;

create index if not exists custom_messages_broadcast_recipient_sent_idx
  on custom_messages (recipient_id, sent_at, id) where is_broadcast;
create index if not exists custom_messages_history_idx on custom_messages (sent_at desc, id desc);

create or replace function preserve_message_names_before_profile_delete()
returns trigger
language plpgsql security definer set search_path = public as $$
begin
  update custom_messages
  set sender_name = case when sender_id = old.id then 'Deleted user' else sender_name end,
      sender_email = case when sender_id = old.id then '' else sender_email end,
      recipient_name = case when recipient_id = old.id then 'Deleted user' else recipient_name end
  where sender_id = old.id or recipient_id = old.id;
  return old;
end $$;

drop trigger if exists preserve_message_names_before_profile_delete on profiles;
create trigger preserve_message_names_before_profile_delete
before delete on profiles for each row execute function preserve_message_names_before_profile_delete();

create or replace function creator_message_history()
returns table (
  id uuid, sender_id uuid, recipient_id uuid, sender_name text, recipient_name text,
  body text, sent_at timestamptz, is_broadcast boolean
)
language plpgsql stable security definer set search_path = public as $$
begin
  if not is_creator() then raise exception 'Creator role required.'; end if;
  return query
    select m.id, m.sender_id, m.recipient_id,
           coalesce(nullif(m.sender_name, ''), sender.full_name, 'Deleted user'),
           coalesce(nullif(m.recipient_name, ''), recipient.full_name, 'Deleted user'),
           m.body, m.sent_at, m.is_broadcast
    from custom_messages m
    left join profiles sender on sender.id = m.sender_id
    left join profiles recipient on recipient.id = m.recipient_id
    order by m.sent_at desc, m.id desc;
end $$;

create or replace function creator_broadcast(p_recipient_ids uuid[], p_body text)
returns integer
language plpgsql security definer set search_path = public as $$
declare
  v_sender profiles;
  v_body text := btrim(coalesce(p_body, ''));
  v_inserted integer;
begin
  select * into v_sender from profiles
  where id = auth.uid() and role = 'creator' and is_active and account_status = 'APPROVED';
  if not found then raise exception 'An active Creator account is required.'; end if;
  if v_body = '' or char_length(v_body) > 4000 then
    raise exception 'Broadcast messages must contain 1 to 4000 characters.';
  end if;
  if p_recipient_ids is null or cardinality(p_recipient_ids) = 0 then
    raise exception 'Choose at least one recipient.';
  end if;
  if cardinality(p_recipient_ids) <> (
    select count(distinct requested.id) from unnest(p_recipient_ids) as requested(id)
  ) then
    raise exception 'The recipient list contains duplicates.';
  end if;
  if v_sender.id = any(p_recipient_ids) then
    raise exception 'The Creator cannot be a broadcast recipient.';
  end if;
  if exists (
    select 1
    from unnest(p_recipient_ids) requested(id)
    left join profiles p on p.id = requested.id
    where p.id is null or not p.is_active or p.account_status <> 'APPROVED'
  ) then
    raise exception 'Every recipient must be an active, approved account.';
  end if;

  insert into custom_messages (sender_id, recipient_id, sender_name, sender_email, recipient_name, body, is_broadcast)
  select v_sender.id, p.id, v_sender.full_name, v_sender.email, p.full_name, v_body, true
  from profiles p
  where p.id = any(p_recipient_ids);
  get diagnostics v_inserted = row_count;
  return v_inserted;
end $$;

revoke all on function creator_message_history(), creator_broadcast(uuid[], text)
  from public, anon, authenticated;
grant execute on function creator_message_history(), creator_broadcast(uuid[], text)
  to authenticated;

drop trigger if exists companies_log on companies;
create trigger companies_log after insert or update or delete on companies
  for each row execute function trg_log_master_data();
