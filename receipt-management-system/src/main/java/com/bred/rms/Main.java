package com.bred.rms;

import com.bred.rms.config.AppConfig;
import com.bred.rms.config.AppContext;
import com.bred.rms.ui.MainWindow;
import com.bred.rms.ui.theme.ThemeManager;

import javax.swing.SwingUtilities;

/** Launches the Business Receipt Encoding and Documentation System (BRED). */
public final class Main {
    /** Prevents instantiation of the application launcher. */
    private Main() { }

    /** Starts the application using the configured Supabase project. */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ThemeManager.installSaved();
            AppContext ctx = null;
            String problem = null;
            try {
                AppConfig config = AppConfig.load();
                if (config.isConfigured()) {
                    ctx = AppContext.create(config);
                } else {
                    problem = "Supabase is not configured. Set RMS_SUPABASE_URL and RMS_SUPABASE_ANON_KEY "
                            + "or add them to application.properties.";
                }
            } catch (IllegalStateException e) {
                problem = e.getMessage();
            }
            new MainWindow(ctx, problem).setVisible(true);
        });
    }
}
