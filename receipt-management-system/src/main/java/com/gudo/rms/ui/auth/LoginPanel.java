package com.gudo.rms.ui.auth;

import com.gudo.rms.ui.theme.RmsColors;
import com.gudo.rms.ui.theme.ThemeManager;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.AbstractBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.Shape;
import java.util.function.Consumer;

/** Sign-in screen for the Receipt Management System. */
public final class LoginPanel extends JLayeredPane {
    /** Performs the sign-in operation and returns a message, or null on success. */
    @FunctionalInterface
    public interface LoginHandler {
        void handle(String email, char[] password, Consumer<String> done);
    }

    private final LoginHandler loginHandler;
    private final JTextField emailField = new RoundedLoginTextField();
    private final JPasswordField passwordField = new RoundedLoginPasswordField();
    private final JLabel hintLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel messageLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JButton signInButton = new RoundedButton("Sign In");
    private final JButton createAccountButton = new RoundedButton("Create Account");
    private final JButton themeButton = new RoundedButton();
    private final JButton passwordToggle = new JButton(new EyeIcon(false));
    private final RoundedCardPanel card;
    private final JPanel cardWrapper = new JPanel(new GridBagLayout());
    private final Background3D background;
    private RoundedSidePanel brandPanel;
    private RoundedSidePanel formPanel;
    private boolean busy;

    private final Consumer<Boolean> themeListener = dark -> applyTheme();

    /** Creates a sign-in panel for a login callback. */
    public LoginPanel(LoginHandler loginHandler) {
        if (loginHandler == null) throw new IllegalArgumentException("loginHandler");
        this.loginHandler = loginHandler;
        setLayout(null);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        background = new Background3D(ThemeManager.isDark(), RmsColors.PRIMARY);
        background.setOpaque(false);
        add(background, Integer.valueOf(0));

        card = new RoundedCardPanel();
        card.setLayout(new GridLayout(1, 2));
        card.setOpaque(false);
        card.setBorder(new RoundedCardBorder(RmsColors.border(), 26));
        card.setPreferredSize(new Dimension(860, 520));

        JPanel brand = buildBrandPanel();
        JPanel form = buildFormPanel();
        card.add(brand);
        card.add(form);
        cardWrapper.setOpaque(false);
        cardWrapper.setBorder(BorderFactory.createEmptyBorder(32, 32, 32, 32));
        cardWrapper.add(card, new GridBagConstraints());
        add(cardWrapper, Integer.valueOf(1));

        ThemeManager.addThemeListener(themeListener);
        applyTheme();

        signInButton.addActionListener(event -> attemptLogin());
        passwordField.addActionListener(event -> attemptLogin());
    }

    private JPanel buildBrandPanel() {
        RoundedSidePanel panel = new RoundedSidePanel(true);
        brandPanel = panel;
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(44, 42, 44, 42));
        panel.setFill(RmsColors.PRIMARY);

        JLabel mark = new JLabel("RMS", SwingConstants.CENTER);
        mark.setForeground(Color.WHITE);
        mark.setFont(new Font("SansSerif", Font.BOLD, 34));
        mark.setAlignmentX(CENTER_ALIGNMENT);
        panel.add(mark);
        panel.add(Box.createVerticalStrut(18));

        JLabel title = new JLabel("Receipt Management", SwingConstants.CENTER);
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setAlignmentX(CENTER_ALIGNMENT);
        panel.add(title);

