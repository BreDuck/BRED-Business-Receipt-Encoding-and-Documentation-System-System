package com.bred.rms.repositories.supabase;

import com.fasterxml.jackson.databind.JsonNode;
import com.bred.rms.models.Company;
import com.bred.rms.repositories.CompanyRepository;
import com.bred.rms.repositories.RepositoryException;
import com.bred.rms.supabase.RestClient;
import com.bred.rms.supabase.SupabaseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Reads and manages all companies through Creator-protected company policies. */
public final class SupabaseCompanyRepository implements CompanyRepository {
    private final RestClient rest;

    public SupabaseCompanyRepository(RestClient rest) {
        this.rest = rest;
    }

    @Override
    public List<Company> findAll() {
        try {
            List<Company> companies = new ArrayList<>();
            for (JsonNode row : rest.selectAll("companies", "select=id,name&order=name.asc,id.asc")) {
                companies.add(new Company(Json.uuid(row, "id"), Json.text(row, "name")));
            }
            return List.copyOf(companies);
        } catch (SupabaseException e) {
            throw SupabaseErrors.translate(e);
        }
    }

    @Override
    public void update(UUID id, String name) {
        try {
            JsonNode updated = rest.patch("companies", "id=eq." + id, Map.of("name", name));
            if (!updated.isArray() || updated.isEmpty()) {
                throw new RepositoryException("That company no longer exists.", false);
            }
        } catch (SupabaseException e) {
            if (e.status() == 403) {
                throw new RepositoryException(
                        "Creator company management requires database migration 011 and an active Creator account.",
                        false);
            }
            throw SupabaseErrors.translate(e);
        }
    }

    @Override
    public void delete(UUID id) {
        try {
            JsonNode deleted = rest.delete("companies", "id=eq." + id);
            if (!deleted.isArray() || deleted.isEmpty()) {
                throw new RepositoryException("That company no longer exists.", false);
            }
        } catch (SupabaseException e) {
            if (e.status() == 403) {
                throw new RepositoryException(
                        "Creator company management requires database migration 011 and an active Creator account.",
                        false);
            }
            throw SupabaseErrors.translate(e);
        }
    }
}
