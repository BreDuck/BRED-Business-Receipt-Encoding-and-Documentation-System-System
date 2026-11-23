package com.gudo.rms.ui;

import com.gudo.rms.config.AppContext;
import com.gudo.rms.models.User;
import com.gudo.rms.ui.theme.RmsColors;
import com.gudo.rms.ui.theme.ThemeManager;
import com.gudo.rms.utils.BackgroundTask;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import java.awt.Font;
import java.util.Arrays;

/** Account details, theme switch and "change my password". */
public final class SettingsPanel extends JPanel {
    private final JPasswordField newPassword = new JPasswordField(20);
    private final JPasswordField confirm = new JPasswordField(20);
    private final JLabel message = new JLabel(" ");
    private final JButton change = new JButton("Change password");

    /** Builds the settings screen for the signed-in user. */
    public SettingsPanel(AppContext ctx) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        User u = ctx.currentUser.get();
        add(heading("Account"));
        add(line(u == null ? "Not signed in" : u.fullName() + " · " + u.email() + " · " + u.role().label()));
        add(line("Data stored in: " + ctx.storageLabel));
        add(Box.createVerticalStrut(20));
        add(heading("Appearance"));
        JButton theme = new JButton(ThemeManager.isDark() ? "Switch to light mode" : "Switch to dark mode");
        theme.addActionListener(e -> {
            ThemeManager.toggle();
            theme.setText(ThemeManager.isDark() ? "Switch to light mode" : "Switch to dark mode");
        });
        theme.setAlignmentX(LEFT_ALIGNMENT);
        add(theme);
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
            show(e instanceof com.gudo.rms.services.AuthException && e.getMessage() != null ? e.getMessage() : UiErrors.friendly(e), true);
        });
    }

    private void show(String text, boolean bad) {
        message.setForeground(bad ? RmsColors.DANGER : RmsColors.SUCCESS);
        message.setText(text);
    }
}
