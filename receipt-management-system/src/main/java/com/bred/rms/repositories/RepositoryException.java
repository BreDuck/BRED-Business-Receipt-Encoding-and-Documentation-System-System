package com.bred.rms.repositories;

/** The data store could not do what was asked (offline, signed out, not allowed, server error). The message is user-facing. */
public class RepositoryException extends RuntimeException {
    private final boolean network;

    public RepositoryException(String message, boolean network) {
        super(message);
        this.network = network;
    }

    /** True when the server could not be reached at all. */
    public boolean isNetwork() { return network; }
}
