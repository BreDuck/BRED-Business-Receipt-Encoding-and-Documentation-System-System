package com.gudo.rms.ui;

import com.gudo.rms.config.AppContext;
import com.gudo.rms.models.Role;
import com.gudo.rms.models.User;
import com.gudo.rms.services.ProjectService;
import com.gudo.rms.services.ReceiptService;
import com.gudo.rms.services.SupplierService;
import com.gudo.rms.support.Fakes;

import java.util.UUID;

/** Builds an AppContext over in-memory fakes for UI tests. Test-only. */
final class TestContexts {
    private TestContexts() { }

    static AppContext withUser(Role role) {
        User user = new User(UUID.randomUUID(), "Test User", "t@x.test", role, true);
        var receipts = new Fakes.Receipts();
        var suppliers = new Fakes.Suppliers();
        var projects = new Fakes.Projects();
        var attachments = new Fakes.Attachments();
        return AppContext.forTesting(user, new ReceiptService(receipts, suppliers, projects, attachments, () -> user),
                new SupplierService(suppliers, receipts, () -> user), new ProjectService(projects, receipts, () -> user), attachments);
    }
}
