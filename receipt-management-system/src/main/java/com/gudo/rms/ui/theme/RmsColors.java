package com.gudo.rms.ui.theme;

import java.awt.Color;

/** Single source of truth for brand colors. Theme-dependent values resolve through ThemeManager. */
public final class RmsColors {
    /** Prevents instantiation of this utility class. */
    private RmsColors() { }

    /** Default palette values retained for callers that need fixed colors. */
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

    /** Returns the readable primary accent for the selected appearance and mode. */
    public static Color primary() {
        if (ThemeManager.appearance() == ThemeManager.Appearance.BREDUCK_SIGNATURE) {
            return ThemeManager.isDark() ? Color.decode("#F7A72E") : Color.decode("#995900");
        }
        return PRIMARY;
    }

    /** Returns the bright palette accent for emphasis and filled controls. */
    public static Color primaryAccent() {
        return ThemeManager.appearance() == ThemeManager.Appearance.BREDUCK_SIGNATURE
                ? Color.decode("#F7A72E") : PRIMARY;
    }

    /** Returns the darker primary accent used for emphasis. */
    public static Color primaryDark() {
        if (ThemeManager.appearance() == ThemeManager.Appearance.BREDUCK_SIGNATURE) {
            return ThemeManager.isDark() ? Color.decode("#D88E1D") : Color.decode("#6F4000");
        }
        return PRIMARY_DARK;
    }

    /** Returns the selected-item highlight for the active appearance and mode. */
    public static Color primaryLight() {
        if (ThemeManager.appearance() == ThemeManager.Appearance.BREDUCK_SIGNATURE) {
            return ThemeManager.isDark() ? Color.decode("#3B301C") : Color.decode("#FCE283");
        }
        return PRIMARY_LIGHT;
    }

    /** Returns a foreground color with sufficient contrast on the primary accent. */
    public static Color primaryForeground() {
        return ThemeManager.appearance() == ThemeManager.Appearance.BREDUCK_SIGNATURE
                ? Color.decode("#202124") : Color.WHITE;
    }

    /** Returns the background color for the active theme. */
    public static Color background() {
        if (ThemeManager.isDark()) return DARK_BACKGROUND;
        return ThemeManager.appearance() == ThemeManager.Appearance.BREDUCK_SIGNATURE
                ? Color.decode("#FFF9E8") : LIGHT_BACKGROUND;
    }
    /** Returns the surface color for the active theme. */
    public static Color surface() {
        if (ThemeManager.isDark()) return DARK_SURFACE;
        return ThemeManager.appearance() == ThemeManager.Appearance.BREDUCK_SIGNATURE
                ? Color.decode("#FFFDF5") : LIGHT_SURFACE;
    }
    /** Returns the primary text color for the active theme. */
    public static Color text() { return ThemeManager.isDark() ? DARK_TEXT : LIGHT_TEXT; }
    /** Returns the border color for the active theme. */
    public static Color border() {
        if (ThemeManager.isDark()) return Color.decode("#343843");
        return ThemeManager.appearance() == ThemeManager.Appearance.BREDUCK_SIGNATURE
                ? Color.decode("#EFE2B7") : Color.decode("#E1E3E8");
    }
}