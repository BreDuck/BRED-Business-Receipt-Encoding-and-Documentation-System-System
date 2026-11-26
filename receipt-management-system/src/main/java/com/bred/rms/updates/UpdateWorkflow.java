package com.bred.rms.updates;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Coordinates update checks and download decisions without coupling update logic to Swing. */
public final class UpdateWorkflow {
    private final UpdatePreferences preferences;
    private final UpdateChecker checker;
    private final UpdateDownloadManager downloads;

    public UpdateWorkflow(UpdatePreferences preferences, UpdateChecker checker, UpdateDownloadManager downloads) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.checker = Objects.requireNonNull(checker, "checker");
        this.downloads = Objects.requireNonNull(downloads, "downloads");
    }

    public CompletableFuture<Optional<UpdateInfo>> checkManually() {
        return checker.checkForUpdate();
    }

    public CompletableFuture<Optional<UpdateInfo>> checkAutomatically() {
        return checker.checkAutomatically();
    }

    /** Starts a download only in AUTOMATIC mode; ASK mode must be explicitly confirmed by the user. */
    public Detection handleDetectedUpdate(UpdateInfo update) {
        Objects.requireNonNull(update, "update");
        if (preferences.mode() == UpdateMode.ASK) return new Detection(Decision.ASK_USER, null);
        return new Detection(Decision.DOWNLOADING, downloads.download(update));
    }

    public UpdateDownloadManager.DownloadHandle downloadNow(UpdateInfo update) {
        return downloads.download(Objects.requireNonNull(update, "update"));
    }

    public void cancel(String version) {
        downloads.cancel(version);
    }

    public UpdateDownloadManager.ProgressSubscription addProgressListener(
            Consumer<UpdateDownloadManager.DownloadProgress> listener) {
        return downloads.addProgressListener(listener);
    }

    public Optional<UpdateDownloadManager.DownloadProgress> currentProgress() {
        return downloads.currentProgress();
    }

    public enum Decision {
        ASK_USER, DOWNLOADING
    }

    public record Detection(Decision decision, UpdateDownloadManager.DownloadHandle download) { }
}
