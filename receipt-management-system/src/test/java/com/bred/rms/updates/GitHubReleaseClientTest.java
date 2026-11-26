package com.bred.rms.updates;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubReleaseClientTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void parsesStableReleaseAndExpectedWindowsInstaller() throws Exception {
        GitHubReleaseClient.Release release = client(releaseJson("v1.2.3", false, false,
                "BRED-Setup-1.2.3.msi", trustedInstaller("v1.2.3", "BRED-Setup-1.2.3.msi"), "uploaded"))
                .latestStableRelease().join();

        assertEquals("1.2.3", release.version());
        assertEquals("BRED 1.2.3", release.name());
        assertEquals("Release notes", release.notes());
        assertEquals("BRED-Setup-1.2.3.msi", release.installerFilename());
        assertEquals(Instant.parse("2026-11-25T10:00:00Z"), release.publishedAt().orElseThrow());
        assertEquals(OptionalLong.of(128), release.installerSize());
        assertEquals("a".repeat(64), release.installerSha256().orElseThrow());
    }

    @Test
    void rejectsDraftAndPrereleaseReleases() throws Exception {
        assertFailure(client(releaseJson("v1.2.3", true, false,
                "BRED-Setup-1.2.3.msi", trustedInstaller("v1.2.3", "BRED-Setup-1.2.3.msi"), "uploaded"))
                .latestStableRelease(), "draft");
        assertFailure(client(releaseJson("v1.2.3", false, true,
                "BRED-Setup-1.2.3.msi", trustedInstaller("v1.2.3", "BRED-Setup-1.2.3.msi"), "uploaded"))
                .latestStableRelease(), "prerelease");
    }

    @Test
    void rejectsMissingOrUntrustedInstaller() throws Exception {
        assertFailure(client(releaseJson("v1.2.3", false, false, "other.msi",
                trustedInstaller("v1.2.3", "other.msi"), "uploaded")).latestStableRelease(), "missing");
        assertFailure(client(releaseJson("v1.2.3", false, false,
                "BRED-Setup-1.2.3.msi", "https://evil.example/installer.msi", "uploaded"))
                .latestStableRelease(), "trusted");
    }

    @Test
    void rejectsMalformedResponseAndRateLimit() throws Exception {
        assertFailure(client("not json").latestStableRelease(), "unreadable");
        serve(403, "{\"message\":\"API rate limit exceeded\"}", "X-RateLimit-Remaining", "0");
        assertFailure(new GitHubReleaseClient(HttpClient.newHttpClient(), endpoint()).latestStableRelease(),
                "rate limit");
    }

    @Test
    void reportsNetworkFailure() throws Exception {
        startServer(200, "{}");
        URI closedEndpoint = endpoint();
        server.stop(0);
        server = null;
        assertFailure(new GitHubReleaseClient(HttpClient.newHttpClient(), closedEndpoint).latestStableRelease(),
                "reach GitHub");
    }

    @Test
    void distinguishesSameAndNewerVersions() throws Exception {
        GitHubReleaseClient releaseClient = client(releaseJson("v1.0.10", false, false,
                "BRED-Setup-1.0.10.msi", trustedInstaller("v1.0.10", "BRED-Setup-1.0.10.msi"), "uploaded"));
        Preferences node = Preferences.userRoot().node("com/bred/rms/test-updates/" + UUID.randomUUID());
        try {
            UpdatePreferences preferences = new UpdatePreferences(node);
            Optional<UpdateInfo> available = new UpdateChecker("1.0.9", releaseClient, preferences)
                    .checkForUpdate().join();
            assertTrue(available.isPresent());
            assertEquals("1.0.10", available.orElseThrow().latestVersion());

            Optional<UpdateInfo> same = new UpdateChecker("v1.0.10", releaseClient, preferences)
                    .checkForUpdate().join();
            assertTrue(same.isEmpty());
            assertTrue(preferences.lastAttemptedCheck() != null);
            assertTrue(preferences.lastSuccessfulCheck() != null);
        } finally {
            node.removeNode();
        }
    }

    private GitHubReleaseClient client(String body) throws IOException {
        startServer(200, body);
        return new GitHubReleaseClient(HttpClient.newHttpClient(), endpoint());
    }

    private void startServer(int status, String body) throws IOException {
        serve(status, body, null, null);
    }

    private void serve(int status, String body, String header, String value) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/latest", exchange -> {
            if (header != null) exchange.getResponseHeaders().add(header, value);
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();
    }

    private URI endpoint() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/latest");
    }

    private static String releaseJson(String tag, boolean draft, boolean prerelease,
                                      String assetName, String assetUrl, String assetState) {
        return """
                {
                  "tag_name":"%s",
                  "name":"BRED 1.2.3",
                  "body":"Release notes",
                  "draft":%s,
                  "prerelease":%s,
                  "html_url":"https://github.com/Clarkisbred/receipt-management-system/releases/tag/%s",
                  "published_at":"2026-11-25T10:00:00Z",
                  "assets":[{"name":"%s","state":"%s","size":128,"digest":"sha256:%s","browser_download_url":"%s"}]
                }
                """.formatted(tag, draft, prerelease, tag, assetName, assetState, "a".repeat(64), assetUrl);
    }

    private static String trustedInstaller(String tag, String filename) {
        return "https://github.com/Clarkisbred/receipt-management-system/releases/download/"
                + tag + "/" + filename;
    }

    private static void assertFailure(CompletableFuture<?> request, String messagePart) {
        CompletionException failure = assertThrows(CompletionException.class, request::join);
        Throwable cause = failure.getCause();
        assertTrue(cause instanceof UpdateCheckException, () -> "Unexpected failure: " + cause);
        assertTrue(cause.getMessage().toLowerCase().contains(messagePart.toLowerCase()),
                () -> "Unexpected message: " + cause.getMessage());
    }

}
