-- RMS seed / bootstrap. Run AFTER migrations 001-004.
--
-- FIRST ADMIN (do this once)
--   1. Supabase Dashboard -> Authentication -> Users -> "Add user" -> "Create new user".
--      Enter your email + a password and tick "Auto Confirm User".
--   2. Copy the new user's UUID and run (replace the three values):
--
--      insert into profiles (id, full_name, email, role, is_active)
--      values ('PASTE-USER-UUID-HERE', 'Your Name', 'you@example.com', 'admin', true);
--
--   After that, sign in to the app and create other users from the Users screen (Admin only).
--
-- OPTIONAL SAMPLE DATA (safe to skip): a few suppliers and projects to click around with.
insert into suppliers (business_name, tin, address, contact_number) values
  ('Sample Hardware Trading', '000-111-222-000', '12 Rizal St., Davao City', '0917 000 0001'),
  ('Sample Office Supplies', '000-333-444-000', '45 Quezon Ave., Davao City', '0917 000 0002')
on conflict do nothing;
insert into projects (name, code, fund_source) values
  ('Office Renovation', 'PRJ-001', 'General Fund'),
  ('Community Outreach', 'PRJ-002', 'Donations')
on conflict do nothing;
