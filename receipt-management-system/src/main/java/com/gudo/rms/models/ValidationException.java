package com.gudo.rms.models;

/** A problem with what the user entered or tried to do. The message is written for the user. */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) { super(message); }
}