        JLabel subtitle = new JLabel("Organize. Track. Report.", SwingConstants.CENTER);
        subtitle.setForeground(new Color(235, 242, 255));
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 14));
        subtitle.setName("brandTagline");
        subtitle.setAlignmentX(CENTER_ALIGNMENT);
        panel.add(subtitle);

        panel.add(Box.createVerticalStrut(42));
        for (String feature : new String[] {
                "Receipt records and attachments",
                "Supplier and project tracking",
                "VAT, totals and financial reports",
                "Role-based access and activity log"}) {
            JLabel item = new JLabel(feature);
            item.setForeground(Color.WHITE);
            item.setFont(new Font("SansSerif", Font.PLAIN, 13));
            item.setAlignmentX(CENTER_ALIGNMENT);
            panel.add(item);
            panel.add(Box.createVerticalStrut(12));
        }
        return panel;
    }

    private JPanel buildFormPanel() {
        RoundedSidePanel panel = new RoundedSidePanel(false);
        formPanel = panel;
        panel.setLayout(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(24, 36, 24, 36));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        themeButton.putClientProperty("JButton.buttonType", "roundRect");
        themeButton.setContentAreaFilled(true);
        themeButton.setBorderPainted(true);
        themeButton.setFocusPainted(false);
        themeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        themeButton.setPreferredSize(new Dimension(142, 46));
        themeButton.setMinimumSize(new Dimension(142, 46));
        themeButton.addActionListener(event -> ThemeManager.toggle());
        top.add(themeButton, BorderLayout.EAST);
        panel.add(top, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 0, 4, 0);

        JLabel heading = new JLabel("Sign In", SwingConstants.CENTER);
        heading.setFont(new Font("SansSerif", Font.BOLD, 26));
        heading.setName("loginHeading");
        c.gridy = 0;
        c.insets = new Insets(0, 0, 8, 0);
        form.add(heading, c);

        JLabel subtitle = new JLabel("Enter your account credentials", SwingConstants.CENTER);
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subtitle.setName("loginSubtitle");
        c.gridy = 1;
        c.insets = new Insets(0, 0, 24, 0);
        form.add(subtitle, c);

        c.gridy = 2;
        c.insets = new Insets(4, 0, 4, 0);
        JLabel emailLabel = new JLabel("Email address");
        emailLabel.setFont(emailLabel.getFont().deriveFont(Font.BOLD));
        form.add(emailLabel, c);

        c.gridy = 3;
        emailField.setPreferredSize(new Dimension(320, 52));
        emailField.setMinimumSize(new Dimension(240, 52));
        form.add(emailField, c);

        c.gridy = 4;
        c.insets = new Insets(12, 0, 4, 0);
        JLabel passwordLabel = new JLabel("Password");
        passwordLabel.setFont(passwordLabel.getFont().deriveFont(Font.BOLD));
        form.add(passwordLabel, c);

        c.gridy = 5;
        c.insets = new Insets(4, 0, 4, 0);
        JPanel passwordRow = new JPanel(new BorderLayout(6, 0));
        passwordRow.setOpaque(false);
        passwordRow.add(passwordField, BorderLayout.CENTER);
        passwordToggle.setPreferredSize(new Dimension(48, 52));
        passwordToggle.setMinimumSize(new Dimension(48, 52));
        passwordToggle.setToolTipText("Show password");
        passwordToggle.getAccessibleContext().setAccessibleName("Show password");
        passwordToggle.setContentAreaFilled(false);
        passwordToggle.setBorderPainted(false);
        passwordToggle.setBorder(BorderFactory.createEmptyBorder());
        passwordToggle.setFocusable(false);
        passwordToggle.addActionListener(event -> togglePasswordVisibility());
        passwordRow.add(passwordToggle, BorderLayout.EAST);
        form.add(passwordRow, c);

        c.gridy = 6;
        c.insets = new Insets(10, 0, 4, 0);
        hintLabel.setFont(hintLabel.getFont().deriveFont(12f));
        form.add(hintLabel, c);

        c.gridy = 7;
        c.insets = new Insets(4, 0, 8, 0);
        messageLabel.setFont(messageLabel.getFont().deriveFont(12f));
        form.add(messageLabel, c);

        c.gridy = 8;
        c.insets = new Insets(4, 0, 0, 0);
        JPanel actions = new JPanel(new GridLayout(1, 2, 10, 0));
        actions.setOpaque(false);
        styleActionButton(createAccountButton);
        createAccountButton.setToolTipText("Accounts are created by your administrator");
        createAccountButton.addActionListener(event -> JOptionPane.showMessageDialog(
                this, "Please contact your system administrator to create an account.",
                "Create Account", JOptionPane.INFORMATION_MESSAGE));
        styleActionButton(signInButton);
        signInButton.setFont(signInButton.getFont().deriveFont(Font.BOLD, 14f));
        actions.add(createAccountButton);
        actions.add(signInButton);
        form.add(actions, c);

        panel.add(form, BorderLayout.CENTER);
        return panel;
    }

    private static void styleActionButton(JButton button) {
        button.putClientProperty("JButton.buttonType", "roundRect");
        button.setPreferredSize(new Dimension(150, 48));
        button.setMinimumSize(new Dimension(120, 48));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void togglePasswordVisibility() {
        if (passwordField.getEchoChar() == '\0') {
            passwordField.setEchoChar('\u2022');
            passwordToggle.setIcon(new EyeIcon(false));
            passwordToggle.setToolTipText("Show password");
            passwordToggle.getAccessibleContext().setAccessibleName("Show password");
        } else {
            passwordField.setEchoChar((char) 0);
            passwordToggle.setIcon(new EyeIcon(true));
            passwordToggle.setToolTipText("Hide password");
            passwordToggle.getAccessibleContext().setAccessibleName("Hide password");
        }
    }

    private void attemptLogin() {
        if (busy) return;
        String email = emailField.getText().trim();
        char[] password = passwordField.getPassword();
        if (email.isEmpty() || password.length == 0) {
            showMessage("Please enter your email and password.", true);
            java.util.Arrays.fill(password, '\0');
            return;
        }

        setBusy(true);
        showMessage("Signing in...", false);
        loginHandler.handle(email, password, message -> SwingUtilities.invokeLater(() -> {
            java.util.Arrays.fill(password, '\0');
            setBusy(false);
            if (message == null || message.isBlank()) {
                background.setPaused(true);
                passwordField.setText("");
                messageLabel.setText(" ");
            } else {
                showMessage(message, true);
            }
        }));
    }

    private void setBusy(boolean value) {
        busy = value;
        signInButton.setEnabled(!value);
        createAccountButton.setEnabled(!value);
        emailField.setEnabled(!value);
        passwordField.setEnabled(!value);
        signInButton.setText(value ? "Signing In..." : "Sign In");
    }

    /** Sets optional setup or first-run guidance. */
    public void setHint(String hint) {
        hintLabel.setText(hint == null || hint.isBlank() ? " " : hint);
        hintLabel.setToolTipText(hint);
    }

    /** Shows an authentication status or error message. */
    public void showMessage(String message) {
        showMessage(message, true);
    }

    private void showMessage(String message, boolean error) {
        messageLabel.setText(message == null || message.isBlank() ? " " : message);
        messageLabel.setForeground(error ? RmsColors.DANGER
                : (ThemeManager.isDark() ? RmsColors.DARK_TEXT : RmsColors.SECONDARY_TEXT));
    }

    /** Clears transient status and restores the login controls. */
    public void reset() {
        background.setPaused(false);
        setBusy(false);
        passwordField.setText("");
        passwordField.setEchoChar('\u2022');
        passwordToggle.setIcon(new EyeIcon(false));
        passwordToggle.setToolTipText("Show password");
        passwordToggle.getAccessibleContext().setAccessibleName("Show password");
        messageLabel.setText(" ");
    }

    private void applyTheme() {
        boolean dark = ThemeManager.isDark();
        setBackground(RmsColors.background());
        card.setBorder(new RoundedCardBorder(RmsColors.border(), 26));
        if (brandPanel != null) brandPanel.setFill(RmsColors.PRIMARY);
        if (formPanel != null) formPanel.setFill(RmsColors.surface());
        background.updateTheme(dark);
        background.setAccentColor(RmsColors.PRIMARY);

        emailField.setBackground(dark ? RmsColors.DARK_SURFACE : RmsColors.LIGHT_SURFACE);
        passwordField.setBackground(dark ? RmsColors.DARK_SURFACE : RmsColors.LIGHT_SURFACE);
        emailField.setForeground(RmsColors.text());
        passwordField.setForeground(RmsColors.text());
        emailField.setBorder(fieldBorder());
        passwordField.setBorder(fieldBorder());
        emailField.setFont(emailField.getFont().deriveFont(15f));
        passwordField.setFont(passwordField.getFont().deriveFont(15f));
        themeButton.setText(dark ? "Light Theme" : "Dark Theme");
        themeButton.setToolTipText(dark ? "Switch to light theme" : "Switch to dark theme");
        themeButton.setBackground(dark ? RmsColors.DARK_SURFACE : RmsColors.PRIMARY_LIGHT);
        themeButton.setForeground(dark ? RmsColors.DARK_TEXT : RmsColors.PRIMARY);
        themeButton.setBorder(fieldBorder());
        createAccountButton.setBackground(dark ? RmsColors.DARK_SURFACE : RmsColors.LIGHT_SURFACE);
        createAccountButton.setForeground(RmsColors.PRIMARY);
        createAccountButton.setBorder(fieldBorder());
        signInButton.setBackground(RmsColors.PRIMARY);
        signInButton.setForeground(Color.WHITE);
        signInButton.setBorder(fieldBorder());
        signInButton.setFocusPainted(false);
        passwordToggle.repaint();

        for (java.awt.Component component : card.getComponents()) {
            applyTextTheme(component, dark);
        }
        revalidate();
        repaint();
    }

    private javax.swing.border.Border fieldBorder() {
        return BorderFactory.createCompoundBorder(
                new RoundedFieldBorder(RmsColors.border(), 16),
                BorderFactory.createEmptyBorder(0, 12, 0, 12));
    }

    private void applyTextTheme(java.awt.Component component, boolean dark) {
        if (component instanceof JLabel label) {
            if (!Color.WHITE.equals(label.getForeground()) && label.getName() == null
                    && label != hintLabel && label != messageLabel) {
                label.setForeground(RmsColors.text());
            }
        } else if (component instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) {
                applyTextTheme(child, dark);
            }
        }
    }

    @Override
    public void removeNotify() {
        ThemeManager.removeThemeListener(themeListener);
        background.setPaused(true);
        super.removeNotify();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        ThemeManager.addThemeListener(themeListener);
        background.setPaused(false);
        applyTheme();
    }

    @Override
    public void doLayout() {
        int width = getWidth();
        int height = getHeight();
        if (background != null) background.setBounds(0, 0, width, height);
        if (cardWrapper != null) cardWrapper.setBounds(0, 0, width, height);
        super.doLayout();
    }

    private static final class EyeIcon implements Icon {
        private final boolean visible;

        private EyeIcon(boolean visible) {
            this.visible = visible;
        }

        @Override
        public int getIconWidth() { return 20; }

        @Override
        public int getIconHeight() { return 20; }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(RmsColors.text());
            g.setStroke(new java.awt.BasicStroke(1.7f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            Path2D eye = new Path2D.Double();
            eye.moveTo(x + 2, y + 10);
            eye.quadTo(x + 10, y + 1, x + 18, y + 10);
            eye.quadTo(x + 10, y + 19, x + 2, y + 10);
            g.draw(eye);
            g.draw(new Ellipse2D.Double(x + 7, y + 7, 6, 6));
            if (visible) {
                g.drawLine(x + 3, y + 17, x + 17, y + 3);
            }
            g.dispose();
        }
    }

    private static final class RoundedFieldBorder extends AbstractBorder {
        private final Color color;
        private final int radius;

        private RoundedFieldBorder(Color color, int radius) {
            this.color = color;
            this.radius = radius;
        }

        @Override
        public Insets getBorderInsets(Component component, Insets insets) {
            insets.set(1, 1, 1, 1);
            return insets;
        }

        @Override
        public Insets getBorderInsets(Component component) {
            return new Insets(1, 1, 1, 1);
        }

        @Override
        public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color);
            g.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
            g.dispose();
        }
    }

    private static final class RoundedCardBorder extends AbstractBorder {
        private final Color color;
        private final int radius;

        private RoundedCardBorder(Color color, int radius) {
            this.color = color;
            this.radius = radius;
        }

        @Override
        public Insets getBorderInsets(Component component, Insets insets) {
            insets.set(1, 1, 1, 1);
            return insets;
        }

        @Override
        public Insets getBorderInsets(Component component) {
            return new Insets(1, 1, 1, 1);
        }

        @Override
        public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color);
            g.draw(new RoundRectangle2D.Float(x, y, width - 1, height - 1, radius, radius));
            g.dispose();
        }
    }

    private static final class RoundedSidePanel extends JPanel {
        private final boolean leftSide;
        private Color fill = RmsColors.surface();
        private final int radius = 26;

        private RoundedSidePanel(boolean leftSide) {
            this.leftSide = leftSide;
            setOpaque(false);
        }

        private void setFill(Color fill) {
            this.fill = fill;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int width = getWidth();
            int height = getHeight();
            g.setColor(fill);
            g.fill(new RoundRectangle2D.Float(0, 0, width, height, radius, radius));
            if (leftSide) {
                g.fillRect(radius / 2, 0, width - radius / 2, height);
            } else {
                g.fillRect(0, 0, width - radius / 2, height);
            }
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static class RoundedButton extends JButton {
        private RoundedButton() { }

        private RoundedButton(String text) {
            super(text);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            Shape shape = new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16);
            g.clip(shape);
            super.paintComponent(g);
            g.dispose();
        }
    }

    private static class RoundedLoginTextField extends JTextField {
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.clip(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16));
            super.paintComponent(g);
            g.dispose();
        }
    }

    private static class RoundedLoginPasswordField extends JPasswordField {
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.clip(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16));
            super.paintComponent(g);
            g.dispose();
        }
    }

    private static final class RoundedCardPanel extends JPanel {
        private final int radius = 26;

        @Override
        protected void paintChildren(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.clip(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), radius, radius));
            super.paintChildren(g);
            g.dispose();

            if (getBorder() != null) {
                Graphics2D borderGraphics = (Graphics2D) graphics.create();
                getBorder().paintBorder(this, borderGraphics, 0, 0, getWidth(), getHeight());
                borderGraphics.dispose();
            }
        }
    }
}
