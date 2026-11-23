package com.gudo.rms;

import com.gudo.rms.config.AppConfig;
import com.gudo.rms.config.AppContext;
import com.gudo.rms.ui.MainWindow;
import com.gudo.rms.ui.theme.ThemeManager;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.nio.file.Path;

/** Launches the Business Receipt Encoding and Documentation System (BRED). */
public final class Main {
    /** Prevents instantiation of the application launcher. */
    private Main() { }

    /** Folder for local data: {@code RMS_DATA_DIR} if set, otherwise {@code RMS-Data} in the user's home folder. */
    static Path dataDir() {
        String custom = System.getenv("RMS_DATA_DIR");
        if (custom != null && !custom.isBlank()) return Path.of(custom.trim());
        return Path.of(System.getProperty("user.home"), "RMS-Data");
    }

    /** Starts the application: Supabase when it is configured, otherwise data kept on this computer. */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ThemeManager.installSaved();
            AppContext ctx = null;
            String problem = null;
            try {
                AppConfig config = AppConfig.load();
                ctx = config.isConfigured() ? AppContext.create(config) : AppContext.createLocal(dataDir());
            } catch (IllegalStateException e) {
                problem = e.getMessage();
            }
            if (ctx == null && problem != null && !problem.contains("Supabase")) {
                JOptionPane.showMessageDialog(null, problem,
                        "BRED - Business Receipt Encoding and Documentation System", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
            new MainWindow(ctx, problem).setVisible(true);
        });
    }
}
