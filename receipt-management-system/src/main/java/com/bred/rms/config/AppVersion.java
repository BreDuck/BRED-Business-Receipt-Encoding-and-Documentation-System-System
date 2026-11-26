package com.bred.rms.config;

import com.bred.rms.Main;

/** The version of the installed BRED application. */
public final class AppVersion {
    private static final String DEVELOPMENT_VERSION = "0.1.0-SNAPSHOT";

    private AppVersion() { }

    public static String current() {
        String version = System.getProperty("jpackage.app-version");
        if (version != null && !version.isBlank()) return version;
        Package appPackage = Main.class.getPackage();
        version = appPackage == null ? null : appPackage.getImplementationVersion();
        if (version == null || version.isBlank()) version = System.getProperty("rms.version");
        return version == null || version.isBlank() ? DEVELOPMENT_VERSION : version;
    }
}
