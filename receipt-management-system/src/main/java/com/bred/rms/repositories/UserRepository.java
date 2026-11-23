package com.bred.rms.repositories;

import com.bred.rms.models.Role;
import com.bred.rms.models.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Reads and manages user profiles. Creating users goes through the admin-create-user Edge Function. */
public interface UserRepository {
    List<User> findAll();

    Optional<User> findById(UUID id);

    /** Creates an account (admin only). */
    User create(String email, String fullName, Role role, char[] password);

    /** Updates name, role and active flag (admin only; the last active admin is protected by the database). */
    void update(UUID id, String fullName, Role role, boolean active);
}
