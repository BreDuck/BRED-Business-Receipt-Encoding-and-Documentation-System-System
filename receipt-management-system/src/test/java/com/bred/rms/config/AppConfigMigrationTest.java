package com.bred.rms.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppConfigMigrationTest {
    @TempDir
    Path tempDir;

    @Test
    void migratesLegacyPropertiesWithoutDeletingTheOriginalAndUsesTheExternalCopy() throws Exception {
        Path legacy = tempDir.resolve("D-BRED").resolve("application.properties");
        Path user = tempDir.resolve("AppData").resolve("BRED").resolve("application.properties");
        Files.createDirectories(legacy.getParent());
        String oldValues = "supabase.url=https://project.example\nsupabase.anonKey=public-key\n";
        Files.writeString(legacy, oldValues, StandardCharsets.ISO_8859_1);

        Properties migrated = AppConfig.loadProperties(user, legacy, null);

        assertEquals("https://project.example", migrated.getProperty("supabase.url"));
        assertEquals("public-key", migrated.getProperty("supabase.anonKey"));
        assertEquals(oldValues, Files.readString(legacy, StandardCharsets.ISO_8859_1));
        assertEquals(oldValues, Files.readString(user, StandardCharsets.ISO_8859_1));

        Files.writeString(legacy, "supabase.url=https://changed.example\n", StandardCharsets.ISO_8859_1);
        Properties nextLaunch = AppConfig.loadProperties(user, legacy, null);
        assertEquals("https://project.example", nextLaunch.getProperty("supabase.url"));
    }

    @Test
    void doesNotOverwriteAnExistingUserConfiguration() throws Exception {
        Path legacy = tempDir.resolve("legacy.properties");
        Path user = tempDir.resolve("BRED").resolve("application.properties");
        Files.createDirectories(user.getParent());
        Files.writeString(legacy, "supabase.url=https://legacy.example\n");
        Files.writeString(user, "supabase.url=https://user.example\n");

        Properties loaded = AppConfig.loadProperties(user, legacy, null);

        assertEquals("https://user.example", loaded.getProperty("supabase.url"));
        assertEquals("supabase.url=https://legacy.example\n", Files.readString(legacy));
    }

    @Test
    void verifiesCopiedPropertiesBeforeUsingTheMigration() throws Exception {
        Path legacy = tempDir.resolve("legacy.properties");
        Path user = tempDir.resolve("BRED").resolve("application.properties");
        Files.writeString(legacy, "bad=" + "\\" + "u12xz");

        assertThrows(IllegalArgumentException.class, () -> AppConfig.loadProperties(user, legacy, null));

        assertFalse(Files.exists(user));
        assertTrue(Files.exists(legacy));
    }

    @Test
    void usesBundledPropertiesWhenNoFilesystemConfigurationExists() throws Exception {
        byte[] bundled = "supabase.url=https://bundled.example\n".getBytes(StandardCharsets.ISO_8859_1);

        Properties loaded = AppConfig.loadProperties(tempDir.resolve("BRED/application.properties"), null,
                new ByteArrayInputStream(bundled));

        assertEquals("https://bundled.example", loaded.getProperty("supabase.url"));
    }
}
