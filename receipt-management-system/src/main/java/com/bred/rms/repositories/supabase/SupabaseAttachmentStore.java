package com.bred.rms.repositories.supabase;

import com.bred.rms.models.Attachment;
import com.bred.rms.repositories.AttachmentException;
import com.bred.rms.repositories.AttachmentRules;
import com.bred.rms.repositories.AttachmentStore;
import com.bred.rms.supabase.RestClient;
import com.bred.rms.supabase.SupabaseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Receipt scans in the private "receipts" Storage bucket. */
public final class SupabaseAttachmentStore implements AttachmentStore {
    private static final String BUCKET = "receipts";
    private final RestClient rest;

    public SupabaseAttachmentStore(RestClient rest) { this.rest = rest; }

    /** Copies an existing local attachment to a stable Supabase path so repeated imports can be retried safely. */
    public Attachment importCopy(UUID receiptId, Attachment source, byte[] data) throws AttachmentException {
        String contentType = AttachmentRules.contentType(source.fileName());
        String path = source.storagePath();
        if (contentType == null || data.length > AttachmentRules.MAX_BYTES
                || !path.startsWith(receiptId + "/")
                || !path.matches("[A-Za-z0-9._/-]+")
                || path.contains("../") || path.endsWith("/..")) {
            throw new AttachmentException("That local attachment cannot be imported.");
        }
        try {
            rest.uploadObject(BUCKET, path, data, contentType, true);
        } catch (SupabaseException e) {
            throw new AttachmentException(describe(e, "import"));
        }
        return source;
    }

    @Override public Attachment upload(UUID receiptId, Path source) throws AttachmentException {
        AttachmentRules.check(source);
        byte[] data;
        try {
            data = Files.readAllBytes(source);
        } catch (IOException e) {
            throw new AttachmentException("Can't read that file. Check that it still exists.");
        }
        String display = source.getFileName().toString();
        String path = receiptId + "/" + UUID.randomUUID() + "-" + display.replaceAll("[^A-Za-z0-9._-]", "_");
        try {
            rest.uploadObject(BUCKET, path, data, AttachmentRules.contentType(display));
        } catch (SupabaseException e) {
            throw new AttachmentException(describe(e, "upload"));
        }
        return new Attachment(display, path);
    }

    @Override public byte[] download(Attachment a) throws AttachmentException {
        try {
            return rest.downloadObject(BUCKET, a.storagePath());
        } catch (SupabaseException e) {
            throw new AttachmentException(describe(e, "open"));
        }
    }

    @Override public void remove(Attachment a) throws AttachmentException {
        try {
            rest.deleteObject(BUCKET, a.storagePath());
        } catch (SupabaseException e) {
            throw new AttachmentException(describe(e, "remove"));
        }
    }

    private static String describe(SupabaseException e, String verb) {
        if (e.status() == 0) return SupabaseErrors.NETWORK;
        if (e.status() == 404) return "That file could not be found. It may have been removed.";
        if (e.status() == 401) return "Your session has expired. Please sign in again.";
        if (e.status() == 403) return "You don't have permission to " + verb + " that file.";
        return "Couldn't " + verb + " the file (" + e.getMessage() + ").";
    }
}
