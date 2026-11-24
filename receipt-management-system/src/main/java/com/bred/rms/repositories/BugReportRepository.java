package com.bred.rms.repositories;

import com.bred.rms.models.BugReport;

import java.util.List;
import java.util.UUID;

/** Stores bug reports and their optional private image attachments. */
public interface BugReportRepository {
    void submit(UUID reporterId, String title, String description, String fileName, byte[] image, String contentType);
    List<BugReport> findAll();
    byte[] downloadAttachment(String path);
}
