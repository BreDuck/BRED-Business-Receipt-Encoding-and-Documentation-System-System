package com.gudo.rms.ui.theme;

import java.awt.Color;

/** Single source of truth for brand colors. Theme-dependent values resolve through ThemeManager. */
public final class RmsColors {
    /** Prevents instantiation of this utility class. */
    private RmsColors() { }

    public static final Color PRIMARY = Color.decode("#1F5FBF");
    public static final Color PRIMARY_DARK = Color.decode("#164687");
    public static final Color PRIMARY_LIGHT = Color.decode("#E8F0FC");
    public static final Color SUCCESS = Color.decode("#218838");
    public static final Color WARNING = Color.decode("#E6A700");
    public static final Color DANGER = Color.decode("#D62828");
    public static final Color INFO = Color.decode("#2474C7");
    public static final Color SECONDARY_TEXT = Color.decode("#747B87");

    public static final Color DARK_BACKGROUND = Color.decode("#15171C");
    public static final Color DARK_SURFACE = Color.decode("#20232B");
    public static final Color DARK_TEXT = Color.decode("#F3F4F6");
    public static final Color LIGHT_BACKGROUND = Color.decode("#F5F6F8");
    public static final Color LIGHT_SURFACE = Color.decode("#FFFFFF");
    public static final Color LIGHT_TEXT = Color.decode("#202124");

    /** Returns the background color for the active theme. */
    public static Color background() { return ThemeManager.isDark() ? DARK_BACKGROUND : LIGHT_BACKGROUND; }
    /** Returns the surface color for the active theme. */
    public static Color surface() { return ThemeManager.isDark() ? DARK_SURFACE : LIGHT_SURFACE; }
    /** Returns the primary text color for the active theme. */
    public static Color text() { return ThemeManager.isDark() ? DARK_TEXT : LIGHT_TEXT; }
    /** Returns the border color for the active theme. */
    public static Color border() { return ThemeManager.isDark() ? Color.decode("#343843") : Color.decode("#E1E3E8"); }
}
