package com.bred.rms.config;

import com.bred.rms.models.User;
import com.bred.rms.repositories.ActivityLogRepository;
import com.bred.rms.repositories.BugReportRepository;
import com.bred.rms.repositories.AttachmentStore;
import com.bred.rms.repositories.UserRepository;
import com.bred.rms.repositories.supabase.SupabaseActivityLogRepository;
import com.bred.rms.repositories.supabase.SupabaseBugReportRepository;
import com.bred.rms.repositories.supabase.SupabaseAttachmentStore;
import com.bred.rms.repositories.supabase.SupabaseProjectRepository;
import com.bred.rms.repositories.supabase.SupabaseReceiptRepository;
import com.bred.rms.repositories.supabase.SupabaseSupplierRepository;
import com.bred.rms.repositories.supabase.SupabaseUserRepository;
import com.bred.rms.repositories.supabase.SupabaseLocalDataImporter;
import com.bred.rms.local.LocalAttachmentStore;
import com.bred.rms.local.LocalAuth;
import com.bred.rms.local.LocalDatabase;
import com.bred.rms.services.AuthApi;
import com.bred.rms.services.AuthService;
import com.bred.rms.services.BugReportService;
import com.bred.rms.services.CurrentUser;
import com.bred.rms.services.ProjectService;
import com.bred.rms.services.ReceiptService;
import com.bred.rms.services.ReportService;
import com.bred.rms.services.UserService;
import com.bred.rms.services.SupplierService;
import com.bred.rms.supabase.AuthClient;
import com.bred.rms.supabase.RestClient;
import com.bred.rms.supabase.SessionManager;
import com.bred.rms.supabase.SupabaseHttp;

/** Wires the application together once. Panels get what they need from here. */
public final class AppContext {
    public final AuthApi auth;
    public final CurrentUser currentUser;
    public final ReceiptService receipts;
    public final SupplierService suppliers;
    public final ProjectService projects;
    public final AttachmentStore attachments;
    public final UserRepository userRepository;
    public final ActivityLogRepository activityLog;
    public final ReportService reports;
    public final UserService users;
    public final BugReportService bugReports;
    /** One-way local-to-cloud migration, available only in the Supabase-backed app. */
    public final SupabaseLocalDataImporter localDataImporter;
    /** Where data lives, for display. */
    public final String storageLabel;
    /** Optional first-run hint shown on the sign-in screen; null when not needed. */
    public final String loginHint;

    private AppContext(AuthApi auth, CurrentUser currentUser, ReceiptService receipts, SupplierService suppliers,
                       ProjectService projects, AttachmentStore attachments, UserRepository userRepository,
                       ActivityLogRepository activityLog, String storageLabel, String loginHint,
                       SupabaseLocalDataImporter localDataImporter, BugReportRepository bugReportRepository) {
        this.auth = auth;
        this.storageLabel = storageLabel;
        this.loginHint = loginHint;
        this.localDataImporter = localDataImporter;
        this.currentUser = currentUser;
        this.receipts = receipts;
        this.suppliers = suppliers;
        this.projects = projects;
        this.attachments = attachments;
        this.userRepository = userRepository;
        this.activityLog = activityLog;
        this.reports = new ReportService(receipts, suppliers, projects);
        this.users = new UserService(userRepository, currentUser);
        this.bugReports = bugReportRepository == null ? null : new BugReportService(bugReportRepository, currentUser);
    }

    /** Builds the real, Supabase-backed application. {@code config} must be configured. */
    public static AppContext create(AppConfig config) {
        SupabaseHttp http = new SupabaseHttp(config);
        AuthClient authClient = new AuthClient(http);
        SessionManager sessions = new SessionManager(authClient);
        RestClient rest = new RestClient(http, sessions);
        var receiptRepo = new SupabaseReceiptRepository(rest);
        var supplierRepo = new SupabaseSupplierRepository(rest);
        var projectRepo = new SupabaseProjectRepository(rest);
        var userRepo = new SupabaseUserRepository(rest);
        var attachments = new SupabaseAttachmentStore(rest);
        var localDataImporter = new SupabaseLocalDataImporter(rest, receiptRepo, attachments);
        AuthService auth = new AuthService(authClient, sessions, userRepo);
        CurrentUser who = auth::current;
        return new AppContext(auth, who, new ReceiptService(receiptRepo, supplierRepo, projectRepo, attachments, who),
                new SupplierService(supplierRepo, receiptRepo, who), new ProjectService(projectRepo, receiptRepo, who),
                attachments, userRepo, new SupabaseActivityLogRepository(rest), "Supabase project " + config.supabaseUrl(), null,
                localDataImporter, new SupabaseBugReportRepository(rest));
    }

    /**
     * Builds the application on this computer: everything is stored in {@code dataDir} and no internet or Supabase is needed.
     * A damaged data file raises an {@link IllegalStateException} with a message for the user.
     */
    public static AppContext createLocal(java.nio.file.Path dataDir) {
        LocalDatabase db = LocalDatabase.open(dataDir);
        LocalAuth auth = new LocalAuth(db);
        CurrentUser who = auth::current;
        db.bindCurrentUser(who);
        var receiptRepo = db.receipts();
        var supplierRepo = db.suppliers();
        var projectRepo = db.projects();
        var attachments = new LocalAttachmentStore(dataDir);
        String hint = db.defaultAdminPasswordInUse()
                ? "First time here? Sign in with " + LocalDatabase.DEFAULT_ADMIN_EMAIL + " and password admin123, then change it under Settings."
                : null;
        return new AppContext(auth, who, new ReceiptService(receiptRepo, supplierRepo, projectRepo, attachments, who),
                new SupplierService(supplierRepo, receiptRepo, who), new ProjectService(projectRepo, receiptRepo, who),
                attachments, db.users(), db.activityLog(), "This computer: " + db.directory().toAbsolutePath(), hint, null, null);
    }

    /** Builds a context around supplied services (used by UI tests). */
    public static AppContext forTesting(User user, ReceiptService receipts, SupplierService suppliers,
                                        ProjectService projects, AttachmentStore attachments) {
        return new AppContext(null, () -> user, receipts, suppliers, projects, attachments, null, null, "Test data", null, null, null);
    }

    /** Like {@link #forTesting(User, ReceiptService, SupplierService, ProjectService, AttachmentStore)} but with user and activity data. */
    public static AppContext forTesting(User user, ReceiptService receipts, SupplierService suppliers, ProjectService projects,
                                        AttachmentStore attachments, UserRepository userRepository, ActivityLogRepository activityLog) {
        return new AppContext(null, () -> user, receipts, suppliers, projects, attachments, userRepository, activityLog,
                "Test data", null, null, null);
    }
}
