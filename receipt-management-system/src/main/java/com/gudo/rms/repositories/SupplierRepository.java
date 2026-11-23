package com.gudo.rms.repositories;

import com.gudo.rms.models.Supplier;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Stores supplier records. A null id on save means "create". */
public interface SupplierRepository {
    List<Supplier> findAll();

    Optional<Supplier> findById(UUID id);

    Supplier save(Supplier value);

    void delete(UUID id);
}
