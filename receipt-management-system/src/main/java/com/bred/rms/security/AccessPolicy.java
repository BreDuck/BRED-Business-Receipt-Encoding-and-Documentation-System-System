package com.bred.rms.security;

import com.bred.rms.models.Role;

import java.util.Arrays;
import java.util.List;

/** Which menu entries a role sees. (The database enforces the real permissions.) */
public final class AccessPolicy {
    private AccessPolicy() { }

    public static List<Module> modulesFor(Role role) {
        return Arrays.stream(Module.values()).filter(m -> m.allows(role)).toList();
    }
}
