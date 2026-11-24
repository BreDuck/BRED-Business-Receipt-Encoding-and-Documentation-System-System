-- RMS 012: company-wide group chat with urgent message labels.
alter table custom_messages
  add column if not exists company_id uuid references companies(id) on delete cascade,
  add column if not exists is_urgent boolean not null default false;

create index if not exists custom_messages_company_chat_idx
  on custom_messages (company_id, sent_at, id) where recipient_id is null;

drop policy if exists custom_messages_participant_read on custom_messages;
create policy custom_messages_participant_read on custom_messages for select to authenticated
  using (is_active_user() and (
    (recipient_id is not null and (sender_id = auth.uid() or recipient_id = auth.uid()))
    or (recipient_id is null and company_id = current_company_id())
  ));

drop policy if exists custom_messages_send on custom_messages;
create policy custom_messages_send on custom_messages for insert to authenticated
  with check (
    is_active_user()
    and sender_id = auth.uid()
    and recipient_id is not null
    and company_id is null
    and not is_urgent
    and can_message_user(recipient_id)
  );

drop policy if exists custom_messages_company_send on custom_messages;
create policy custom_messages_company_send on custom_messages for insert to authenticated
  with check (
    is_active_user()
    and sender_id = auth.uid()
    and recipient_id is null
    and company_id = current_company_id()
  );

grant insert (company_id, is_urgent) on custom_messages to authenticated;

drop function if exists creator_message_history();
create function creator_message_history()
returns table (
  id uuid, sender_id uuid, recipient_id uuid, sender_name text, recipient_name text,
  body text, sent_at timestamptz, is_broadcast boolean, company_id uuid, is_urgent boolean
)
language plpgsql stable security definer set search_path = public as $$
begin
  if not is_creator() then raise exception 'Creator role required.'; end if;
  return query
    select m.id, m.sender_id, m.recipient_id,
           coalesce(nullif(m.sender_name, ''), sender.full_name, 'Deleted user'),
           case when m.company_id is not null then coalesce(c.name, 'Company chat')
                else coalesce(nullif(m.recipient_name, ''), recipient.full_name, 'Deleted user') end,
           m.body, m.sent_at, m.is_broadcast, m.company_id, m.is_urgent
    from custom_messages m
    left join profiles sender on sender.id = m.sender_id
    left join profiles recipient on recipient.id = m.recipient_id
    left join companies c on c.id = m.company_id
    order by m.sent_at desc, m.id desc;
end $$;

revoke all on function creator_message_history() from public, anon, authenticated;
grant execute on function creator_message_history() to authenticated;

notify pgrst, 'reload schema';
