package com.bred.rms;

import com.bred.rms.ui.theme.RmsColors;
import com.bred.rms.ui.theme.ThemeManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SmokeTest {
    @Test
    void themeColorsExist() {
        assertNotNull(RmsColors.PRIMARY);
        assertDoesNotThrow(ThemeManager::isDark);
    }
}
