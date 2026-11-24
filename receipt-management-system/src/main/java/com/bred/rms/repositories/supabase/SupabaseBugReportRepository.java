package com.bred.rms.repositories.supabase;

import com.fasterxml.jackson.databind.JsonNode;
import com.bred.rms.models.BugReport;
import com.bred.rms.repositories.BugReportRepository;
import com.bred.rms.repositories.RepositoryException;
import com.bred.rms.supabase.RestClient;
import com.bred.rms.supabase.SupabaseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Bug reports in PostgREST with optional files in the private bug-reports bucket. */
public final class SupabaseBugReportRepository implements BugReportRepository {
    private static final String BUCKET = "bug-reports";
    private final RestClient rest;

    public SupabaseBugReportRepository(RestClient rest) { this.rest = rest; }

    @Override public void submit(UUID reporterId, String title, String description, String fileName,
                                 byte[] image, String contentType) {
        String path = null;
        if (image != null) {
            path = reporterId + "/" + UUID.randomUUID() + "-" + fileName.replaceAll("[^A-Za-z0-9._-]", "_");
            try {
                rest.uploadObject(BUCKET, path, image, contentType);
            } catch (SupabaseException e) {
                throw SupabaseErrors.translate(e);
            }
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("reporter_id", reporterId.toString());
        row.put("title", title.trim());
        row.put("description", description.trim());
        row.put("attachment_path", path);
        try {
            JsonNode saved = rest.insert("bug_reports", row);
            if (!saved.isArray() || saved.isEmpty()) throw new RepositoryException("The bug report could not be saved.", false);
        } catch (SupabaseException e) {
            cleanup(path, e);
            throw SupabaseErrors.translate(e);
        } catch (RuntimeException e) {
            cleanup(path, e);
            throw e;
        }
    }

    private void cleanup(String path, Exception original) {
        if (path == null) return;
        try {
            rest.deleteObject(BUCKET, path);
        } catch (SupabaseException cleanupError) {
            original.addSuppressed(cleanupError);
        }
    }

    @Override public List<BugReport> findAll() {
        try {
            List<BugReport> out = new ArrayList<>();
            for (JsonNode row : rest.selectAll("bug_reports",
                    "select=*,profiles(full_name,email),companies(name)&order=created_at.desc,id.asc")) {
                out.add(new BugReport(Json.uuid(row, "id"), row.path("companies").path("name").asText(""),
                        row.path("profiles").path("full_name").asText(""), row.path("profiles").path("email").asText(""),
                        Json.text(row, "title"), Json.text(row, "description"),
                        Json.text(row, "attachment_path"), Json.instant(row, "created_at")));
            }
            return List.copyOf(out);
        } catch (SupabaseException e) {
            throw SupabaseErrors.translate(e);
        }
    }

    @Override public byte[] downloadAttachment(String path) {
        try {
            return rest.downloadObject(BUCKET, path);
        } catch (SupabaseException e) {
            throw SupabaseErrors.translate(e);
        }
    }
}
