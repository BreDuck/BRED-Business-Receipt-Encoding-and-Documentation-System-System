# Setup guide: run BRED with Supabase

The app requires Supabase; it does not silently create or use a local database. You need a Supabase account, Java 21+
and Maven on the computer that runs the app.

## 1. Create the Supabase project
1. Go to <https://supabase.com/dashboard> and click **New project**. This must be a **new** project, not the one from any older app.
2. Pick a name, a strong database password (save it somewhere) and the region closest to you. Wait until it finishes setting up.

## 2. Create the tables, rules and storage
1. In the project, open **SQL Editor**.
2. Open each file below from the `database/migrations/` folder, paste it into a new query and press **Run**. Do them **in this order** and wait for "Success" each time:
   1. `001_schema.sql`: tables
   2. `002_rls.sql`: who may read and write what
   3. `003_rpc.sql`: the save/delete/update functions
   4. `004_storage.sql`: the private `receipts` bucket for attachments
   5. `005_local_import.sql`: idempotency key for imported activity history
   6. `006_account_approval.sql`: pending account status, review RPCs and protected profile access

   The existing `companies` table must have UUID `id`, `name`, and boolean `is_active` columns. Only active companies
   are offered on the registration form; the migration links each application to its company.

## 3. Create the first admin
1. **Authentication → Users → Add user → Create new user.** Enter your email and a password, and tick **Auto Confirm User**.
2. Click the new user and copy its **UUID**.
3. In **SQL Editor** run (replace the three values):
   ```sql
   insert into profiles (id, full_name, email, role, is_active, account_status)
   values ('PASTE-USER-UUID-HERE', 'Your Name', 'you@example.com', 'admin', true, 'APPROVED');
   ```
4. Optional: run `database/seed_data.sql`'s sample section for a few example suppliers and projects.

## 4. Deploy the "create user" function
This lets admins add accounts from inside the app. It keeps the powerful service key on the server.
1. Install Node.js/npm if needed. You can run the Supabase CLI through `npx` without installing a global `supabase` command.
2. Open PowerShell in the BRED project folder (the folder containing `supabase\config.toml` and `supabase\functions\`), then run:
   ```
   npx supabase login
   npx supabase link --project-ref YOUR-PROJECT-REF
   npx supabase functions deploy admin-create-user
   npx supabase functions deploy admin-import-activity
   npx supabase functions deploy register-account
   npx supabase functions deploy review-account
   ```
   `YOUR-PROJECT-REF` is the part before `.supabase.co` in your project URL.
   Registration requires the `register-account` function to be deployed. If the registration screen reports HTTP 404,
   the function is missing or an older version is deployed; rerun the `register-account` deploy command above from this
   folder. If `npx` cannot find Node.js, install Node.js first and reopen PowerShell.
   The functions receive `SUPABASE_URL`, `SUPABASE_ANON_KEY` and `SUPABASE_SERVICE_ROLE_KEY` automatically; you do not set
   anything. `supabase/config.toml` disables gateway JWT verification only for `register-account`; that function uses the
   service role for listing active company names and creating a pending encoder profile. The SQL function rechecks that
   the selected company is active. Keep the service-role key in Supabase's function secrets only; never put it in
   `application.properties`.

## 5. Point the app at your project
1. In Supabase open **Project Settings → API**. Copy the **Project URL** and the **anon / publishable** key.
2. Copy `application.properties.example` to `application.properties` next to the app and fill in both values.
   (Alternatively set the environment variables `RMS_SUPABASE_URL` and `RMS_SUPABASE_ANON_KEY`.)
3. **Never** put the `service_role` or secret key in the app. The app refuses to start with one.

## 6. Run
```
mvn -q exec:java                  # run from source
mvn -q package                    # or build a single jar…
java -jar target/bred-0.1.0-SNAPSHOT.jar
```
Copy the jar and the same `application.properties` (project URL and public anon/publishable key) to the other computers.
Create a separate Supabase Auth account for each employee. In the Receipts directory, use **Refresh** to load records
added by other users while the screen is open.

## Import existing local data
If you have a previous `RMS-Data` folder, sign in as an admin, open **Settings**, and choose **Import local data into
Supabase**. Select the folder containing `data.json`. The importer copies suppliers, projects, receipts, attachments and
activity history. Matching local activity users are linked to Supabase accounts by email; unmatched activity keeps its
display name without a user link. Local user accounts and password hashes cannot be transferred to Supabase Auth, so
recreate those users from **Users**. Imported receipts are attributed to the admin performing the import. The source
folder is left unchanged; imports can be retried after a partial failure.

## First live run checklist
This app was built and tested without access to a live Supabase project (unit tests, a fake server and a throwaway PostgreSQL were used). Please walk through this once on your real project:

- [ ] The app starts and shows the sign-in screen (not the setup screen).
- [ ] Sign in with the admin account from step 3.
- [ ] **Suppliers → Add supplier** (name and TIN required) saves and appears in the list.
- [ ] **Projects → Add project** saves.
- [ ] **Receipts → Add receipt** with two line items and a PDF attachment saves; the paper-style view opens afterwards.
- [ ] Open the attachment from the receipt view.
- [ ] Adding the same receipt number for the same supplier again is refused.
- [ ] On a second computer (or a second run) sign in and see the same receipt.
- [ ] **Users → Add user** creates an Encoder account (tests the Edge Function).
- [ ] From the sign-in screen, **Create Account** submits a request and displays the approval message.
- [ ] The request appears in **Users** with **PENDING** status; its password cannot sign in before approval.
- [ ] Approving enables sign-in; rejecting keeps the account blocked.
- [ ] Sign in as the Encoder: no Users or Activity Log in the menu, no Delete button on receipts, Add/Edit works.
- [ ] Back as Admin: deleting a receipt works, and the Activity Log shows the actions.
- [ ] **Reports → Export CSV** opens correctly in Excel.

If something fails, note the message shown on screen and which step it happened on.
