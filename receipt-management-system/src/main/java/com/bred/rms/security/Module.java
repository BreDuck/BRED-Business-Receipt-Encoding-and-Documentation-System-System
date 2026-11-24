package com.bred.rms.security;

import com.bred.rms.models.Role;

/** The screens of the application, in menu order. */
public enum Module {
    DASHBOARD("Dashboard", false), MESSAGES("Custom Messages", false), RECEIPTS("Receipts", false),
    SUPPLIERS("Suppliers", false), PROJECTS("Projects", false),
    REPORTS("Reports", false), USERS("Users", true), ACTIVITY_LOG("Activity Log", true), SETTINGS("Settings", false);

    private final String title;
    private final boolean adminOnly;

    Module(String title, boolean adminOnly) {
        this.title = title;
        this.adminOnly = adminOnly;
    }

    public String title() { return title; }

    public boolean allows(Role role) { return !adminOnly || role != null && role.canManageCompany(); }
}
