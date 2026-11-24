package com.bred.rms.services;

import com.bred.rms.models.BugReport;
import com.bred.rms.models.Role;
import com.bred.rms.models.User;
import com.bred.rms.models.ValidationException;
import com.bred.rms.repositories.BugReportRepository;

import java.util.List;

/** Validates submissions and restricts report review to the Creator. */
public final class BugReportService {
    public static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;
    private final BugReportRepository reports;
    private final CurrentUser who;

    public BugReportService(BugReportRepository reports, CurrentUser who) {
        this.reports = reports;
        this.who = who;
    }

    public void submit(String title, String description, String fileName, byte[] image, String contentType) {
        User user = who.get();
        if (user == null || !user.active()) throw new SecurityException("Please sign in first.");
        if (Guard.blank(title)) throw new ValidationException("Enter a short report title.");
        if (title.trim().length() > 200) throw new ValidationException("The report title is too long.");
        if (Guard.blank(description)) throw new ValidationException("Describe the problem before submitting.");
        if (description.trim().length() > 10000) throw new ValidationException("The report description is too long.");
        if (image != null) {
            if (fileName == null || fileName.isBlank() || image.length == 0 || image.length > MAX_IMAGE_BYTES) {
                throw new ValidationException("Choose an image smaller than 8 MB.");
            }
            if (contentType == null || !contentType.toLowerCase(java.util.Locale.ROOT).startsWith("image/")) {
                throw new ValidationException("The optional attachment must be an image.");
            }
        }
        reports.submit(user.id(), title, description, fileName, image, contentType);
    }

    public List<BugReport> listForCreator() {
        User user = who.get();
        if (user == null || !user.active() || user.role() != Role.CREATOR) {
            throw new SecurityException("Only the Creator can view submitted bug reports.");
        }
        return reports.findAll();
    }

    public byte[] downloadAttachment(String path) {
        User user = who.get();
        if (user == null || !user.active() || user.role() != Role.CREATOR) {
            throw new SecurityException("Only the Creator can view submitted bug report images.");
        }
        if (Guard.blank(path) || path.contains("..")) throw new ValidationException("Invalid attachment path.");
        return reports.downloadAttachment(path);
    }
}
