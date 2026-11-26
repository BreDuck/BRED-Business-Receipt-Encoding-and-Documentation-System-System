package com.bred.rms.updates;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateDownloadManagerTest {
    @TempDir
    Path tempDir;

    private HttpServer server;
    private UpdateDownloadManager manager;
    private ExecutorService executor;

    @AfterEach
    void cleanUp() throws IOException {
        if (manager != null) manager.close();
        if (server != null) server.stop(0);
    }

    @Test
    void downloadsVerifiesAndReportsProgressBeforePublishingFinalFile() throws Exception {
        byte[] installer = fakeMsiCompoundFile();
        String checksum = sha256(installer);
        AtomicInteger requests = startServer(200, installer, installer.length);
        manager = createManager(uri -> uri.getHost().equals("127.0.0.1"), uri -> uri.getHost().equals("127.0.0.1"));
        var progress = new java.util.concurrent.CopyOnWriteArrayList<UpdateDownloadManager.DownloadProgress>();
        manager.addProgressListener(progress::add);

        UpdateDownloadManager.DownloadResult result = manager.download(update(installer, checksum)).completion()
                .get(5, TimeUnit.SECONDS);

        assertTrue(result.checksumVerified());
        assertEquals(installer.length, Files.size(result.installerPath()));
        assertTrue(Files.exists(result.installerPath()));
        assertFalse(Files.exists(tempDir.resolve("BRED-Setup-1.2.3.msi.part")));
        assertEquals(1, requests.get());
        assertTrue(progress.stream().anyMatch(event -> event.status() == UpdateDownloadManager.Status.DOWNLOADING
                && event.percentage() == 100));
        assertEquals(UpdateDownloadManager.Status.READY, progress.get(progress.size() - 1).status());
        assertTrue(progress.get(progress.size() - 1).checksumVerified());
        assertTrue(progress.get(progress.size() - 1).downloadResult().isPresent());
    }

    @Test
    void rejectsEmptyFilesChecksumMismatchInvalidMsiAndBadUrl() throws Exception {
        byte[] installer = fakeMsiCompoundFile();
        startServer(200, new byte[0], 0);
        manager = createManager(uri -> uri.getHost().equals("127.0.0.1"), uri -> uri.getHost().equals("127.0.0.1"));
        assertDownloadFailure(update(new byte[0], null), "empty");

        server.stop(0);
        startServer(200, installer, installer.length);
        assertDownloadFailure(update(installer, "0".repeat(64)), "checksum");
        byte[] invalidMsi = new byte[512];
        startServer(200, invalidMsi, invalidMsi.length);
        assertDownloadFailure(update(invalidMsi, null), "valid Windows MSI");
        assertDownloadFailure(update(new byte[0], null, "BRED-Setup-1.2.3.exe"), "valid BRED MSI");
        assertThrows(UpdateDownloadException.class, () -> manager.download(update(installer, null,
                "BRED-Setup-1.2.3.msi", URI.create("https://example.com/BRED-Setup-1.2.3.msi"))));
        assertFalse(Files.exists(tempDir.resolve("BRED-Setup-1.2.3.msi")));
        assertFalse(Files.exists(tempDir.resolve("BRED-Setup-1.2.3.msi.part")));
    }

    @Test
    void interruptedDownloadIsRejectedAndPartialFileRemoved() throws Exception {
        byte[] installer = fakeMsiCompoundFile();
        startServer(200, installer, installer.length + 10);
        manager = createManager(uri -> true, uri -> uri.getHost().equals("127.0.0.1"));

        assertDownloadFailure(updateWithoutExpectedSize(installer), "interrupted");

        assertFalse(Files.exists(tempDir.resolve("BRED-Setup-1.2.3.msi")));
        assertFalse(Files.exists(tempDir.resolve("BRED-Setup-1.2.3.msi.part")));
    }

    @Test
    void reportsHttpAndOfflineDownloadErrorsClearly() throws Exception {
        byte[] installer = fakeMsiCompoundFile();
        startServer(404, "missing".getBytes(java.nio.charset.StandardCharsets.UTF_8), 7);
        manager = createManager(uri -> true, uri -> uri.getHost().equals("127.0.0.1"));
        assertDownloadFailure(update(installer, null), "no longer available");

        server.stop(0);
        UpdateInfo offline = update(installer, null, "BRED-Setup-1.2.3.msi",
                URI.create("http://127.0.0.1:1/download/v1.2.3/BRED-Setup-1.2.3.msi"));
        assertDownloadFailure(offline, "connect to GitHub");
        assertFalse(Files.exists(tempDir.resolve("BRED-Setup-1.2.3.msi.part")));
    }

    @Test
    void rejectsRedirectsToUntrustedHostsBeforeRequestingThem() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/download/", exchange -> {
            exchange.getResponseHeaders().add("Location", "https://example.com/installer.msi");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        manager = createManager(uri -> true, uri -> uri.getHost().equals("127.0.0.1"));

        assertDownloadFailure(update(fakeMsiCompoundFile(), null), "untrusted host");

        assertFalse(Files.exists(tempDir.resolve("BRED-Setup-1.2.3.msi.part")));
    }

    @Test
    void cancellingStopsTheDownloadAndRemovesPartialFile() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/download/", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            started.countDown();
            try (var output = exchange.getResponseBody()) {
                byte[] chunk = new byte[8192];
                for (int i = 0; i < 10_000; i++) {
                    output.write(chunk);
                    output.flush();
                    Thread.sleep(2);
                }
            } catch (IOException | InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();
        manager = createManager(uri -> true, uri -> uri.getHost().equals("127.0.0.1"));
        UpdateDownloadManager.DownloadHandle handle = manager.download(update(fakeMsiCompoundFile(), null));
        assertTrue(started.await(2, TimeUnit.SECONDS));

        assertTrue(handle.cancel());
        assertThrows(CancellationException.class, () -> handle.completion().join());
        waitForPartialCleanup();
        assertFalse(Files.exists(tempDir.resolve("BRED-Setup-1.2.3.msi.part")));
        assertFalse(Files.exists(tempDir.resolve("BRED-Setup-1.2.3.msi")));
    }

    @Test
    void repeatedRequestForSameVersionJoinsTheExistingDownload() throws Exception {
        byte[] installer = fakeMsiCompoundFile();
        AtomicInteger requests = startServer(200, installer, installer.length);
        manager = createManager(uri -> true, uri -> uri.getHost().equals("127.0.0.1"));
        UpdateInfo info = update(installer, null);

        UpdateDownloadManager.DownloadHandle first = manager.download(info);
        UpdateDownloadManager.DownloadHandle duplicate = manager.download(info);

        assertSame(first, duplicate);
        assertSame(first.completion(), duplicate.completion());
        first.completion().get(5, TimeUnit.SECONDS);
        assertEquals(1, requests.get());
        assertFalse(first.completion().join().checksumVerified());
        assertTrue(manager.currentProgress().orElseThrow().message().contains("did not provide"));
    }

    @Test
    void askModeWaitsForUserWhileAutomaticModeStartsTheDownload() throws Exception {
        byte[] installer = fakeMsiCompoundFile();
        AtomicInteger requests = startServer(200, installer, installer.length);
        manager = createManager(uri -> true, uri -> uri.getHost().equals("127.0.0.1"));
        Preferences node = Preferences.userRoot().node("com/bred/rms/test-update-workflow/" + UUID.randomUUID());
        try {
            UpdatePreferences preferences = new UpdatePreferences(node);
            UpdateChecker checker = new UpdateChecker("1.2.2",
                    new GitHubReleaseClient(HttpClient.newHttpClient(), URI.create("http://127.0.0.1:1/latest")),
                    preferences);
            UpdateWorkflow workflow = new UpdateWorkflow(preferences, checker, manager);
            UpdateInfo info = update(installer, null);

            UpdateWorkflow.Detection ask = workflow.handleDetectedUpdate(info);
            assertEquals(UpdateWorkflow.Decision.ASK_USER, ask.decision());
            assertNull(ask.download());
            assertEquals(0, requests.get());

            preferences.setMode(UpdateMode.AUTOMATIC);
            UpdateWorkflow.Detection automatic = workflow.handleDetectedUpdate(info);
            assertEquals(UpdateWorkflow.Decision.DOWNLOADING, automatic.decision());
            assertTrue(automatic.download().completion().get(5, TimeUnit.SECONDS).installerPath().toFile().exists());
            assertEquals(1, requests.get());
        } finally {
            node.removeNode();
            Preferences.userRoot().flush();
        }
    }

    private UpdateDownloadManager createManager(java.util.function.Predicate<URI> assetValidator,
                                                java.util.function.Predicate<URI> responseValidator) {
        executor = Executors.newSingleThreadExecutor();
        return new UpdateDownloadManager(tempDir, HttpClient.newHttpClient(), executor,
                assetValidator, responseValidator);
    }

    private AtomicInteger startServer(int status, byte[] body, long responseLength) throws IOException {
        if (server != null) server.stop(0);
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/download/", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(status, responseLength);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        return requests;
    }

    private AtomicInteger startServer(int status, byte[] body, int responseLength) throws IOException {
        return startServer(status, body, (long) responseLength);
    }

    private UpdateInfo update(byte[] installer, String checksum) {
        return update(installer, checksum, "BRED-Setup-1.2.3.msi",
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                        + "/download/v1.2.3/BRED-Setup-1.2.3.msi"));
    }

    private UpdateInfo updateWithoutExpectedSize(byte[] installer) {
        return new UpdateInfo("1.2.2", "1.2.3", "BRED 1.2.3", "Notes",
                URI.create("https://github.com/Clarkisbred/receipt-management-system/releases/tag/v1.2.3"),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                        + "/download/v1.2.3/BRED-Setup-1.2.3.msi"),
                "BRED-Setup-1.2.3.msi", Optional.empty(), OptionalLong.empty(), Optional.empty());
    }

    private UpdateInfo update(byte[] installer, String checksum, String filename) {
        return update(installer, checksum, filename,
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/download/v1.2.3/" + filename));
    }

    private UpdateInfo update(byte[] installer, String checksum, String filename, URI url) {
        return new UpdateInfo("1.2.2", "1.2.3", "BRED 1.2.3", "Notes",
                URI.create("https://github.com/Clarkisbred/receipt-management-system/releases/tag/v1.2.3"),
                url, filename, Optional.empty(), OptionalLong.of(installer.length),
                Optional.ofNullable(checksum));
    }

    private void assertDownloadFailure(UpdateInfo info, String message) {
        Exception failure = assertThrows(Exception.class,
                () -> manager.download(info).completion().join());
        Throwable cause = failure instanceof UpdateDownloadException ? failure : failure.getCause();
        assertTrue(cause instanceof UpdateDownloadException, () -> "Unexpected failure: " + failure);
        assertTrue(cause.getMessage().toLowerCase().contains(message.toLowerCase()), cause.getMessage());
    }

    private static byte[] fakeMsiCompoundFile() {
        byte[] bytes = new byte[512];
        byte[] signature = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        bytes[0x1a] = 3;
        bytes[0x1c] = (byte) 0xfe;
        bytes[0x1d] = (byte) 0xff;
        bytes[0x1e] = 9;
        bytes[0x20] = 6;
        return bytes;
    }

    private static String sha256(byte[] input) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
    }

    private void waitForPartialCleanup() throws InterruptedException {
        Path partial = tempDir.resolve("BRED-Setup-1.2.3.msi.part");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (Files.exists(partial) && System.nanoTime() < deadline) Thread.sleep(10);
    }
}
