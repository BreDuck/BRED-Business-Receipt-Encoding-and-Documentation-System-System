package com.bred.rms.updates;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Asynchronously retrieves and validates BRED's latest published GitHub release. */
public final class GitHubReleaseClient {
    public static final URI LATEST_RELEASE_API = URI.create(
            "https://api.github.com/repos/Clarkisbred/BRED-Business-Receipt-Encoding-and-Documentation-System-System/releases/latest");

    private static final String REPOSITORY_RELEASE_PATH =
            "/Clarkisbred/BRED-Business-Receipt-Encoding-and-Documentation-System-System/releases/";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http;
    private final URI endpoint;

    public GitHubReleaseClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER)
                .build(), LATEST_RELEASE_API);
    }

    GitHubReleaseClient(HttpClient http, URI endpoint) {
        this.http = http;
        this.endpoint = endpoint;
    }

    /** Fetches release metadata without blocking the calling thread. */
    public CompletableFuture<Release> latestStableRelease() {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "BRED-Desktop-Updater")
                .GET()
                .build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((response, failure) -> {
                    if (failure != null) throw new CompletionException(networkFailure(failure));
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new CompletionException(httpFailure(response));
                    }
                    try {
                        return parseRelease(response.body());
                    } catch (UpdateCheckException e) {
                        throw new CompletionException(e);
                    }
                });
    }

    private static Release parseRelease(String body) {
        JsonNode root;
        try {
            root = JSON.readTree(body);
        } catch (IOException e) {
            throw new UpdateCheckException("GitHub returned an unreadable release response.", e);
        }
        if (root == null || !root.isObject()) {
            throw new UpdateCheckException("GitHub returned an invalid release response.");
        }
        JsonNode draft = root.get("draft");
        JsonNode prerelease = root.get("prerelease");
        if (draft == null || !draft.isBoolean() || prerelease == null || !prerelease.isBoolean()) {
            throw new UpdateCheckException("GitHub release metadata is missing its stable-release status.");
        }
        if (draft.booleanValue()) {
            throw new UpdateCheckException("The latest GitHub release is still a draft.");
        }
        if (prerelease.booleanValue()) {
            throw new UpdateCheckException("The latest GitHub release is a prerelease and was ignored.");
        }

        String tag = text(root, "tag_name");
        String version = VersionComparator.normalize(tag)
                .orElseThrow(() -> new UpdateCheckException("The GitHub release has an invalid version tag."));
        URI releasePage = trustedReleasePage(text(root, "html_url"), tag);
        String expectedFilename = "BRED-Setup-" + version + ".msi";
        JsonNode assets = root.get("assets");
        if (assets == null || !assets.isArray()) {
            throw new UpdateCheckException("The GitHub release does not list its installer asset.");
        }

        URI installerUrl = null;
        OptionalLong installerSize = OptionalLong.empty();
        Optional<String> installerSha256 = Optional.empty();
        for (JsonNode asset : assets) {
            if (!asset.isObject() || !expectedFilename.equals(text(asset, "name"))) continue;
            if (!"uploaded".equals(text(asset, "state"))) {
                throw new UpdateCheckException("The GitHub installer asset is not fully uploaded.");
            }
            installerUrl = trustedInstallerUrl(text(asset, "browser_download_url"), tag, expectedFilename);
            JsonNode size = asset.get("size");
            if (size != null && !size.isNull()) {
                if (!size.isIntegralNumber() || !size.canConvertToLong() || size.longValue() < 0) {
                    throw new UpdateCheckException("The GitHub installer asset has an invalid size.");
                }
                installerSize = OptionalLong.of(size.longValue());
            }
            JsonNode digest = asset.get("digest");
            if (digest != null && !digest.isNull() && !digest.asText().isBlank()) {
                if (!digest.isTextual()) {
                    throw new UpdateCheckException("The GitHub installer asset has an invalid digest.");
                }
                String value = digest.asText();
                if (value.startsWith("sha256:")) {
                    String checksum = value.substring("sha256:".length());
                    if (!checksum.matches("[0-9a-fA-F]{64}")) {
                        throw new UpdateCheckException("The GitHub installer asset has an invalid SHA-256 digest.");
                    }
                    installerSha256 = Optional.of(checksum.toLowerCase(java.util.Locale.ROOT));
                }
            }
            break;
        }
        if (installerUrl == null) {
            throw new UpdateCheckException("The GitHub release is missing " + expectedFilename + ".");
        }

        Optional<Instant> publishedAt = parsePublishedAt(root.get("published_at"));
        String name = text(root, "name");
        if (name.isBlank()) name = tag;
        String notes = text(root, "body");
        return new Release(version, name, notes, releasePage, installerUrl, expectedFilename, publishedAt,
                installerSize, installerSha256);
    }

    private static Optional<Instant> parsePublishedAt(JsonNode value) {
        if (value == null || value.isNull() || value.asText().isBlank()) return Optional.empty();
        try {
            return Optional.of(Instant.parse(value.asText()));
        } catch (DateTimeParseException e) {
            throw new UpdateCheckException("The GitHub release has an invalid publication date.", e);
        }
    }

    private static URI trustedReleasePage(String value, String tag) {
        URI uri = trustedGitHubUrl(value, "release page");
        String expectedPath = REPOSITORY_RELEASE_PATH + "tag/" + tag;
        if (!expectedPath.equals(uri.getPath())) {
            throw new UpdateCheckException("The GitHub release page is not from the configured BRED repository.");
        }
        return uri;
    }

    /** Validates the canonical HTTPS URL for the configured BRED Windows release asset. */
    public static void validateInstallerDownloadUrl(URI uri, String version, String filename) {
        if (uri == null) throw new UpdateCheckException("The installer download URL is missing.");
        URI trusted = trustedGitHubUrl(uri.toString(), "installer download");
        String path = trusted.getPath();
        String expectedPrefix = REPOSITORY_RELEASE_PATH + "download/";
        if (!path.startsWith(expectedPrefix) || !path.endsWith("/" + filename)) {
            throw new UpdateCheckException("The installer URL is not a BRED release asset from the configured repository.");
        }
        String tag = path.substring(expectedPrefix.length(), path.length() - filename.length() - 1);
        if (!VersionComparator.normalize(tag).filter(version::equals).isPresent()) {
            throw new UpdateCheckException("The installer URL does not match the requested BRED version.");
        }
    }

    private static URI trustedInstallerUrl(String value, String tag, String filename) {
        URI uri = trustedGitHubUrl(value, "installer download");
        String expectedPath = REPOSITORY_RELEASE_PATH + "download/" + tag + "/" + filename;
        if (!expectedPath.equals(uri.getPath())) {
            throw new UpdateCheckException("The installer URL is not a BRED release asset from the configured repository.");
        }
        return uri;
    }

    private static URI trustedGitHubUrl(String value, String description) {
        if (value == null || value.isBlank()) {
            throw new UpdateCheckException("GitHub omitted the " + description + " URL.");
        }
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !"github.com".equalsIgnoreCase(uri.getHost())
                    || (uri.getPort() != -1 && uri.getPort() != 443) || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new UpdateCheckException("The " + description + " URL is not a trusted GitHub HTTPS URL.");
            }
            return uri;
        } catch (IllegalArgumentException e) {
            throw new UpdateCheckException("GitHub returned an invalid " + description + " URL.", e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.isValueNode() ? "" : value.asText();
    }

    private static UpdateCheckException httpFailure(HttpResponse<?> response) {
        int status = response.statusCode();
        if (status == 403 || status == 429) {
            String remaining = response.headers().firstValue("X-RateLimit-Remaining").orElse("");
            if (status == 429 || "0".equals(remaining)) {
                return new UpdateCheckException("GitHub API rate limit reached. Try checking for updates later.");
            }
        }
        if (status == 404) {
            return new UpdateCheckException("No published BRED release was found on GitHub.");
        }
        return new UpdateCheckException("GitHub update check failed with HTTP " + status + ".");
    }

    private static UpdateCheckException networkFailure(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
        if (cause instanceof java.net.http.HttpTimeoutException) {
            return new UpdateCheckException("The GitHub update check timed out.", cause);
        }
        if (cause instanceof IOException || cause instanceof java.net.ConnectException) {
            return new UpdateCheckException("Could not reach GitHub to check for updates.", cause);
        }
        return new UpdateCheckException("The GitHub update check failed.", cause);
    }

    /** Validated fields from a stable release and its expected Windows installer asset. */
    public record Release(String version, String name, String notes, URI pageUrl, URI installerUrl,
                          String installerFilename, Optional<Instant> publishedAt, OptionalLong installerSize,
                          Optional<String> installerSha256) { }
}
