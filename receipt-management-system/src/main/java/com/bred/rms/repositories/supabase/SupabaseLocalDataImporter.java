package com.bred.rms.repositories.supabase;

import com.fasterxml.jackson.databind.JsonNode;
import com.bred.rms.local.LocalAttachmentStore;
import com.bred.rms.models.Attachment;
import com.bred.rms.models.Receipt;
import com.bred.rms.models.ReceiptItem;
import com.bred.rms.models.ReceiptType;
import com.bred.rms.repositories.AttachmentException;
import com.bred.rms.repositories.RepositoryException;
import com.bred.rms.supabase.RestClient;
import com.bred.rms.supabase.SupabaseException;
import com.bred.rms.supabase.SupabaseHttp;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Imports an existing local data folder into the signed-in Supabase project. */
public final class SupabaseLocalDataImporter {
    private static final int ACTIVITY_BATCH_SIZE = 200;

    public record Result(int suppliers, int projects, int receipts, int attachments, int activityEntries, int localUsers) { }

    private final RestClient rest;
    private final SupabaseReceiptRepository receipts;
    private final SupabaseAttachmentStore cloudAttachments;

    public SupabaseLocalDataImporter(RestClient rest, SupabaseReceiptRepository receipts,
                                     SupabaseAttachmentStore cloudAttachments) {
        this.rest = rest;
        this.receipts = receipts;
        this.cloudAttachments = cloudAttachments;
    }

    public Result importDirectory(Path dataDirectory) throws AttachmentException {
        Path directory = dataDirectory.toAbsolutePath().normalize();
        Path dataFile = directory.resolve("data.json");
        JsonNode root;
        try {
            if (!Files.isRegularFile(dataFile)) {
                throw new RepositoryException("No data.json was found in the selected folder.", false);
            }
            root = SupabaseHttp.JSON.readTree(Files.readAllBytes(dataFile));
        } catch (IOException e) {
            throw new RepositoryException("Could not read the local data file: " + e.getMessage(), false);
        }
        if (root == null || !root.isObject() || root.path("version").asInt(-1) != 1) {
            throw new RepositoryException("The selected data.json is not a supported BRED data file.", false);
        }
        requireArray(root, "users");
        requireArray(root, "suppliers");
        requireArray(root, "projects");
        requireArray(root, "receipts");
        requireArray(root, "activity");

        LocalAttachmentStore localAttachments = new LocalAttachmentStore(directory);
        Map<UUID, UUID> userIds = loadCloudUserIds(root.path("users"));
        int supplierCount = importSuppliers(root.path("suppliers"));
        int projectCount = importProjects(root.path("projects"));
        int[] receiptCounts = importReceipts(root.path("receipts"), localAttachments);
        int activityCount = importActivity(root.path("activity"), userIds);
        return new Result(supplierCount, projectCount, receiptCounts[0], receiptCounts[1],
                activityCount, root.path("users").size());
    }

    private Map<UUID, UUID> loadCloudUserIds(JsonNode localUsers) {
        try {
            Map<String, UUID> byEmail = new LinkedHashMap<>();
            for (JsonNode profile : rest.selectAll("profiles", "select=id,email")) {
                byEmail.put(profile.path("email").asText().trim().toLowerCase(Locale.ROOT),
                        UUID.fromString(profile.path("id").asText()));
            }
            Map<UUID, UUID> mapped = new LinkedHashMap<>();
            for (JsonNode user : localUsers) {
                UUID cloudId = byEmail.get(text(user, "email").trim().toLowerCase(Locale.ROOT));
                if (cloudId != null) mapped.put(uuid(user, "id"), cloudId);
            }
            return mapped;
        } catch (SupabaseException e) {
            throw SupabaseErrors.translate(e);
        }
    }

