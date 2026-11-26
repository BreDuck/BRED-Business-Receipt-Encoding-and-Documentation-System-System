package com.bred.rms.repositories;

import com.bred.rms.models.AppRelease;

/** Reads the public current release and publishes a new version. */
public interface AppReleaseRepository {
    AppRelease current();
    AppRelease publish(String version);
}
