package com.bred.rms.repositories;

import com.bred.rms.models.Project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Stores project records. A null id on save means "create". */
public interface ProjectRepository {
    List<Project> findAll();

    Optional<Project> findById(UUID id);

    Project save(Project value);

    void delete(UUID id);
}
