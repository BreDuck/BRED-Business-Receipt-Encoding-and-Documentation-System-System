package com.gudo.rms.services;

import com.gudo.rms.models.Role;
import com.gudo.rms.models.User;
import com.gudo.rms.models.ValidationException;
import com.gudo.rms.repositories.UserRepository;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** Account management. Administrators only; the database enforces the same rules. */
public final class UserService {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final String DENIED = "Only administrators can manage users.";

    private final UserRepository users;
    private final CurrentUser who;

    /** Creates the service. */
    public UserService(UserRepository users, CurrentUser who) {
        this.users = users;
        this.who = who;
    }

    /** Returns all accounts. */
    public List<User> list() {
        Guard.requireAdmin(who, DENIED);
        return users.findAll();
    }

    /** Creates an account through the Edge Function (password at least 6 characters). */
    public User create(String fullName, String email, Role role, char[] password) {
        Guard.requireAdmin(who, DENIED);
        if (Guard.blank(fullName)) throw new ValidationException("Full name is required.");
        if (email == null || !EMAIL.matcher(email.trim()).matches()) throw new ValidationException("Enter a valid email address.");
        if (password == null || password.length < 6) throw new ValidationException("The password must be at least 6 characters.");
        if (role == null) throw new ValidationException("Choose a role.");
        return users.create(email.trim(), fullName.trim(), role, password);
    }

    /** Changes name, role and active flag. Errors from the database (such as the last-admin rule) pass through unchanged. */
    public void update(UUID id, String fullName, Role role, boolean active) {
        Guard.requireAdmin(who, DENIED);
        if (Guard.blank(fullName)) throw new ValidationException("Full name is required.");
        users.update(id, fullName.trim(), role, active);
    }
}
