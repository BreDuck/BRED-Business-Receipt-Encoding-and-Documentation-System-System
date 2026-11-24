package com.bred.rms.ui;

import com.bred.rms.config.AppContext;
import com.bred.rms.models.User;
import com.bred.rms.ui.theme.RmsColors;
import com.bred.rms.ui.theme.ThemeManager;
import com.bred.rms.utils.BackgroundTask;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import java.awt.Dimension;
import java.awt.Font;
import java.nio.file.Path;
import java.util.Arrays;

/** Account details, appearance options and "change my password". */
public final class SettingsPanel extends JPanel {
    private final JPasswordField newPassword = new JPasswordField(20);
    private final JPasswordField confirm = new JPasswordField(20);
    private final JLabel message = new JLabel(" ");
    private final JButton change = new JButton("Change password");
    private final JButton importLocal = new JButton("Import local data into Supabase");
    private final JLabel importMessage = new JLabel(" ");

    /** Builds the settings screen for the signed-in user. */
    public SettingsPanel(AppContext ctx) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        User u = ctx.currentUser.get();
        add(heading("Account"));
        add(line(u == null ? "Not signed in" : u.fullName() + " · " + u.email() + " · " + u.role().label()));
        add(line("Data stored in: " + ctx.storageLabel));
        if (ctx.localDataImporter != null && u != null && u.role() == com.bred.rms.models.Role.ADMIN) {
            add(Box.createVerticalStrut(12));
            add(heading("Move existing local data to Supabase"));
            add(line("Imports suppliers, projects, receipts, attachments and activity history."));
            add(line("Local passwords cannot be transferred; recreate user accounts in Supabase."));
            importLocal.setAlignmentX(LEFT_ALIGNMENT);
            importLocal.addActionListener(e -> chooseLocalDataDirectory(ctx));
            add(importLocal);
            importMessage.setAlignmentX(LEFT_ALIGNMENT);
            importMessage.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
            add(importMessage);
        }
        add(Box.createVerticalStrut(20));
        add(heading("Appearance"));
        JButton theme = new JButton(ThemeManager.isDark() ? "Switch to light mode" : "Switch to dark mode");
        theme.addActionListener(e -> {
            ThemeManager.toggle();
            theme.setText(ThemeManager.isDark() ? "Switch to light mode" : "Switch to dark mode");
        });
        theme.setAlignmentX(LEFT_ALIGNMENT);
        add(theme);
        add(Box.createVerticalStrut(8));
        add(heading("Other Appearances"));
        JComboBox<ThemeManager.Appearance> appearances =
                new JComboBox<>(ThemeManager.Appearance.values());
        appearances.setSelectedItem(ThemeManager.appearance());
        appearances.setAlignmentX(LEFT_ALIGNMENT);
        Dimension appearanceSize = appearances.getPreferredSize();
        appearances.setMaximumSize(appearanceSize);
        appearances.addActionListener(e ->
                ThemeManager.setAppearance((ThemeManager.Appearance) appearances.getSelectedItem()));
        add(appearances);
        add(Box.createVerticalStrut(20));
        add(heading("Change my password"));
        add(labelled("New password (min. 6)", newPassword));
        add(labelled("Repeat new password", confirm));
        change.setAlignmentX(LEFT_ALIGNMENT);
        change.addActionListener(e -> submit(ctx));
        add(change);
        message.setAlignmentX(LEFT_ALIGNMENT);
        message.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        add(message);
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    }

    private static JLabel heading(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 15f));
        l.setAlignmentX(LEFT_ALIGNMENT);
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        return l;
    }

    private static JLabel line(String text) {
        JLabel l = new JLabel(text);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private static JPanel labelled(String label, JPasswordField field) {
        JPanel p = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 2));
        p.setAlignmentX(LEFT_ALIGNMENT);
        JLabel l = new JLabel(label);
        l.setPreferredSize(new java.awt.Dimension(170, l.getPreferredSize().height));
        p.add(l);
        p.add(field);
        p.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    private void submit(AppContext ctx) {
        char[] a = newPassword.getPassword();
        char[] b = confirm.getPassword();
        if (!Arrays.equals(a, b)) {
            show("The two passwords do not match.", true);
            Arrays.fill(a, '\0');
            Arrays.fill(b, '\0');
            return;
        }
        String pw = new String(a);
        Arrays.fill(a, '\0');
        Arrays.fill(b, '\0');
        change.setEnabled(false);
        BackgroundTask.run(() -> {
            ctx.auth.changePassword(pw);
            return Boolean.TRUE;
        }, ok -> {
            change.setEnabled(true);
            newPassword.setText("");
            confirm.setText("");
            show("Password changed.", false);
        }, e -> {
            change.setEnabled(true);
            show(e instanceof com.bred.rms.services.AuthException && e.getMessage() != null ? e.getMessage() : UiErrors.friendly(e), true);
        });
    }

    private void chooseLocalDataDirectory(AppContext ctx) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select the folder containing data.json");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        String configured = System.getenv("RMS_DATA_DIR");
        Path defaultDirectory = configured == null || configured.isBlank()
                ? Path.of(System.getProperty("user.home"), "RMS-Data")
                : Path.of(configured.trim());
        chooser.setSelectedFile(defaultDirectory.toFile());
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        int answer = javax.swing.JOptionPane.showConfirmDialog(this,
                "This imports data into the current Supabase project. Records with matching IDs are updated. "
                        + "The local data is not deleted. Continue?",
                "Import local data", javax.swing.JOptionPane.OK_CANCEL_OPTION, javax.swing.JOptionPane.WARNING_MESSAGE);
        if (answer != javax.swing.JOptionPane.OK_OPTION) return;

        Path directory = chooser.getSelectedFile().toPath();
        importLocal.setEnabled(false);
        showImport("Importing local data…", false);
        BackgroundTask.run(() -> ctx.localDataImporter.importDirectory(directory), result -> {
            importLocal.setEnabled(true);
            showImport("Imported " + result.suppliers() + " suppliers, " + result.projects() + " projects, "
                    + result.receipts() + " receipts, " + result.attachments() + " attachments, and "
                    + result.activityEntries() + " activity entries. " + result.localUsers()
                    + " local accounts were not transferred; recreate accounts in Users.", false);
        }, error -> {
            importLocal.setEnabled(true);
            showImport(UiErrors.friendly(error), true);
        });
    }

    private void showImport(String text, boolean bad) {
        importMessage.setForeground(bad ? RmsColors.DANGER : RmsColors.SUCCESS);
        importMessage.setText(text);
    }

    private void show(String text, boolean bad) {
        message.setForeground(bad ? RmsColors.DANGER : RmsColors.SUCCESS);
        message.setText(text);
    }
}
