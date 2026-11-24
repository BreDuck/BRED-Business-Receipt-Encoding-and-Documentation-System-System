-- RMS 010: private, one-to-one messages.
-- Teammate names/roles are available to active colleagues, but their email addresses
-- remain hidden from the messaging contact list.
create or replace function message_contacts()
returns table (id uuid, full_name text, role user_role, company_name text)
language sql stable security definer set search_path = public as
$$
  select p.id, p.full_name, p.role, coalesce(c.name, '')
  from profiles p
  left join companies c on c.id = p.company_id
  where is_active_user()
    and p.is_active
    and p.account_status = 'APPROVED'
    and (is_creator() or p.company_id = current_company_id() or p.role = 'creator')
  order by p.full_name, p.id
$$;
revoke all on function message_contacts() from public, anon, authenticated;
grant execute on function message_contacts() to authenticated;

create or replace function can_message_user(p_recipient_id uuid)
returns boolean
language sql stable security definer set search_path = public as
$$
  select is_active_user() and exists (
    select 1 from profiles recipient
    where recipient.id = p_recipient_id
      and recipient.is_active
      and recipient.account_status = 'APPROVED'
      and (is_creator() or recipient.company_id = current_company_id() or recipient.role = 'creator')
  )
$$;
revoke all on function can_message_user(uuid) from public, anon, authenticated;
grant execute on function can_message_user(uuid) to authenticated;

create table if not exists custom_messages (
  id uuid primary key default gen_random_uuid(),
  sender_id uuid not null default auth.uid() references profiles(id) on delete cascade,
  recipient_id uuid not null references profiles(id) on delete cascade,
  body text not null check (btrim(body) <> '' and char_length(body) <= 4000),
  sent_at timestamptz not null default now(),
  constraint custom_messages_distinct_participants check (sender_id <> recipient_id)
);

create index if not exists custom_messages_sender_sent_idx on custom_messages (sender_id, sent_at, id);
create index if not exists custom_messages_recipient_sent_idx on custom_messages (recipient_id, sent_at, id);

alter table custom_messages enable row level security;
drop policy if exists custom_messages_participant_read on custom_messages;
create policy custom_messages_participant_read on custom_messages for select to authenticated
  using (is_active_user() and (sender_id = auth.uid() or recipient_id = auth.uid()));

drop policy if exists custom_messages_send on custom_messages;
create policy custom_messages_send on custom_messages for insert to authenticated
  with check (
    is_active_user()
    and sender_id = auth.uid()
    and can_message_user(recipient_id)
  );

revoke all on custom_messages from anon, authenticated;
grant select on custom_messages to authenticated;
grant insert (recipient_id, body) on custom_messages to authenticated;
grant all on custom_messages to service_role;
