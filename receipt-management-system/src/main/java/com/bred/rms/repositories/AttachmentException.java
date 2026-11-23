package com.bred.rms.repositories;

/** An attachment could not be stored or fetched. The message is user-facing. */
public class AttachmentException extends Exception {
    public AttachmentException(String message) { super(message); }
}
