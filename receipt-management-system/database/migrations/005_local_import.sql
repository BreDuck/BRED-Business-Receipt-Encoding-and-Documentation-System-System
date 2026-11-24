-- RMS 005: stable import identifiers let local activity history be retried safely.
alter table activity_log add column if not exists source_key text;
create unique index if not exists activity_log_source_key_key on activity_log (source_key);
grant all on activity_log to service_role;
