package com.bred.rms.services;

import com.bred.rms.models.Company;
import com.bred.rms.models.Role;
import com.bred.rms.models.User;
import com.bred.rms.repositories.CompanyRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompanyServiceTest {
    private static final class MemoryCompanies implements CompanyRepository {
        final Company company = new Company(UUID.randomUUID(), "BRED");
        UUID updated;
        UUID deleted;
        @Override public List<Company> findAll() { return List.of(company); }
        @Override public void update(UUID id, String name) { updated = id; }
        @Override public void delete(UUID id) { deleted = id; }
    }

    @Test
    void companyManagementIsCreatorOnly() {
        MemoryCompanies repository = new MemoryCompanies();
        CompanyService creator = new CompanyService(repository, () ->
                new User(UUID.randomUUID(), "Creator", "creator@example.com", Role.CREATOR, true));
        CompanyService admin = new CompanyService(repository, () ->
                new User(UUID.randomUUID(), "Admin", "admin@example.com", Role.ADMIN, true));

        assertEquals(List.of(repository.company), creator.list());
        creator.update(repository.company.id(), "  Updated  ");
        creator.delete(repository.company.id());
        assertEquals(repository.company.id(), repository.updated);
        assertEquals(repository.company.id(), repository.deleted);
        assertThrows(SecurityException.class, admin::list);
        assertThrows(SecurityException.class, () -> admin.update(repository.company.id(), "Blocked"));
        assertThrows(SecurityException.class, () -> admin.delete(repository.company.id()));
    }
}
