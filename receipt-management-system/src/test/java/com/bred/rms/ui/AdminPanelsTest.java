package com.bred.rms.ui;

import com.bred.rms.config.AppContext;
import com.bred.rms.models.ActivityEntry;
import com.bred.rms.models.Role;
import com.bred.rms.models.User;
import com.bred.rms.repositories.ActivityLogRepository;
import com.bred.rms.repositories.UserRepository;
import com.bred.rms.security.Module;
import com.bred.rms.services.ProjectService;
import com.bred.rms.services.ReceiptService;
import com.bred.rms.services.SupplierService;
import com.bred.rms.support.Fakes;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class AdminPanelsTest {
    @Test
    void adminScreensBuildOverFakeData() {
        User admin = Fakes.admin();
        UserRepository users = new UserRepository() {
            @Override public List<User> findAll() { return List.of(admin); }
            @Override public Optional<User> findById(UUID id) { return Optional.of(admin); }
            @Override public User create(String email, String fullName, Role role, char[] password) { return admin; }
            @Override public void update(UUID id, String fullName, Role role, boolean active) { }
        };
        ActivityLogRepository log = () -> List.of(new ActivityEntry(Instant.now(), admin.id(), "Ada Admin", "receipts.insert", "OR-1"));
        var receipts = new Fakes.Receipts();
        var suppliers = new Fakes.Suppliers();
        var projects = new Fakes.Projects();
        var attachments = new Fakes.Attachments();
        AppContext ctx = AppContext.forTesting(admin, new ReceiptService(receipts, suppliers, projects, attachments, () -> admin),
                new SupplierService(suppliers, receipts, () -> admin), new ProjectService(projects, receipts, () -> admin),
                attachments, users, log);
        Shell shell = new Shell(ctx, () -> { });
        assertDoesNotThrow(() -> shell.show(Module.USERS));
        assertDoesNotThrow(() -> shell.show(Module.ACTIVITY_LOG));
        assertDoesNotThrow(() -> shell.show(Module.SETTINGS));
        assertDoesNotThrow(() -> shell.show(Module.SUPPLIERS));
        assertDoesNotThrow(() -> shell.show(Module.PROJECTS));
    }
}
