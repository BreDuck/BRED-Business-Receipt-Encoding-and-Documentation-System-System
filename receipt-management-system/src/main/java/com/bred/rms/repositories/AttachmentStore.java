package com.bred.rms.repositories;

import com.bred.rms.models.Attachment;

import java.nio.file.Path;
import java.util.UUID;

/** Keeps scanned copies of receipts. */
public interface AttachmentStore {
    /** Checks and uploads a file for the receipt; returns the record to save with the receipt. */
    Attachment upload(UUID receiptId, Path source) throws AttachmentException;

    byte[] download(Attachment attachment) throws AttachmentException;

    /** Removes a stored file. Best effort: failures are reported, never fatal to the caller. */
    void remove(Attachment attachment) throws AttachmentException;
}
