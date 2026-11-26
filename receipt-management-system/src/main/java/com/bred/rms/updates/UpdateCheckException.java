package com.bred.rms.updates;

/** A user-safe failure while querying or validating a published update. */
public final class UpdateCheckException extends RuntimeException {
    public UpdateCheckException(String message) {
        super(message);
    }

    public UpdateCheckException(String message, Throwable cause) {
        super(message, cause);
    }
}
