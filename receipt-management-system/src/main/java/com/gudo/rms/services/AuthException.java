package com.gudo.rms.services;

/** Sign-in failed. The message is shown to the user as-is. */
public class AuthException extends Exception {
    public AuthException(String message) { super(message); }
}
