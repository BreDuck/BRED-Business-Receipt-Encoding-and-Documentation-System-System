# Architecture

## Layers
```
Swing UI (com.gudo.rms.ui)         screens, dialogs, no network code
   ↓
Services (…services)               rules: validation, permissions, totals, reports
   ↓
Repository interfaces (…repositories)
   ↓
Supabase implementations (…repositories.supabase) → RestClient / AuthClient (…supabase)
```
The UI only talks to services, services only to repository interfaces, so all Supabase code lives in two packages and
the rest can be tested with in-memory fakes.

## Two storage modes
`AppContext` wires one of two sets of repositories behind the same interfaces, so the UI and services never know which:
- **Local (default, no configuration):** `com.gudo.rms.local`. `LocalDatabase` keeps everything in `data.json` (written through a temporary file, previous version kept as `data.json.bak`, passwords stored as salted PBKDF2 hashes), `LocalAttachmentStore` keeps scans under `attachments/`, `LocalAuth` signs in against the stored accounts. First run creates `admin@rms.local`. It enforces the same rules the database does (duplicate receipts, last active administrator, unique email).
- **Supabase:** used when the Supabase URL and anon key are configured; described below.

## Data and security (Supabase mode)
- **Supabase is the data store in this mode.**
- **Auth:** Supabase Auth (email + password). The role (`admin` or `encoder`) comes from the `profiles` table, never from the client.
- **Row Level Security is the real security boundary.** The UI hides buttons by role for convenience, the database refuses what the role may not do. Reads need an active account. Receipts and their items are written only through RPCs. Deleting needs admin. The activity log is readable by admins only.
- **RPCs** (`save_receipt`, `delete_receipt`, `admin_update_profile`) run as `SECURITY DEFINER`: a receipt and its items are saved atomically, duplicates (same receipt number + same supplier, ignoring case and spaces) are refused, the last active admin cannot be demoted or deactivated, and every change is written to `activity_log`.
- **Attachments** go to the private Storage bucket `receipts` under `<receipt id>/<uuid>-<file name>` and are downloaded with the user's own token. Files are uploaded when Save is pressed. Encoders cannot delete Storage objects, so an attachment removed during an edit may stay behind as an unreferenced file; they can be cleaned up by hand in the Supabase dashboard (Storage → receipts).
- **Creating users** goes through the `admin-create-user` Edge Function. The service-role key exists only there; the desktop app holds just the public anon key and refuses a secret key at start-up.

## Money
Amounts are `BigDecimal` with two decimals, rounded half-up. Subtotal = Σ quantity × unit price. Grand total = subtotal − discount + VAT + additional charges. VAT defaults to 12% of (subtotal − discount) and can be overridden by hand.

## Layout
| Path | What is there |
|---|---|
| `src/main/java/com/gudo/rms/models` | Plain records (Receipt, Supplier, Project, User…) and money maths |
| `…/services` | ReceiptService, SupplierService, ProjectService, UserService, ReportService, AuthService |
| `…/repositories` | Interfaces plus `supabase/` implementations |
| `…/supabase` | HTTP client, session handling, Auth client, config |
| `…/ui` | Shell (sidebar + header), panels per module, theme |
| `database/migrations` | `001`–`004` SQL, run in order |
| `database/test`, `database/validate.sh` | SQL assertions run on a throwaway PostgreSQL |
| `supabase/functions/admin-create-user` | The Edge Function |
