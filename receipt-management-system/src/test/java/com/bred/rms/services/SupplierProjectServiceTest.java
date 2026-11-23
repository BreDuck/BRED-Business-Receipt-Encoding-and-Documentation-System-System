package com.bred.rms.services;

import com.bred.rms.models.Project;
import com.bred.rms.models.Supplier;
import com.bred.rms.models.User;
import com.bred.rms.models.ValidationException;
import com.bred.rms.support.Fakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupplierProjectServiceTest {
    Fakes.Receipts receipts;
    Fakes.Suppliers suppliers;
    Fakes.Projects projects;
    User current;
    SupplierService supplierService;
    ProjectService projectService;

    @BeforeEach
    void setUp() {
        receipts = new Fakes.Receipts();
        suppliers = new Fakes.Suppliers();
        projects = new Fakes.Projects();
        current = Fakes.admin();
        supplierService = new SupplierService(suppliers, receipts, () -> current);
        projectService = new ProjectService(projects, receipts, () -> current);
    }

    @Test
    void supplierDeleteBlockedWhileReferenced() {
        Supplier s = suppliers.add("Acme", "111");
        receipts.save(Fakes.receipt("OR-1", s, null, LocalDate.of(2026, 11, 1), "x", "1"));
        ValidationException e = assertThrows(ValidationException.class, () -> supplierService.delete(s.id()));
        assertTrue(e.getMessage().contains("1 receipt"), e.getMessage());
        assertEquals(1, suppliers.data.size());
    }

    @Test
    void supplierDeleteAllowedForAdminWhenUnusedAndDeniedForEncoder() {
        Supplier s = suppliers.add("Acme", "111");
        current = Fakes.encoder();
        assertThrows(SecurityException.class, () -> supplierService.delete(s.id()));
        current = Fakes.admin();
        supplierService.delete(s.id());
        assertEquals(0, suppliers.data.size());
    }

    @Test
    void supplierSaveRequiresNameAndTinAndRejectsDuplicateTin() {
        assertThrows(ValidationException.class, () -> supplierService.save(new Supplier(null, " ", "111", "", "")));
        assertThrows(ValidationException.class, () -> supplierService.save(new Supplier(null, "Acme", " ", "", "")));
        Supplier created = supplierService.save(new Supplier(null, "Acme", "111", "", ""));
        assertThrows(ValidationException.class, () -> supplierService.save(new Supplier(null, "Other", " 111 ", "", "")));
        supplierService.save(new Supplier(created.id(), "Acme Renamed", "111", "", ""));
        assertEquals("Acme Renamed", suppliers.data.get(created.id()).businessName());
    }

    @Test
    void projectDeleteBlockedWhileReferenced() {
        Supplier s = suppliers.add("Acme", "111");
        Project p = projects.add("Bridge", "BR-01");
        receipts.save(Fakes.receipt("OR-1", s, p, LocalDate.of(2026, 11, 1), "x", "1"));
        ValidationException e = assertThrows(ValidationException.class, () -> projectService.delete(p.id()));
        assertTrue(e.getMessage().contains("1 receipt"), e.getMessage());
    }

    @Test
    void projectSaveRequiresNameAndCodeAndRejectsDuplicateCode() {
        assertThrows(ValidationException.class, () -> projectService.save(new Project(null, "", "BR", "")));
        assertThrows(ValidationException.class, () -> projectService.save(new Project(null, "Bridge", "", "")));
        projectService.save(new Project(null, "Bridge", "BR-01", ""));
        assertThrows(ValidationException.class, () -> projectService.save(new Project(null, "Other", "br-01", "")));
    }
}
