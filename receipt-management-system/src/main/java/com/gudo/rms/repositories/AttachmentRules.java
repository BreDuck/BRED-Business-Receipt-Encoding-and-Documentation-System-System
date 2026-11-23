package com.gudo.rms.repositories;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Client-side checks run before any upload: type, size and readability. */
public final class AttachmentRules {
    public static final long MAX_BYTES = 10L * 1024 * 1024;

    private AttachmentRules() { }

    /** Throws an {@link AttachmentException} with a friendly message when the file can't be attached. */
    public static void check(Path file) throws AttachmentException {
        if (file == null || !Files.isRegularFile(file) || !Files.isReadable(file)) {
            throw new AttachmentException("Can't read that file. Check that it still exists.");
        }
        if (contentType(file.getFileName().toString()) == null) {
            throw new AttachmentException("Attachments must be png, jpg or pdf files.");
        }
        try {
            if (Files.size(file) > MAX_BYTES) throw new AttachmentException("That file is over 10 MB. Please use a smaller scan.");
        } catch (IOException e) {
            throw new AttachmentException("Can't read that file. Check that it still exists.");
        }
    }

    /** Returns the MIME type for an allowed file name, or null when the type isn't allowed. */
    public static String contentType(String fileName) {
        String n = fileName.toLowerCase(Locale.ROOT);
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        return null;
    }
}
