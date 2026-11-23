package com.bred.rms.services;

import com.bred.rms.models.User;

/** Supplies the signed-in user (or null when nobody is signed in). */
@FunctionalInterface
public interface CurrentUser {
    User get();
}
