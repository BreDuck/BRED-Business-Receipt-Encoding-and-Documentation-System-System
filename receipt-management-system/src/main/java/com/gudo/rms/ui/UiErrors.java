package com.gudo.rms.ui;

import com.gudo.rms.models.ValidationException;
import com.gudo.rms.repositories.AttachmentException;
import com.gudo.rms.repositories.RepositoryException;

/** Turns exceptions into text safe to show a person. */
public final class UiErrors {
    private UiErrors() { }

    /** Returns the exception's own message when it is meant for people, otherwise a generic line. */
    public static String friendly(Throwable e) {
        boolean meant = e instanceof ValidationException || e instanceof AttachmentException
                || e instanceof SecurityException || e instanceof RepositoryException;
        return meant && e.getMessage() != null ? e.getMessage() : "Something went wrong. Please try again.";
    }
}
