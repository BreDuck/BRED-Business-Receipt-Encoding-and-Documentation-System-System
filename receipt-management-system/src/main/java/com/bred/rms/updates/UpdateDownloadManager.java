package com.bred.rms.updates;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileStore;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Downloads stable BRED Windows installers to a per-user temporary staging directory. */
public final class UpdateDownloadManager implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(UpdateDownloadManager.class.getName());
    private static final String RELEASE_ASSET_HOST = "github.com";
    private static final String[] REDIRECT_HOSTS = {
            "github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com"
    };

    private final Path updateDirectory;
    private final HttpClient http;
    private final ExecutorService executor;
    private final Predicate<URI> assetUrlValidator;
    private final Predicate<URI> responseUrlValidator;
    private final ConcurrentHashMap<String, DownloadJob> jobs = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<DownloadProgress>> listeners = new CopyOnWriteArrayList<>();
    private volatile DownloadProgress latestProgress;

    public UpdateDownloadManager() {
        this(defaultUpdateDirectory(), HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
                        .followRedirects(HttpClient.Redirect.NEVER).build(),
                Executors.newCachedThreadPool(runnable -> {
                    Thread thread = new Thread(runnable, "bred-update-download");
                    thread.setDaemon(true);
                    return thread;
                }),
                UpdateDownloadManager::isTrustedReleaseAsset,
                UpdateDownloadManager::isTrustedResponseUrl);
    }

    UpdateDownloadManager(Path updateDirectory, HttpClient http, ExecutorService executor,
                          Predicate<URI> assetUrlValidator, Predicate<URI> responseUrlValidator) {
        this.updateDirectory = Objects.requireNonNull(updateDirectory, "updateDirectory").toAbsolutePath().normalize();
        this.http = Objects.requireNonNull(http, "http");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.assetUrlValidator = Objects.requireNonNull(assetUrlValidator, "assetUrlValidator");
        this.responseUrlValidator = Objects.requireNonNull(responseUrlValidator, "responseUrlValidator");
    }

    /** Starts a download, or joins the active/completed job for the same version. */
    public DownloadHandle download(UpdateInfo update) {
        Objects.requireNonNull(update, "update");
        validateUpdate(update);
        AtomicBoolean created = new AtomicBoolean();
        DownloadJob job = jobs.compute(update.latestVersion(), (version, current) -> {
            if (current != null && !current.retryable()) return current;
            created.set(true);
            return new DownloadJob(update);
        });
        if (created.get()) job.start();
        else job.replayReady();
        return job.handle;
    }

    /** Subscribe to progress/state changes. The current state, if any, is delivered immediately. */
    public ProgressSubscription addProgressListener(Consumer<DownloadProgress> listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        DownloadProgress current = latestProgress;
        if (current != null) listener.accept(current);
        return () -> listeners.remove(listener);
    }

    public Optional<DownloadProgress> currentProgress() {
        return Optional.ofNullable(latestProgress);
    }

    public void cancel(String version) {
        DownloadJob job = jobs.get(version);
        if (job != null) job.cancel();
    }

    public Path updateDirectory() {
        return updateDirectory;
    }

    private static Path defaultUpdateDirectory() {
        Path temporary = Path.of(System.getProperty("java.io.tmpdir"), "BRED", "updates")
                .toAbsolutePath().normalize();
        if (!insideApplicationDirectory(temporary)) return temporary;

        String localAppData = System.getenv("LOCALAPPDATA");
        Path fallback = localAppData == null || localAppData.isBlank()
                ? Path.of(System.getProperty("user.home"), "AppData", "Local", "BRED", "updates")
                : Path.of(localAppData, "BRED", "updates");
        fallback = fallback.toAbsolutePath().normalize();
        if (insideApplicationDirectory(fallback)) {
            throw new IllegalStateException("Could not locate a temporary update directory outside the application.");
        }
        return fallback;
    }

    private static boolean insideApplicationDirectory(Path candidate) {
        try {
            Path location = Path.of(UpdateDownloadManager.class.getProtectionDomain().getCodeSource()
                    .getLocation().toURI()).toAbsolutePath().normalize();
            Path installDirectory = Files.isDirectory(location) ? location : location.getParent();
            Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
            return (installDirectory != null && candidate.startsWith(installDirectory))
                    || candidate.startsWith(workingDirectory);
        } catch (Exception error) {
            throw new IllegalStateException("Could not determine the application directory for update staging.", error);
        }
    }

    private void validateUpdate(UpdateInfo update) {
        String expectedFilename = "BRED-Setup-" + update.latestVersion() + ".msi";
        if (!expectedFilename.equals(update.installerFilename())
                || !update.installerFilename().matches("BRED-Setup-[0-9]+(?:\\.[0-9]+)*\\.msi")) {
            throw new UpdateDownloadException("The release does not identify a valid BRED MSI installer.");
        }
        if (!assetUrlValidator.test(update.installerDownloadUrl())) {
            throw new UpdateDownloadException("The installer download URL is not a trusted BRED GitHub release asset.");
        }
    }

    private static boolean isTrustedReleaseAsset(URI uri) {
        try {
            String path = uri.getPath();
            String tag = path.substring(path.lastIndexOf("/download/") + "/download/".length()).split("/", 2)[0];
            String version = VersionComparator.normalize(tag).orElse("");
            GitHubReleaseClient.validateInstallerDownloadUrl(uri, version,
                    path.substring(path.lastIndexOf('/') + 1));
            return RELEASE_ASSET_HOST.equalsIgnoreCase(uri.getHost());
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static boolean isTrustedResponseUrl(URI uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) return false;
        for (String host : REDIRECT_HOSTS) {
            if (host.equalsIgnoreCase(uri.getHost())) return true;
        }
        return false;
    }

    private void publish(DownloadProgress progress) {
        latestProgress = progress;
        for (Consumer<DownloadProgress> listener : listeners) {
            try {
                listener.accept(progress);
            } catch (RuntimeException error) {
                LOGGER.log(Level.WARNING, "An update progress listener failed.", error);
            }
        }
    }

    @Override
    public void close() {
        for (DownloadJob job : jobs.values()) {
            if (!job.handle.completion().isDone()) job.cancel();
        }
        executor.shutdownNow();
    }

    public enum Status {
        DOWNLOADING, VERIFYING, READY, FAILED, CANCELLED
    }

    public record DownloadProgress(Status status, String currentVersion, String version, String installerFilename,
                                   long downloadedBytes, OptionalLong totalBytes, int percentage,
                                   boolean checksumAvailable, boolean checksumVerified, String message,
                                   Optional<DownloadResult> downloadResult) { }

    public static final class DownloadResult {
        private final Path installerPath;
        private final Path stagingDirectory;
        private final String currentVersion;
        private final String version;
        private final long sizeBytes;
        private final String sha256;
        private final Optional<String> expectedSha256;
        private final boolean checksumVerified;

        private DownloadResult(Path installerPath, Path stagingDirectory, String currentVersion, String version,
                               long sizeBytes, String sha256, Optional<String> expectedSha256,
                               boolean checksumVerified) {
            this.installerPath = installerPath;
            this.stagingDirectory = stagingDirectory;
            this.currentVersion = currentVersion;
            this.version = version;
            this.sizeBytes = sizeBytes;
            this.sha256 = sha256;
            this.expectedSha256 = expectedSha256;
            this.checksumVerified = checksumVerified;
        }

        public Path installerPath() { return installerPath; }
        public Path stagingDirectory() { return stagingDirectory; }
        public String currentVersion() { return currentVersion; }
        public String version() { return version; }
        public long sizeBytes() { return sizeBytes; }
        public String sha256() { return sha256; }
        public Optional<String> expectedSha256() { return expectedSha256; }
        public boolean checksumVerified() { return checksumVerified; }
    }

    @FunctionalInterface
    public interface ProgressSubscription {
        void close();
    }

    public static final class DownloadHandle {
        private final DownloadJob job;

        private DownloadHandle(DownloadJob job) {
            this.job = job;
        }

        public CompletableFuture<DownloadResult> completion() {
            return job.completion;
        }

        public boolean cancel() {
            return job.cancel();
        }
    }

    private final class DownloadJob {
        private final UpdateInfo update;
        private final CompletableFuture<DownloadResult> completion = new CompletableFuture<>();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean started = new AtomicBoolean();
        private final DownloadHandle handle = new DownloadHandle(this);
        private volatile Future<?> worker;
        private volatile InputStream activeStream;
        private volatile Status status;
        private volatile DownloadProgress readyProgress;
        private volatile boolean running;

        private DownloadJob(UpdateInfo update) {
            this.update = update;
        }

        private boolean retryable() {
            return status == Status.FAILED || status == Status.CANCELLED;
        }

        private void start() {
            if (started.compareAndSet(false, true)) worker = executor.submit(this::run);
        }

        private boolean cancel() {
            if (completion.isDone()) return false;
            cancelled.set(true);
            InputStream stream = activeStream;
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException error) {
                    LOGGER.log(Level.FINE, "Unable to close the cancelled update stream.", error);
                }
            }
            Future<?> activeWorker = worker;
            if (activeWorker != null) activeWorker.cancel(true);
            if (!running) {
                status = Status.CANCELLED;
                publish(progress(Status.CANCELLED, 0, OptionalLong.empty(), "Download cancelled.", false));
                completion.completeExceptionally(new CancellationException("The update download was cancelled."));
            }
            return true;
        }

        private void replayReady() {
            DownloadProgress progress = readyProgress;
            if (progress != null) UpdateDownloadManager.this.publish(progress);
        }

        private void run() {
            running = true;
            Path part = null;
            boolean moved = false;
            boolean wasCancelled = false;
            UpdateDownloadException failure = null;
            try {
                checkCancelled();
                Files.createDirectories(updateDirectory);
                Path target = updateDirectory.resolve(update.installerFilename()).normalize();
                if (!target.getParent().equals(updateDirectory)) {
                    throw new UpdateDownloadException("The installer filename is invalid.");
                }
                if (Files.exists(target)) {
                    Verification existing = verifyExisting(target, update);
                    if (existing.valid()) {
                        publishReady(target, Files.size(target), existing.sha256(), existing.checksumVerified(),
                                existing.checksumVerified() ? "MSI installer ready. SHA-256 verified."
                                        : "MSI installer ready. GitHub did not provide a trusted SHA-256 checksum.");
                        return;
                    }
                    Files.delete(target);
                }
                checkAvailableSpace(update);
                part = updateDirectory.resolve(update.installerFilename() + ".part");
                Files.deleteIfExists(part);
                HttpResponse<InputStream> response = requestAsset(update.installerDownloadUrl());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    response.body().close();
                    throw new UpdateDownloadException(downloadHttpError(response.statusCode()));
                }
                if (!responseUrlValidator.test(response.uri())) {
                    response.body().close();
                    throw new UpdateDownloadException("The installer download redirected to an untrusted host.");
                }
                activeStream = response.body();
                OptionalLong total = effectiveTotal(response, update);
                checkAvailableSpace(update);
                status = Status.DOWNLOADING;
                publish(progress(Status.DOWNLOADING, 0, total, "Downloading update...", false));

                MessageDigest digest = sha256();
                long downloaded = 0;
                try (InputStream input = activeStream;
                     var output = Files.newOutputStream(part, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                    byte[] buffer = new byte[64 * 1024];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        checkCancelled();
                        verifyNextChunkSize(update, total, downloaded, count);
                        checkAvailableSpaceForRemaining(update.installerFilename(), total, downloaded);
                        output.write(buffer, 0, count);
                        digest.update(buffer, 0, count);
                        downloaded += count;
                        publish(progress(Status.DOWNLOADING, downloaded, total, "Downloading update...", false));
                    }
                } finally {
                    activeStream = null;
                }
                checkCancelled();
                status = Status.VERIFYING;
                publish(progress(Status.VERIFYING, downloaded, total, "Verifying installer...", false));
                if (downloaded <= 0) throw new UpdateDownloadException("The downloaded installer is empty.");
                if (update.installerSize().isPresent() && downloaded != update.installerSize().getAsLong()) {
                    throw new UpdateDownloadException("The downloaded installer size does not match the published release.");
                }
                if (total.isPresent() && downloaded != total.getAsLong()) {
                    throw new UpdateDownloadException("The download was interrupted before the installer was complete.");
                }
                if (!isMsiCompoundFile(part)) {
                    throw new UpdateDownloadException("The downloaded file is not a valid Windows MSI installer.");
                }
                byte[] actualDigest = digest.digest();
                boolean checksumVerified = verifyChecksum(update, actualDigest);
                checkCancelled();
                moveToFinal(part, target);
                moved = true;
                ready(target, checksumVerified, actualDigest);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                wasCancelled = true;
            } catch (IOException error) {
                if (cancelled.get()) wasCancelled = true;
                else failure = downloadFailure(error);
            } catch (RuntimeException error) {
                if (cancelled.get() || error instanceof CancellationException) wasCancelled = true;
                else if (error instanceof UpdateDownloadException downloadError) failure = downloadError;
                else failure = new UpdateDownloadException("The installer download failed unexpectedly.", error);
            } finally {
                InputStream unfinishedStream = activeStream;
                activeStream = null;
                if (unfinishedStream != null) {
                    try {
                        unfinishedStream.close();
                    } catch (IOException error) {
                        LOGGER.log(Level.FINE, "Unable to close the update response stream.", error);
                    }
                }
                if (!moved && part != null) {
                    try {
                        Files.deleteIfExists(part);
                    } catch (IOException error) {
                        LOGGER.log(Level.WARNING, "Unable to remove the incomplete BRED installer file.", error);
                        failure = new UpdateDownloadException(
                                "The incomplete installer could not be removed safely.", error);
                    }
                }
                running = false;
                if (failure != null) fail(failure);
                else if (wasCancelled || cancelled.get()) finishCancelled();
            }
        }

        private UpdateDownloadException downloadFailure(IOException error) {
            String message = error instanceof java.net.http.HttpTimeoutException
                    ? "The installer download timed out."
                    : error instanceof java.net.ConnectException || error instanceof java.net.UnknownHostException
                    ? "Could not connect to GitHub to download the installer."
                    : isDiskSpaceError(error) ? "There is not enough disk space to download the installer."
                    : "The installer download was interrupted. Try again when your connection is available.";
            return new UpdateDownloadException(message, error);
        }

        private void finishCancelled() {
            status = Status.CANCELLED;
            publish(progress(Status.CANCELLED, 0, OptionalLong.empty(), "Download cancelled.", false));
            completion.completeExceptionally(new CancellationException("The update download was cancelled."));
        }

        private void fail(UpdateDownloadException error) {
            status = Status.FAILED;
            publish(progress(Status.FAILED, 0, OptionalLong.empty(), error.getMessage(), false));
            LOGGER.log(Level.WARNING, "BRED update download failed: " + error.getMessage(), error);
            completion.completeExceptionally(error);
        }

        private void ready(Path path, boolean checksumVerified, byte[] actualDigest) {
            long size;
            try {
                size = Files.size(path);
            } catch (IOException error) {
                fail(new UpdateDownloadException("The downloaded MSI could not be read.", error));
                return;
            }
            publishReady(path, size, HexFormat.of().formatHex(actualDigest), checksumVerified,
                    checksumVerified ? "MSI installer ready. SHA-256 verified."
                            : "MSI installer ready. GitHub did not provide a trusted SHA-256 checksum.");
        }

        private void publishReady(Path path, long size, String actualSha256, boolean checksumVerified,
                                  String message) {
            Optional<String> expectedSha256 = update.installerSha256();
            DownloadResult result = new DownloadResult(path.toAbsolutePath().normalize(), updateDirectory,
                    update.currentVersion(), update.latestVersion(), size, actualSha256, expectedSha256,
                    checksumVerified);
            OptionalLong total = OptionalLong.of(size);
            DownloadProgress progress = new DownloadProgress(Status.READY, update.currentVersion(), update.latestVersion(),
                    update.installerFilename(), size, total, 100, expectedSha256.isPresent(), checksumVerified,
                    message, Optional.of(result));
            readyProgress = progress;
            publish(progress);
            completion.complete(result);
        }

        private DownloadProgress progress(Status state, long received, OptionalLong total, String message,
                                          boolean checksumVerified) {
            int percent = total.isPresent() && total.getAsLong() > 0
                    ? (int) Math.min(100, (received * 100.0) / total.getAsLong()) : 0;
            return new DownloadProgress(state, update.currentVersion(), update.latestVersion(),
                    update.installerFilename(), received, total, percent,
                    update.installerSha256().isPresent(), checksumVerified, message, Optional.empty());
        }

        private void checkCancelled() throws InterruptedException {
            if (cancelled.get() || Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Download cancelled");
            }
        }

        private HttpResponse<InputStream> requestAsset(URI assetUrl) throws IOException, InterruptedException {
            URI requestUri = assetUrl;
            for (int redirects = 0; redirects <= 5; redirects++) {
                checkCancelled();
                HttpRequest request = HttpRequest.newBuilder(requestUri)
                        .timeout(Duration.ofMinutes(5))
                        .header("Accept", "application/octet-stream")
                        .header("User-Agent", "BRED-Desktop-Updater")
                        .GET().build();
                HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
                int status = response.statusCode();
                if (status < 300 || status >= 400) return response;

                response.body().close();
                if (redirects == 5) throw new UpdateDownloadException("GitHub redirected the installer too many times.");
                String location = response.headers().firstValue("Location").orElse("");
                if (location.isBlank()) throw new UpdateDownloadException("GitHub returned an invalid installer redirect.");
                URI redirected;
                try {
                    redirected = requestUri.resolve(location);
                } catch (IllegalArgumentException error) {
                    throw new UpdateDownloadException("GitHub returned an invalid installer redirect.", error);
                }
                if (!responseUrlValidator.test(redirected)) {
                    throw new UpdateDownloadException("The installer download redirected to an untrusted host.");
                }
                requestUri = redirected;
            }
            throw new UpdateDownloadException("GitHub redirected the installer too many times.");
        }

        private Verification verifyExisting(Path file, UpdateInfo info) {
            try {
                long size = Files.size(file);
                if (!Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS) || size <= 0
                        || info.installerSize().isPresent() && size != info.installerSize().getAsLong()
                        || !isMsiCompoundFile(file)) return new Verification(false, false, "");
                byte[] actual;
                try (InputStream input = Files.newInputStream(file)) {
                    MessageDigest digest = sha256();
                    byte[] buffer = new byte[64 * 1024];
                    int count;
                    while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
                    actual = digest.digest();
                }
                String actualSha256 = HexFormat.of().formatHex(actual);
                if (info.installerSha256().isEmpty()) return new Verification(true, false, actualSha256);
                return new Verification(MessageDigest.isEqual(actual,
                        HexFormat.of().parseHex(info.installerSha256().orElseThrow())), true, actualSha256);
            } catch (IOException error) {
                return new Verification(false, false, "");
            }
        }
    }

    private record Verification(boolean valid, boolean checksumVerified, String sha256) { }

    private void checkAvailableSpace(UpdateInfo update) throws IOException {
        if (update.installerSize().isEmpty()) return;
        FileStore store = Files.getFileStore(updateDirectory);
        if (store.getUsableSpace() < update.installerSize().getAsLong()) {
            throw new FileSystemException(update.installerFilename(), null, "insufficient disk space");
        }
    }

    private void checkAvailableSpaceForRemaining(String filename, OptionalLong total, long downloaded)
            throws IOException {
        if (total.isEmpty()) return;
        FileStore store = Files.getFileStore(updateDirectory);
        long remaining = Math.max(0, total.getAsLong() - downloaded);
        if (store.getUsableSpace() < remaining) {
            throw new FileSystemException(filename, null, "insufficient disk space");
        }
    }

    private static OptionalLong effectiveTotal(HttpResponse<?> response, UpdateInfo update) {
        OptionalLong expected = update.installerSize();
        OptionalLong contentLength = response.headers().firstValueAsLong("Content-Length");
        if (expected.isPresent() && contentLength.isPresent() && expected.getAsLong() != contentLength.getAsLong()) {
            throw new UpdateDownloadException("GitHub's installer size does not match its download response.");
        }
        return expected.isPresent() ? expected : contentLength;
    }

    private static void verifyNextChunkSize(UpdateInfo update, OptionalLong total, long downloaded, int count) {
        if (total.isPresent() && downloaded > total.getAsLong() - count) {
            throw new UpdateDownloadException("The installer download exceeds the published asset size.");
        }
        if (update.installerSize().isPresent() && downloaded > update.installerSize().getAsLong() - count) {
            throw new UpdateDownloadException("The installer download exceeds the published asset size.");
        }
    }

    private static boolean verifyChecksum(UpdateInfo update, byte[] actualDigest) {
        if (update.installerSha256().isEmpty()) return false;
        byte[] expected = HexFormat.of().parseHex(update.installerSha256().orElseThrow());
        if (!MessageDigest.isEqual(expected, actualDigest)) {
            throw new UpdateDownloadException("The installer SHA-256 checksum does not match GitHub's release metadata.");
        }
        return true;
    }

    static boolean isMsiCompoundFile(Path path) throws IOException {
        long size = Files.size(path);
        if (size < 512) return false;
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            ByteBuffer header = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN);
            while (header.hasRemaining()) {
                if (channel.read(header) < 0) return false;
            }
            byte[] bytes = header.array();
            byte[] signature = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                    (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
            for (int i = 0; i < signature.length; i++) {
                if (bytes[i] != signature[i]) return false;
            }
            int majorVersion = Short.toUnsignedInt(header.getShort(0x1a));
            int byteOrder = Short.toUnsignedInt(header.getShort(0x1c));
            int sectorShift = Short.toUnsignedInt(header.getShort(0x1e));
            int miniSectorShift = Short.toUnsignedInt(header.getShort(0x20));
            return (majorVersion == 3 || majorVersion == 4)
                    && byteOrder == 0xFFFE
                    && (sectorShift == 9 || sectorShift == 12)
                    && (majorVersion != 3 || sectorShift == 9)
                    && miniSectorShift == 6;
        }
    }

    private static void moveToFinal(Path part, Path target) throws IOException {
        try {
            Files.move(part, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException error) {
            Files.move(part, target);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable.", error);
        }
    }

    private static boolean isDiskSpaceError(IOException error) {
        return error instanceof FileSystemException fileError && fileError.getReason() != null
                && fileError.getReason().toLowerCase(Locale.ROOT).contains("space");
    }

    private static String downloadHttpError(int status) {
        if (status == 404) return "The installer is no longer available from GitHub.";
        if (status == 403 || status == 429) return "GitHub temporarily blocked the installer download. Try again later.";
        return "GitHub could not provide the installer (HTTP " + status + ").";
    }
}
