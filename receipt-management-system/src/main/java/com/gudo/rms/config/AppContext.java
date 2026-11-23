package com.gudo.rms.config;

import com.gudo.rms.models.User;
import com.gudo.rms.repositories.ActivityLogRepository;
import com.gudo.rms.repositories.AttachmentStore;
import com.gudo.rms.repositories.UserRepository;
import com.gudo.rms.repositories.supabase.SupabaseActivityLogRepository;
import com.gudo.rms.repositories.supabase.SupabaseAttachmentStore;
import com.gudo.rms.repositories.supabase.SupabaseProjectRepository;
import com.gudo.rms.repositories.supabase.SupabaseReceiptRepository;
import com.gudo.rms.repositories.supabase.SupabaseSupplierRepository;
import com.gudo.rms.repositories.supabase.SupabaseUserRepository;
import com.gudo.rms.local.LocalAttachmentStore;
import com.gudo.rms.local.LocalAuth;
import com.gudo.rms.local.LocalDatabase;
import com.gudo.rms.services.AuthApi;
import com.gudo.rms.services.AuthService;
import com.gudo.rms.services.CurrentUser;
import com.gudo.rms.services.ProjectService;
import com.gudo.rms.services.ReceiptService;
import com.gudo.rms.services.ReportService;
import com.gudo.rms.services.UserService;
import com.gudo.rms.services.SupplierService;
import com.gudo.rms.supabase.AuthClient;
import com.gudo.rms.supabase.RestClient;
import com.gudo.rms.supabase.SessionManager;
import com.gudo.rms.supabase.SupabaseHttp;

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
    /** Where data lives, for display. */
    public final String storageLabel;
    /** Optional first-run hint shown on the sign-in screen; null when not needed. */
    public final String loginHint;

    private AppContext(AuthApi auth, CurrentUser currentUser, ReceiptService receipts, SupplierService suppliers,
                       ProjectService projects, AttachmentStore attachments, UserRepository userRepository,
                       ActivityLogRepository activityLog, String storageLabel, String loginHint) {
        this.auth = auth;
        this.storageLabel = storageLabel;
        this.loginHint = loginHint;
        this.currentUser = currentUser;
        this.receipts = receipts;
        this.suppliers = suppliers;
        this.projects = projects;
        this.attachments = attachments;
        this.userRepository = userRepository;
        this.activityLog = activityLog;
        this.reports = new ReportService(receipts, suppliers, projects);
        this.users = new UserService(userRepository, currentUser);
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
        AuthService auth = new AuthService(authClient, sessions, userRepo);
        CurrentUser who = auth::current;
        return new AppContext(auth, who, new ReceiptService(receiptRepo, supplierRepo, projectRepo, attachments, who),
                new SupplierService(supplierRepo, receiptRepo, who), new ProjectService(projectRepo, receiptRepo, who),
                attachments, userRepo, new SupabaseActivityLogRepository(rest), "Supabase project " + config.supabaseUrl(), null);
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
                attachments, db.users(), db.activityLog(), "This computer: " + db.directory().toAbsolutePath(), hint);
    }

    /** Builds a context around supplied services (used by UI tests). */
    public static AppContext forTesting(User user, ReceiptService receipts, SupplierService suppliers,
                                        ProjectService projects, AttachmentStore attachments) {
        return new AppContext(null, () -> user, receipts, suppliers, projects, attachments, null, null, "Test data", null);
    }

    /** Like {@link #forTesting(User, ReceiptService, SupplierService, ProjectService, AttachmentStore)} but with user and activity data. */
    public static AppContext forTesting(User user, ReceiptService receipts, SupplierService suppliers, ProjectService projects,
                                        AttachmentStore attachments, UserRepository userRepository, ActivityLogRepository activityLog) {
        return new AppContext(null, () -> user, receipts, suppliers, projects, attachments, userRepository, activityLog, "Test data", null);
    }
}
