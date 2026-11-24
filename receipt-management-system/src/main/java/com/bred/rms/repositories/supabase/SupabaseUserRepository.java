package com.bred.rms.repositories.supabase;

import com.fasterxml.jackson.databind.JsonNode;
import com.bred.rms.models.AccountStatus;
import com.bred.rms.models.Company;
import com.bred.rms.models.Role;
import com.bred.rms.models.User;
import com.bred.rms.repositories.RepositoryException;
import com.bred.rms.repositories.UserRepository;
import com.bred.rms.supabase.RestClient;
import com.bred.rms.supabase.SupabaseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Profiles over PostgREST; account creation through the admin-create-user Edge Function. */
public final class SupabaseUserRepository implements UserRepository {
    private static final String COLUMNS = "select=id,full_name,email,role,is_active,account_status,created_at,companies(name)";
    private final RestClient rest;

    public SupabaseUserRepository(RestClient rest) { this.rest = rest; }

    @Override public List<User> findAll() {
        try {
            List<User> out = new ArrayList<>();
            for (JsonNode n : rest.selectAll("profiles", COLUMNS + "&order=full_name.asc,id.asc")) out.add(map(n));
            return out;
        } catch (SupabaseException e) { throw SupabaseErrors.translate(e); }
    }

    @Override public Optional<User> findById(UUID id) {
        try {
            JsonNode rows = rest.select("profiles", COLUMNS + "&id=eq." + id);
            return rows.isArray() && rows.size() > 0 ? Optional.of(map(rows.get(0))) : Optional.empty();
        } catch (SupabaseException e) { throw SupabaseErrors.translate(e); }
    }

    @Override public User create(String email, String fullName, Role role, char[] password) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email.trim());
        body.put("password", new String(password));
        body.put("full_name", fullName.trim());
        body.put("role", role.dbValue());
        try {
            JsonNode res = rest.invokeFunction("admin-create-user", body);
            if (!res.hasNonNull("id")) throw new RepositoryException("The account could not be created.", false);
            return new User(UUID.fromString(res.get("id").asText()), fullName.trim(), email.trim(), role, true,
                    AccountStatus.APPROVED, java.time.Instant.now());
        } catch (SupabaseException e) { throw SupabaseErrors.translate(e); }
    }

    @Override public void registerAccount(String fullName, String email, UUID companyId, char[] password) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("full_name", fullName.trim());
        body.put("email", email.trim());
        body.put("password", new String(password));
        body.put("company_id", companyId.toString());
        try {
            rest.invokeFunction("register-account", body);
        } catch (SupabaseException e) { throw registrationFunctionError(e); }
    }

    @Override public List<Company> availableCompanies() {
        try {
            List<Company> companies = new ArrayList<>();
            for (JsonNode node : rest.getFunction("register-account", "mode=companies")) {
                companies.add(new Company(Json.uuid(node, "id"), Json.text(node, "name")));
            }
            return List.copyOf(companies);
        } catch (SupabaseException e) { throw registrationFunctionError(e); }
    }

    @Override public void reviewRegistration(UUID id, AccountStatus decision) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("account_id", id.toString());
        body.put("action", decision == AccountStatus.APPROVED ? "APPROVE" : "REJECT");
        try {
            rest.invokeFunction("review-account", body);
        } catch (SupabaseException e) { throw SupabaseErrors.translate(e); }
    }

    @Override public void update(UUID id, String fullName, Role role, boolean active) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("p_id", id.toString());
        args.put("p_full_name", fullName);
        args.put("p_role", role.dbValue());
        args.put("p_active", active);
        try {
            rest.rpc("admin_update_profile", args);
        } catch (SupabaseException e) { throw SupabaseErrors.translate(e); }
    }

    private static User map(JsonNode n) {
        return new User(Json.uuid(n, "id"), Json.text(n, "full_name"), Json.text(n, "email"),
                Role.fromDb(Json.text(n, "role")), !n.hasNonNull("is_active") || n.get("is_active").asBoolean(),
                AccountStatus.fromDb(Json.text(n, "account_status")),
                n.hasNonNull("created_at") ? Json.instant(n, "created_at") : java.time.Instant.EPOCH,
                n.path("companies").path("name").asText(""));
    }

    private static RuntimeException registrationFunctionError(SupabaseException e) {
        if (e.status() == 404) {
            return new RepositoryException(
                    "The register-account Supabase Function is missing or outdated. From the BRED project folder, run "
                            + "`npx supabase login`, `npx supabase link --project-ref YOUR-PROJECT-REF`, then "
                            + "`npx supabase functions deploy register-account`.",
                    false);
        }
        return SupabaseErrors.translate(e);
    }
}
