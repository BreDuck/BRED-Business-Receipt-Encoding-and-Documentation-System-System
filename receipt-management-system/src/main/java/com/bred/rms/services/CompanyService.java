package com.bred.rms.services;

import com.bred.rms.models.Company;
import com.bred.rms.models.User;
import com.bred.rms.models.ValidationException;
import com.bred.rms.repositories.CompanyRepository;

import java.util.List;
import java.util.UUID;

/** Creator-only company management. Database policies provide the same authorization boundary. */
public final class CompanyService {
    private final CompanyRepository companies;
    private final CurrentUser who;

    public CompanyService(CompanyRepository companies, CurrentUser who) {
        this.companies = companies;
        this.who = who;
    }

    public List<Company> list() {
        requireCreator();
        return companies.findAll();
    }

    public boolean isSupported() { return companies.isSupported(); }

    public void update(UUID id, String name) {
        requireCreator();
        if (id == null) throw new ValidationException("Choose a company.");
        if (name == null || name.isBlank()) throw new ValidationException("Company name is required.");
        companies.update(id, name.trim());
    }

    public void delete(UUID id) {
        requireCreator();
        if (id == null) throw new ValidationException("Choose a company.");
        companies.delete(id);
    }

    private void requireCreator() {
        User user = who.get();
        if (user == null || !user.active() || user.role() != com.bred.rms.models.Role.CREATOR) {
            throw new SecurityException("Only the Creator can manage companies.");
        }
    }
}
