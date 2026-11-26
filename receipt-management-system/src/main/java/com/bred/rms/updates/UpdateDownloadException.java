package com.bred.rms.updates;

/** A user-safe failure while downloading or verifying a release installer. */
public final class UpdateDownloadException extends RuntimeException {
    public UpdateDownloadException(String message) {
        super(message);
    }

    public UpdateDownloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
