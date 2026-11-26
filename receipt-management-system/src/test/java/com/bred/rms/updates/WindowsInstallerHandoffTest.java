package com.bred.rms.updates;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowsInstallerHandoffTest {
    @TempDir
    Path tempDir;

    private HttpServer server;
    private UpdateDownloadManager downloads;
    private ExecutorService executor;

    @AfterEach
    void cleanUp() throws IOException {
        if (downloads != null) downloads.close();
        if (server != null) server.stop(0);
    }

    @Test
    void startsInteractiveMsiHandoffThatWaitsForBredAndRequestsElevation() throws Exception {
        UpdateDownloadManager.DownloadResult result = downloadedMsi(null);
        AtomicReference<List<String>> command = new AtomicReference<>();
        WindowsInstallerHandoff handoff = new WindowsInstallerHandoff(tempDir.resolve("results"),
                path -> compatibleMetadata(), (args, log) -> {
                    command.set(args);
                    Files.writeString(tempDir.resolve("results/install-1.2.3.ready"), "READY");
                    return new FakeProcess();
                }, true);

        WindowsInstallerHandoff.Handoff started = handoff.startAfterExit(result, ProcessHandle.current().pid());

        assertEquals("1.2.3", started.version());
        List<String> args = command.get();
        assertTrue(args.contains("-EncodedCommand"));
        String script = new String(Base64.getDecoder().decode(args.get(args.indexOf("-EncodedCommand") + 1)),
                StandardCharsets.UTF_16LE);
        assertTrue(script.contains("while ($true)"));
        assertTrue(script.contains("Get-Process -Id $pidToWait"));
        assertTrue(script.contains("msiexec.exe"));
        assertTrue(script.contains("'/i \"' + $installer + '\" /norestart'"));
        assertTrue(script.contains("-Verb RunAs"));
        assertTrue(script.contains("Get-FileHash"));
        assertFalse(script.toLowerCase(java.util.Locale.ROOT).contains("/quiet"));
        assertFalse(script.contains("Stop-Process"));
        assertFalse(Files.exists(tempDir.resolve("results/install-1.2.3.ready")));
        assertTrue(Files.exists(result.installerPath()));
    }

    @Test
    void rejectsModifiedInstallerBeforeStartingHandoff() throws Exception {
        UpdateDownloadManager.DownloadResult result = downloadedMsi(null);
        Files.write(result.installerPath(), new byte[512]);
        AtomicReference<Boolean> started = new AtomicReference<>(false);
        WindowsInstallerHandoff handoff = new WindowsInstallerHandoff(tempDir.resolve("results"),
                path -> compatibleMetadata(), (args, log) -> {
                    started.set(true);
                    return new FakeProcess();
                }, true);

        assertThrows(UpdateDownloadException.class,
                () -> handoff.startAfterExit(result, ProcessHandle.current().pid()));
        assertFalse(started.get());
    }

    @Test
    void rejectsWrongUpgradeIdentityAndNonWindowsInstallation() throws Exception {
        UpdateDownloadManager.DownloadResult result = downloadedMsi(null);
        WindowsInstallerHandoff wrongIdentity = new WindowsInstallerHandoff(tempDir.resolve("results"),
                path -> new WindowsInstallerHandoff.PackageMetadata("1.2.3", "{PRODUCT}", true,
                        List.of(new WindowsInstallerHandoff.UpgradeRange("{WRONG}", "1.0.0", "2.0.0", 0))),
                (args, log) -> new FakeProcess(), true);
        assertThrows(UpdateDownloadException.class,
                () -> wrongIdentity.startAfterExit(result, ProcessHandle.current().pid()));

        WindowsInstallerHandoff notWindows = new WindowsInstallerHandoff(tempDir.resolve("results"),
                path -> compatibleMetadata(), (args, log) -> new FakeProcess(), false);
        assertThrows(UpdateDownloadException.class,
                () -> notWindows.startAfterExit(result, ProcessHandle.current().pid()));
    }

    @Test
    void respectsInclusiveAndExclusiveUpgradeVersionBounds() throws Exception {
        UpdateDownloadManager.DownloadResult result = downloadedMsi(null);
        String code = WindowsInstallerHandoff.REQUIRED_UPGRADE_CODE;
        WindowsInstallerHandoff excluded = new WindowsInstallerHandoff(tempDir.resolve("excluded"),
                path -> new WindowsInstallerHandoff.PackageMetadata("1.2.3", "{PRODUCT}", true,
                        List.of(new WindowsInstallerHandoff.UpgradeRange("{" + code + "}",
                                "1.2.2", "1.3.0", 0))),
                (args, log) -> new FakeProcess(), true);
        assertThrows(UpdateDownloadException.class,
                () -> excluded.startAfterExit(result, ProcessHandle.current().pid()));

        WindowsInstallerHandoff included = new WindowsInstallerHandoff(tempDir.resolve("included"),
                path -> new WindowsInstallerHandoff.PackageMetadata("1.2.3", "{PRODUCT}", true,
                        List.of(new WindowsInstallerHandoff.UpgradeRange("{" + code + "}",
                                "1.2.2", "1.3.0", 0x100))),
                (args, log) -> {
                    Files.writeString(tempDir.resolve("included")
                            .resolve("install-1.2.3.ready"), "READY");
                    return new FakeProcess();
                }, true);
        assertEquals("1.2.3", included.startAfterExit(result, ProcessHandle.current().pid()).version());
    }

    @Test
    void reportsHelperLaunchFailureAndVerifiesInstalledVersionOnNextLaunch() throws Exception {
        UpdateDownloadManager.DownloadResult result = downloadedMsi(null);
        WindowsInstallerHandoff launchFailure = new WindowsInstallerHandoff(tempDir.resolve("launch-failure"),
                path -> compatibleMetadata(), (args, log) -> {
                    throw new IOException("simulated helper launch failure");
                }, true);
        assertThrows(UpdateDownloadException.class,
                () -> launchFailure.startAfterExit(result, ProcessHandle.current().pid()));
        assertTrue(Files.exists(result.installerPath()));

        Path resultDirectory = tempDir.resolve("previous-result");
        Files.createDirectories(resultDirectory);
        Files.writeString(resultDirectory.resolve("install-1.2.3.properties"),
                "status=SUCCESS\nversion=1.2.3\nexitCode=0\n");
        WindowsInstallerHandoff previous = new WindowsInstallerHandoff(resultDirectory,
                path -> compatibleMetadata(), (args, log) -> new FakeProcess(), false);
        assertTrue(previous.readPreviousResult("1.0.5").orElseThrow().contains("still running version 1.0.5"));
        assertFalse(Files.exists(resultDirectory.resolve("install-1.2.3.properties")));
    }

    private UpdateDownloadManager.DownloadResult downloadedMsi(String checksum) throws Exception {
        byte[] content = fakeMsi();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/download/", exchange -> {
            exchange.sendResponseHeaders(200, content.length);
            try (var output = exchange.getResponseBody()) {
                output.write(content);
            }
        });
        server.start();
        executor = Executors.newSingleThreadExecutor();
        downloads = new UpdateDownloadManager(tempDir.resolve("staging"), HttpClient.newHttpClient(), executor,
                uri -> true, uri -> uri.getHost().equals("127.0.0.1"));
        UpdateInfo info = new UpdateInfo("1.2.2", "1.2.3", "BRED 1.2.3", "release notes",
                URI.create("https://github.com/Clarkisbred/receipt-management-system/releases/tag/v1.2.3"),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                        + "/download/v1.2.3/BRED-Setup-1.2.3.msi"),
                "BRED-Setup-1.2.3.msi", Optional.empty(), OptionalLong.of(content.length),
                Optional.ofNullable(checksum));
        return downloads.download(info).completion().get(5, TimeUnit.SECONDS);
    }

    private static WindowsInstallerHandoff.PackageMetadata compatibleMetadata() {
        String code = WindowsInstallerHandoff.REQUIRED_UPGRADE_CODE;
        return new WindowsInstallerHandoff.PackageMetadata("1.2.3", "{PRODUCT-CODE}", true,
                List.of(new WindowsInstallerHandoff.UpgradeRange("{" + code + "}", "1.0.0", "2.0.0", 0)));
    }

    private static byte[] fakeMsi() {
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

    private static final class FakeProcess extends Process {
        @Override public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
        @Override public InputStream getInputStream() { return InputStream.nullInputStream(); }
        @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
        @Override public int waitFor() { return 0; }
        @Override public int exitValue() { return 0; }
        @Override public void destroy() { }
        @Override public long pid() { return 12345; }
    }
}
