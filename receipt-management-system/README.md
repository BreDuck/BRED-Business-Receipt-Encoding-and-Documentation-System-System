# Receipt Management System (RMS)

A desktop app for a small office to encode business receipts, keep them in one searchable directory, and open any
receipt in a layout that looks like the paper original.

It works **out of the box with no internet and no account**: data is kept in a folder on your computer. If you later
want several computers to share the same data, connect it to your own Supabase project (see SETUP.md).

## What it does
- **Receipts**: add, view, edit and delete (admins only) receipts with line items, VAT, discount, additional charges and a computed grand total in pesos (₱). Attach a photo or PDF (png, jpg, jpeg, pdf, up to 10 MB each).
- **Receipt detail**: click a receipt number to see it as a paper-style slip, with Edit, Delete (admins) and Open attachment.
- **Search and filter**: by receipt number, TIN, supplier, product description, date range, supplier or project.
- **Suppliers and Projects**: business name, TIN, address, contact; project name, code, fund source, with total spend per project.
- **Dashboard and Reports**: this month's receipts and spend, recent receipts, totals by project, supplier or month, CSV export.
- **Users and Activity Log** (admins): create accounts, change roles, deactivate people, see who changed what.

## Roles
| Role | Can do |
|---|---|
| Admin | Everything, including deleting receipts, managing users and reading the activity log |
| Encoder | Add and edit receipts, suppliers and projects; view the dashboard and reports. No deleting, no Users, no Activity Log |

There is no public sign-up. An admin creates every account.

## Getting started
1. Install Java 21 or newer and Maven.
2. Run the app: `mvn -q exec:java` (or `mvn -q package`, then `java -jar target/rms-0.1.0-SNAPSHOT.jar`). In VS Code, run `Main.java`.
3. Sign in with **admin@rms.local** and password **admin123**, then change the password under **Settings**.
4. Add a supplier, then a receipt. Create more accounts (Admin or Encoder) under **Users**.

**Where is my data?** In `RMS-Data` inside your user folder (for example `C:\Users\You\RMS-Data`): `data.json` (everything except files), `data.json.bak` (the previous save) and `attachments/` (scans). Set the environment variable `RMS_DATA_DIR` to use another folder. Back up that folder to back up the app. Only one copy of the app should use the folder at a time.

**Sharing between computers (optional):** follow [SETUP.md](SETUP.md) to use Supabase. When `application.properties` (or the `RMS_SUPABASE_*` variables) is filled in, the app uses Supabase instead of the local folder. Existing local data is not copied across.

## Learn more
- [ARCHITECTURE.md](ARCHITECTURE.md): how the code and the database fit together.
- `docs/superpowers/`: the design and the build plan this app was made from.

## Tests
`mvn -q test` runs the unit and UI smoke tests. `bash database/validate.sh` checks the SQL migrations against a throwaway local PostgreSQL (needs PostgreSQL server binaries installed).
