package com.bred.rms.repositories;

import com.bred.rms.models.Company;

import java.util.List;
import java.util.UUID;

/** Manages company records. Creator-only authorization is enforced by the service and database. */
public interface CompanyRepository {
    default boolean isSupported() { return true; }

    List<Company> findAll();

    void update(UUID id, String name);

    void delete(UUID id);
}
