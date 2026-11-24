package com.bred.rms.repositories;

import com.bred.rms.models.Role;
import com.bred.rms.models.AccountStatus;
import com.bred.rms.models.Company;
import com.bred.rms.models.User;
import com.bred.rms.repositories.RepositoryException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Reads and manages user profiles. Creating users goes through the admin-create-user Edge Function. */
public interface UserRepository {
    List<User> findAll();

    /** Lists active users visible as direct-message recipients, without exposing their email addresses. */
    default List<User> findMessagingContacts() {
        return findAll().stream().filter(User::active).toList();
    }

    Optional<User> findById(UUID id);

    /** Creates an account (admin only). */
    User create(String email, String fullName, Role role, char[] password);

    /** Updates name, role and active flag (admin only; the last active admin is protected by the database). */
    void update(UUID id, String fullName, Role role, boolean active);

    /** Submits a public registration request. Supabase implementations send it to the register-account function. */
    default void registerAccount(String fullName, String email, Role role, java.util.UUID companyId,
                                 String companyName, char[] password) {
        throw new RepositoryException("Public registration is not available in local mode.", false);
    }

    /** Lists active companies that accept account applications. */
    default List<Company> availableCompanies() {
        throw new RepositoryException("Company applications are not available.", false);
    }

    /** Changes a pending registration to approved or rejected through the protected review function. */
    default void reviewRegistration(UUID id, AccountStatus decision) {
        throw new RepositoryException("Registration review is not available.", false);
    }
}
