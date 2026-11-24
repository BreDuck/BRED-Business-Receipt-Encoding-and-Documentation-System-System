package com.bred.rms.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Properties;

/** Loads public Supabase settings. Precedence: environment variables, ./application.properties, classpath. */
public final class AppConfig {
    private final String supabaseUrl;
    private final String anonKey;

    /** Creates an app config instance. */
    private AppConfig(String supabaseUrl, String anonKey) {
        this.supabaseUrl = supabaseUrl;
        this.anonKey = anonKey;
    }

    /** Loads Supabase settings from environment variables, a local properties file, or the classpath. */
    public static AppConfig load() {
        Properties props = new Properties();
        try {
            InputStream source = openProperties();
            if (source != null) {
                try (InputStream in = source) { props.load(in); }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read application.properties: " + e.getMessage(), e);
        }
        String url = firstNonBlank(System.getenv("RMS_SUPABASE_URL"), props.getProperty("supabase.url"));
        String key = firstNonBlank(System.getenv("RMS_SUPABASE_ANON_KEY"), props.getProperty("supabase.anonKey"));
        if (isPrivilegedKey(key)) {
            throw new IllegalStateException("A privileged Supabase key was supplied. Use only the public anon/publishable key in the desktop app.");
        }
        return new AppConfig(url.replaceAll("/+$", ""), key);
    }

    private static InputStream openProperties() throws IOException {
        Path workingDirectoryFile = Path.of("application.properties");
        if (Files.isRegularFile(workingDirectoryFile)) return Files.newInputStream(workingDirectoryFile);

        try {
            Path location = Path.of(AppConfig.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path directory = Files.isDirectory(location) ? location : location.getParent();
            for (int i = 0; directory != null && i < 4; i++, directory = directory.getParent()) {
                Path candidate = directory.resolve("application.properties");
                if (Files.isRegularFile(candidate)) return Files.newInputStream(candidate);
            }
        } catch (URISyntaxException | SecurityException e) {
            throw new IOException("Could not locate application.properties beside the application.", e);
        }

        return AppConfig.class.getResourceAsStream("/application.properties");
    }

    /** Builds a config directly from values (used by tests and tools). Rejects privileged keys. */
    public static AppConfig of(String supabaseUrl, String anonKey) {
        if (isPrivilegedKey(anonKey)) {
            throw new IllegalStateException("A privileged Supabase key was supplied. Use only the public anon/publishable key in the desktop app.");
        }
        return new AppConfig(supabaseUrl.replaceAll("/+$", ""), anonKey);
    }

    /** Reports whether this app config is configured. */
    public boolean isConfigured() {
        return !supabaseUrl.isBlank() && !anonKey.isBlank() && !supabaseUrl.contains("YOUR-PROJECT-REF");
    }

    /** Returns the configured Supabase project URL. */
    public String supabaseUrl() { return supabaseUrl; }
    /** Returns the configured public Supabase anon key. */
    public String anonKey() { return anonKey; }

    /** Rejects service-role JWTs and new-style secret keys. */
    public static boolean isPrivilegedKey(String key) {
        if (key == null || key.isBlank()) return false;
        if (key.startsWith("sb_secret_")) return true;
        String[] parts = key.split("\\.");
        if (parts.length == 3) {
            try {
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                return payload.contains("\"service_role\"");
            } catch (IllegalArgumentException ignored) { /* not a JWT */ }
        }
        return false;
    }

    /** Returns the first non-blank trimmed value, or an empty string when both are blank. */
    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a.trim();
        return b == null ? "" : b.trim();
    }
}
