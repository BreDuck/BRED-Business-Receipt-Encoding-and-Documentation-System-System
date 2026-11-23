# Setup guide: sharing data through Supabase (optional)

You do **not** need any of this to use the app on one computer: just run it (see README.md). Follow this guide only when
several computers should share the same receipts.

Takes about 20 minutes. You need a free Supabase account, Java 21+ and Maven on the computer that runs the app.

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

## 3. Create the first admin
1. **Authentication → Users → Add user → Create new user.** Enter your email and a password, and tick **Auto Confirm User**.
2. Click the new user and copy its **UUID**.
3. In **SQL Editor** run (replace the three values):
   ```sql
   insert into profiles (id, full_name, email, role, is_active)
   values ('PASTE-USER-UUID-HERE', 'Your Name', 'you@example.com', 'admin', true);
   ```
4. Optional: run `database/seed_data.sql`'s sample section for a few example suppliers and projects.

## 4. Deploy the "create user" function
This lets admins add accounts from inside the app. It keeps the powerful service key on the server.
1. Install the Supabase CLI (<https://supabase.com/docs/guides/cli>).
2. In the project folder run:
   ```
   supabase login
   supabase link --project-ref YOUR-PROJECT-REF
   supabase functions deploy admin-create-user
   ```
   `YOUR-PROJECT-REF` is the part before `.supabase.co` in your project URL.
   The function receives `SUPABASE_URL`, `SUPABASE_ANON_KEY` and `SUPABASE_SERVICE_ROLE_KEY` automatically; you do not set anything.

## 5. Point the app at your project
1. In Supabase open **Project Settings → API**. Copy the **Project URL** and the **anon / publishable** key.
2. Copy `application.properties.example` to `application.properties` next to the app and fill in both values.
   (Alternatively set the environment variables `RMS_SUPABASE_URL` and `RMS_SUPABASE_ANON_KEY`.)
3. **Never** put the `service_role` or secret key in the app. The app refuses to start with one.

## 6. Run
```
mvn -q exec:java                  # run from source
mvn -q package                    # or build a single jar…
java -jar target/rms-0.1.0-SNAPSHOT.jar
```
Copy the jar and the same `application.properties` (project URL and public anon/publishable key) to the other computers.
Create a separate Supabase Auth account for each employee. After sign-in, the app header should show **Shared cloud**;
if it says **Local only**, that computer is not connected to the shared project and its receipts will not appear for coworkers.
In the Receipts directory, use **Refresh** to load records added by other users while the screen is open.

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
- [ ] Sign in as the Encoder: no Users or Activity Log in the menu, no Delete button on receipts, Add/Edit works.
- [ ] Back as Admin: deleting a receipt works, and the Activity Log shows the actions.
- [ ] **Reports → Export CSV** opens correctly in Excel.

If something fails, note the message shown on screen and which step it happened on.
