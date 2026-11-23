package com.bred.rms.local;

import com.bred.rms.models.Attachment;
import com.bred.rms.repositories.AttachmentException;
import com.bred.rms.repositories.AttachmentRules;
import com.bred.rms.repositories.AttachmentStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.stream.Stream;

/** Keeps attachment files in an {@code attachments} folder next to the data file. */
public final class LocalAttachmentStore implements AttachmentStore {
    private final Path root;

    /** Creates the store; files live under {@code dataDir/attachments}. */
    public LocalAttachmentStore(Path dataDir) { this.root = dataDir.resolve("attachments").toAbsolutePath().normalize(); }

    @Override
    public Attachment upload(UUID receiptId, Path source) throws AttachmentException {
        AttachmentRules.check(source);
        String name = source.getFileName().toString();
        String safe = name.replaceAll("[^A-Za-z0-9._-]", "_");
        String storagePath = receiptId + "/" + UUID.randomUUID() + "-" + safe;
        try {
            Path target = resolve(storagePath);
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new AttachmentException("Couldn't save the file to the data folder: " + e.getMessage());
        }
        return new Attachment(name, storagePath);
    }

    @Override
    public byte[] download(Attachment attachment) throws AttachmentException {
        Path file = resolve(attachment.storagePath());
        if (!Files.isRegularFile(file)) throw new AttachmentException("That attachment file is missing from the data folder.");
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new AttachmentException("Couldn't read the attachment: " + e.getMessage());
        }
    }

    @Override
    public void remove(Attachment attachment) throws AttachmentException {
        Path file = resolve(attachment.storagePath());
        try {
            Files.deleteIfExists(file);
            Path parent = file.getParent();
            if (parent != null && !parent.equals(root) && Files.isDirectory(parent)) {
                try (Stream<Path> left = Files.list(parent)) {
                    if (left.findAny().isEmpty()) Files.delete(parent);
                }
            }
        } catch (IOException e) {
            throw new AttachmentException("Couldn't remove the attachment file: " + e.getMessage());
        }
    }

    /** Maps a stored path to a file, refusing anything that points outside the attachments folder. */
    private Path resolve(String storagePath) throws AttachmentException {
        Path p = root.resolve(storagePath == null ? "" : storagePath).normalize();
        if (!p.startsWith(root) || p.equals(root)) throw new AttachmentException("That attachment path is not valid.");
        return p;
    }
}
