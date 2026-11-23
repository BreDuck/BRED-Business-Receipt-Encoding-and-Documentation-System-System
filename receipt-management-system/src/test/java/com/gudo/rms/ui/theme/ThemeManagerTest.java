package com.gudo.rms.ui.theme;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThemeManagerTest {
    @Test
    void signatureAppearanceKeepsItsColorsReadableInBothModes() {
        ThemeManager.installSaved();
        ThemeManager.Appearance previousAppearance = ThemeManager.appearance();
        boolean previousDark = ThemeManager.isDark();
        try {
            ThemeManager.setAppearance(ThemeManager.Appearance.BREDUCK_SIGNATURE);
            ThemeManager.installSaved();
            assertEquals(ThemeManager.Appearance.BREDUCK_SIGNATURE, ThemeManager.appearance());

            ThemeManager.apply(false);
            assertEquals(Color.decode("#995900"), RmsColors.primary());
            assertEquals(Color.decode("#F7A72E"), RmsColors.primaryAccent());
            assertEquals(Color.decode("#FCE283"), RmsColors.primaryLight());
            assertEquals(Color.decode("#FFF9E8"), RmsColors.background());
            assertEquals(Color.decode("#FFFDF5"), RmsColors.surface());
            assertEquals(Color.decode("#202124"), RmsColors.primaryForeground());

            ThemeManager.apply(true);
            assertEquals(Color.decode("#F7A72E"), RmsColors.primary());
            assertEquals(Color.decode("#F7A72E"), RmsColors.primaryAccent());
            assertEquals(Color.decode("#3B301C"), RmsColors.primaryLight());
            assertEquals(Color.decode("#202124"), RmsColors.primaryForeground());
        } finally {
            ThemeManager.setAppearance(previousAppearance);
            ThemeManager.apply(previousDark);
        }
    }
}
