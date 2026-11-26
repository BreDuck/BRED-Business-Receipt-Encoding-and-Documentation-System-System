package com.bred.rms.updates;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/** Persists per-user update preferences locally; session notification state is kept only in memory. */
public final class UpdatePreferences {
    private static final String MODE_KEY = "update.mode";
    private static final String AUTO_CHECK_KEY = "update.autoCheck";
    private static final String LAST_SUCCESSFUL_CHECK_KEY = "update.lastSuccessfulCheck";
    private static final String LAST_ATTEMPTED_CHECK_KEY = "update.lastAttemptedCheck";

    private final Preferences preferences;
    private final AtomicBoolean updateNotificationShown = new AtomicBoolean();

    public UpdatePreferences() {
        this(Preferences.userNodeForPackage(UpdatePreferences.class));
    }

    public UpdatePreferences(Preferences preferences) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
    }

    public UpdateMode mode() {
        try {
            return UpdateMode.valueOf(preferences.get(MODE_KEY, UpdateMode.ASK.name()));
        } catch (IllegalArgumentException e) {
            return UpdateMode.ASK;
        }
    }

    public void setMode(UpdateMode mode) {
        if (mode == null) throw new IllegalArgumentException("mode");
        preferences.put(MODE_KEY, mode.name());
        flush();
    }

    public boolean automaticallyCheckForUpdates() {
        return preferences.getBoolean(AUTO_CHECK_KEY, true);
    }

    public void setAutomaticallyCheckForUpdates(boolean enabled) {
        preferences.putBoolean(AUTO_CHECK_KEY, enabled);
        flush();
    }

    public Instant lastSuccessfulCheck() {
        return readInstant(LAST_SUCCESSFUL_CHECK_KEY);
    }

    public Instant lastAttemptedCheck() {
        return readInstant(LAST_ATTEMPTED_CHECK_KEY);
    }

    public void recordAttemptedCheck(Instant time) {
        writeInstant(LAST_ATTEMPTED_CHECK_KEY, time);
    }

    public void recordSuccessfulCheck(Instant time) {
        writeInstant(LAST_SUCCESSFUL_CHECK_KEY, time);
    }

    /** Returns true only for the first update notification requested during this process session. */
    public boolean markUpdateNotificationShown() {
        return updateNotificationShown.compareAndSet(false, true);
    }

    public boolean updateNotificationShownThisSession() {
        return updateNotificationShown.get();
    }

    private Instant readInstant(String key) {
        long millis = preferences.getLong(key, 0L);
        return millis <= 0 ? null : Instant.ofEpochMilli(millis);
    }

    private void writeInstant(String key, Instant time) {
        if (time == null) throw new IllegalArgumentException("time");
        preferences.putLong(key, time.toEpochMilli());
        flush();
    }

    private void flush() {
        try {
            preferences.flush();
        } catch (BackingStoreException e) {
            throw new IllegalStateException("Unable to save update preferences.", e);
        }
    }
}