    private int importSuppliers(JsonNode rows) {
        for (JsonNode row : rows) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("id", uuid(row, "id").toString());
            values.put("business_name", text(row, "businessName"));
            values.put("tin", text(row, "tin"));
            values.put("address", text(row, "address"));
            values.put("contact_number", text(row, "contactNumber"));
            upsert("suppliers", values);
        }
        return rows.size();
    }

    private int importProjects(JsonNode rows) {
        for (JsonNode row : rows) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("id", uuid(row, "id").toString());
            values.put("name", text(row, "name"));
            values.put("code", text(row, "code"));
            values.put("fund_source", text(row, "fundSource"));
            upsert("projects", values);
        }
        return rows.size();
    }

    private int[] importReceipts(JsonNode rows, LocalAttachmentStore localAttachments) throws AttachmentException {
        int attachmentCount = 0;
        for (JsonNode row : rows) {
            UUID receiptId = uuid(row, "id");
            List<Attachment> attachments = new ArrayList<>();
            for (JsonNode node : row.path("attachments")) {
                Attachment source = new Attachment(text(node, "fileName"), text(node, "storagePath"));
                byte[] data = localAttachments.download(source);
                attachments.add(cloudAttachments.importCopy(receiptId, source, data));
                attachmentCount++;
            }

            List<ReceiptItem> items = new ArrayList<>();
            for (JsonNode node : row.path("items")) {
                items.add(new ReceiptItem(text(node, "description"),
                        new BigDecimal(text(node, "quantity")), new BigDecimal(text(node, "unitPrice"))));
            }
            UUID projectId = nullableUuid(row.get("projectId"));
            Receipt receipt = new Receipt(receiptId, text(row, "receiptNo"),
                    LocalDate.parse(text(row, "date")), ReceiptType.fromDb(text(row, "type")),
                    uuid(row, "supplierId"), projectId, items,
                    decimal(row, "discount"), decimal(row, "vat"), decimal(row, "additionalCharges"),
                    attachments, null, instant(row, "createdAt"), instant(row, "modifiedAt"));
            receipts.save(receipt);
        }
        return new int[] {rows.size(), attachmentCount};
    }

    private int importActivity(JsonNode rows, Map<UUID, UUID> cloudUserIds) {
        if (rows.isEmpty()) return 0;

        int imported = 0;
        List<Map<String, Object>> batch = new ArrayList<>(ACTIVITY_BATCH_SIZE);
        int index = 0;
        for (JsonNode row : rows) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("source_key", activityKey(index++, row));
            entry.put("at", instant(row, "at").toString());
            UUID localUserId = nullableUuid(row.get("userId"));
            UUID cloudUserId = localUserId == null ? null : cloudUserIds.get(localUserId);
            entry.put("user_id", cloudUserId == null ? null : cloudUserId.toString());
            entry.put("user_name", text(row, "userName"));
            entry.put("action", text(row, "action"));
            entry.put("detail", text(row, "detail"));
            batch.add(entry);
            if (batch.size() == ACTIVITY_BATCH_SIZE) {
                invokeActivityImport(batch);
                imported += batch.size();
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            invokeActivityImport(batch);
            imported += batch.size();
        }
        return imported;
    }

    private void invokeActivityImport(List<Map<String, Object>> entries) {
        try {
            rest.invokeFunction("admin-import-activity", Map.of("entries", entries));
        } catch (SupabaseException e) {
            throw SupabaseErrors.translate(e);
        }
    }

    private void upsert(String table, Map<String, Object> values) {
        try {
            rest.upsert(table, "id", values);
        } catch (SupabaseException e) {
            throw SupabaseErrors.translate(e);
        }
    }

    private static void requireArray(JsonNode root, String field) {
        if (!root.path(field).isArray()) {
            throw new RepositoryException("The local data file is missing its " + field + " records.", false);
        }
    }

    private static UUID uuid(JsonNode node, String field) {
        try {
            return UUID.fromString(text(node, field));
        } catch (IllegalArgumentException e) {
            throw new RepositoryException("A local record has an invalid " + field + ".", false);
        }
    }

    private static UUID nullableUuid(JsonNode node) {
        if (node == null || node.isNull() || node.asText().isBlank()) return null;
        try {
            return UUID.fromString(node.asText());
        } catch (IllegalArgumentException e) {
            throw new RepositoryException("A local record has an invalid UUID.", false);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return "";
        return value.asText();
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        String value = text(node, field);
        return value.isBlank() ? BigDecimal.ZERO : new BigDecimal(value);
    }

    private static Instant instant(JsonNode node, String field) {
        try {
            return Instant.parse(text(node, field));
        } catch (RuntimeException e) {
            throw new RepositoryException("A local record has an invalid " + field + " timestamp.", false);
        }
    }

    private static String activityKey(int index, JsonNode row) {
        String canonical = index + "|" + text(row, "at") + "|" + text(row, "userId") + "|"
                + text(row, "userName") + "|" + text(row, "action") + "|" + text(row, "detail");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return "local-import:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }
}
