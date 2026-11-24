package com.bred.rms.ui;

import com.bred.rms.config.AppContext;
import com.bred.rms.models.Role;
import com.bred.rms.models.User;
import com.bred.rms.security.AccessPolicy;
import com.bred.rms.security.Module;
import com.bred.rms.ui.components.StatusBadge;
import com.bred.rms.ui.components.SurfacePanel;
import com.bred.rms.ui.theme.RmsColors;
import com.bred.rms.ui.admin.ActivityLogPanel;
import com.bred.rms.ui.admin.UserManagementPanel;
import com.bred.rms.ui.lookups.ProjectPanel;
import com.bred.rms.ui.lookups.SupplierPanel;
import com.bred.rms.ui.receipts.ReceiptDetailPanel;
import com.bred.rms.ui.reports.ReportsPanel;
import com.bred.rms.ui.receipts.ReceiptDirectoryPanel;
import com.bred.rms.ui.receipts.ReceiptFormDialog;
import com.bred.rms.ui.theme.ThemeManager;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.BasicStroke;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.io.IOException;

/** The signed-in frame: sidebar menu (filtered by role), header with user info, and the active screen. */
public final class Shell extends JPanel {
    private final AppContext ctx;
    private final List<Module> modules;
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final Map<Module, JComponent> built = new EnumMap<>(Module.class);
    private final Map<Module, JToggleButton> navButtons = new EnumMap<>(Module.class);
    private final JLabel title = new JLabel();
    private final JLabel brand = new JLabel("BRED");
    private final Consumer<Boolean> themeListener = dark -> brand.setForeground(RmsColors.primary());
    private static final String DETAIL_CARD = "RECEIPT_DETAIL";
    private NavListPanel navList;
    private ReceiptDirectoryPanel directory;
    private ReceiptDetailPanel detail;

    public Shell(AppContext ctx, Runnable onSignOut) {
        super(new BorderLayout());
        this.ctx = ctx;
        User user = ctx.currentUser.get();
        Role role = user == null ? Role.ENCODER : user.role();
        this.modules = AccessPolicy.modulesFor(role);

        add(buildSidebar(user), BorderLayout.WEST);
        JPanel right = new JPanel(new BorderLayout());
        right.add(buildHeader(user, onSignOut), BorderLayout.NORTH);
        content.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        right.add(content, BorderLayout.CENTER);
        add(right, BorderLayout.CENTER);
        show(modules.get(0));
    }

    @Override
    public void addNotify() {
        super.addNotify();
        ThemeManager.addThemeListener(themeListener);
    }

    @Override
    public void removeNotify() {
        ThemeManager.removeThemeListener(themeListener);
        super.removeNotify();
    }

    /** The menu entries this user can see, in order. */
    public List<Module> visibleModules() { return modules; }

    /** Opens a screen, building it on first use. */
    public void show(Module m) {
        if (!modules.contains(m)) return;
        boolean existed = built.containsKey(m);
        built.computeIfAbsent(m, k -> {
            JComponent c = build(k);
            content.add(c, k.name());
            return c;
        });
        if (existed && built.get(m) instanceof RefreshablePanel p) p.refreshData();
        cards.show(content, m.name());
        title.setText(m.title());
        JToggleButton b = navButtons.get(m);
        if (b != null) {
            b.setSelected(true);
            navList.select(modules.indexOf(m));
        }
    }

    /** Creates the screen for a module. */
    private JComponent build(Module m) {
        return switch (m) {
            case RECEIPTS -> {
                directory = new ReceiptDirectoryPanel(ctx, this::openReceipt, this::addReceipt);
                yield directory;
            }
            case DASHBOARD -> new DashboardPanel(ctx, this::openReceipt);
            case MESSAGES -> new MessagesPanel(ctx);
            case REPORTS -> new ReportsPanel(ctx);
            case USERS -> new UserManagementPanel(ctx);
            case ACTIVITY_LOG -> new ActivityLogPanel(ctx);
            case SETTINGS -> new JScrollPane(new SettingsPanel(ctx));
            case SUPPLIERS -> new SupplierPanel(ctx);
            case PROJECTS -> new ProjectPanel(ctx);
        };
    }

    /** Opens a receipt's paper-style detail view in place of the current screen. */
    public void openReceipt(java.util.UUID id) {
        if (detail != null) content.remove(detail);
        detail = new ReceiptDetailPanel(ctx, id, this::backToDirectory, () -> {
            if (directory != null) directory.refreshData();
        });
        content.add(detail, DETAIL_CARD);
        cards.show(content, DETAIL_CARD);
        title.setText("Receipt");
        JToggleButton b = navButtons.get(Module.RECEIPTS);
        if (b != null) {
            b.setSelected(true);
            navList.select(modules.indexOf(Module.RECEIPTS));
        }
    }

    private void backToDirectory() {
        show(Module.RECEIPTS);
        if (directory != null) directory.refreshData();
    }

    /** Opens the add-receipt form; the directory reloads after a successful save. */
    private void addReceipt() {
        new ReceiptFormDialog(SwingUtilities.getWindowAncestor(this), ctx, null, saved -> {
            if (directory != null) directory.refreshData();
            openReceipt(saved.id());
        }).setVisible(true);
    }

    private JComponent buildSidebar(User user) {
        SurfacePanel side = new SurfacePanel(null, RmsColors::surface) {
            @Override
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                Graphics2D g = (Graphics2D) graphics.create();
                g.setColor(RmsColors.border());
                g.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());
                g.dispose();
            }
        };
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(BorderFactory.createEmptyBorder(24, 18, 18, 18));
        side.setPreferredSize(new Dimension(252, 0));
        brand.setFont(brand.getFont().deriveFont(Font.BOLD, 24f));
        brand.setForeground(RmsColors.primary());
        String companyName = user == null ? "" : user.companyName();
        String companyText = companyName.isBlank()
                ? (user != null && user.role() == Role.CREATOR ? "All companies" : "No company")
                : companyName;
        JLabel sub = new JLabel("<html><b>" + escapeHtml(companyText) + "</b><br>"
                + "Business Receipt<br>Encoding and<br>Documentation System</html>");
        sub.setForeground(RmsColors.SECONDARY_TEXT);
        sub.setFont(sub.getFont().deriveFont(Font.PLAIN, 11f));
        brand.setAlignmentX(LEFT_ALIGNMENT);
        sub.setAlignmentX(LEFT_ALIGNMENT);
        side.add(brand);
        side.add(sub);
        side.add(Box.createVerticalStrut(30));

        JLabel section = new JLabel("WORKSPACE");
        section.setFont(section.getFont().deriveFont(Font.BOLD, 10f));
        section.setForeground(RmsColors.SECONDARY_TEXT);
        section.setAlignmentX(LEFT_ALIGNMENT);
        side.add(section);
        side.add(Box.createVerticalStrut(10));

        ButtonGroup group = new ButtonGroup();
        navList = new NavListPanel();
        navList.setPreferredSize(new Dimension(216, modules.size() * 56));
        navList.setMaximumSize(new Dimension(Integer.MAX_VALUE, modules.size() * 56));
        for (Module m : modules) {
            JToggleButton b = new NavButton(m.title());
            b.setHorizontalAlignment(JToggleButton.LEFT);
            b.setMargin(new Insets(0, 16, 0, 12));
            b.setFocusable(false);
            b.addActionListener(e -> show(m));
            group.add(b);
            navButtons.put(m, b);
            navList.addButton(b);
        }
        navList.setAlignmentX(LEFT_ALIGNMENT);
        side.add(navList);
        side.add(Box.createVerticalGlue());
        return side;
    }

    private static final class NavButton extends JToggleButton {
        private NavButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setOpaque(false);
            setBorderPainted(false);
            setFocusPainted(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (getModel().isRollover() && !isSelected()) {
                RoundRectangle2D shape = new RoundRectangle2D.Float(1, 1, getWidth() - 3, getHeight() - 3, 14, 14);
                g.setColor(ThemeManager.isDark()
                        ? new Color(255, 255, 255, 12)
                        : new Color(RmsColors.primaryAccent().getRed(), RmsColors.primaryAccent().getGreen(), RmsColors.primaryAccent().getBlue(), 14));
                g.fill(shape);
            }
            setForeground(isSelected() && !ThemeManager.isDark() ? RmsColors.primaryDark() : RmsColors.text());
            super.paintComponent(g);
            g.dispose();
        }
    }

    private static final class NavListPanel extends JPanel {
        private static final int ROW_HEIGHT = 50;
        private static final int ROW_GAP = 6;
        private static final int ARC = 14;
        private final List<JToggleButton> buttons = new ArrayList<>();
        private final Timer animation;
        private double y = -1;
        private double targetY = -1;

        private NavListPanel() {
            setLayout(null);
            setOpaque(false);
            animation = new Timer(16, null);
            animation.addActionListener(event -> {
                y += (targetY - y) * 0.24;
                if (Math.abs(targetY - y) < 0.6) {
                    y = targetY;
                    animation.stop();
                }
                repaint();
            });
        }

        private void addButton(JToggleButton button) {
            buttons.add(button);
            add(button);
        }

        private void select(int index) {
            if (index < 0 || index >= buttons.size()) return;
            targetY = index * (ROW_HEIGHT + ROW_GAP);
            if (y < 0) {
                y = targetY;
                repaint();
            } else if (Math.abs(targetY - y) > 0.6) {
                animation.start();
            }
        }

        @Override
        public void doLayout() {
            for (int i = 0; i < buttons.size(); i++) {
                buttons.get(i).setBounds(0, i * (ROW_HEIGHT + ROW_GAP), getWidth(), ROW_HEIGHT);
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (y < 0 || getWidth() <= 2) return;
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            RoundRectangle2D outline = new RoundRectangle2D.Float(1, (float) y + 1,
                    getWidth() - 3, ROW_HEIGHT - 3, ARC, ARC);
            g.setColor(ThemeManager.isDark()
                    ? new Color(RmsColors.primaryAccent().getRed(), RmsColors.primaryAccent().getGreen(), RmsColors.primaryAccent().getBlue(), 38)
                    : RmsColors.primaryLight());
            g.fill(outline);
            g.setColor(RmsColors.primaryAccent());
            g.setStroke(new BasicStroke(1.2f));
            g.draw(outline);
            g.dispose();
        }

        @Override
        public void removeNotify() {
            animation.stop();
            super.removeNotify();
        }
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private JComponent buildHeader(User user, Runnable onSignOut) {
        SurfacePanel header = new SurfacePanel(new BorderLayout(), RmsColors::surface);
        header.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        header.add(title, BorderLayout.WEST);

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        tools.setOpaque(false);
        boolean sharedCloud = ctx.storageLabel.startsWith("Supabase project ");
        StatusBadge storage = StatusBadge.of(sharedCloud ? "Shared cloud" : "Local only",
                sharedCloud ? RmsColors.SUCCESS : RmsColors.WARNING);
        storage.setToolTipText(sharedCloud
                ? "Receipts are stored in the shared Supabase project."
                : "Receipts are stored only on this computer.");
        tools.add(storage);
        if (user != null) {
            if (user.role() == Role.CREATOR) {
                tools.add(new ProfileAvatar());
            }
            tools.add(new JLabel(user.fullName()));
            tools.add(StatusBadge.of(user.role().label(), user.role().canManageCompany() ? RmsColors.primary() : RmsColors.INFO));
        }
        JButton theme = new JButton(ThemeManager.isDark() ? "Light mode" : "Dark mode");
        theme.addActionListener(e -> {
            ThemeManager.toggle();
            theme.setText(ThemeManager.isDark() ? "Light mode" : "Dark mode");
        });
        JButton out = new JButton("Sign out");
        out.addActionListener(e -> onSignOut.run());
        tools.add(theme);
        tools.add(out);
        header.add(tools, BorderLayout.EAST);
        return header;
    }

    private static final class ProfileAvatar extends JPanel {
        private final BufferedImage image;

        private ProfileAvatar() {
            setOpaque(false);
            setPreferredSize(new Dimension(38, 38));
            setMaximumSize(new Dimension(38, 38));
            setToolTipText("Creator profile");
            BufferedImage loaded = null;
            try {
                var resource = ProfileAvatar.class.getResource("/com/bred/rms/ui/logo/breducklogo.png");
                if (resource != null) loaded = ImageIO.read(resource);
            } catch (IOException ignored) {
                loaded = null;
            }
            image = loaded;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int diameter = 36;
            int x = (getWidth() - diameter) / 2;
            int y = (getHeight() - diameter) / 2;
            Ellipse2D clip = new Ellipse2D.Float(x, y, diameter, diameter);
            g.setClip(clip);
            if (image != null) {
                g.drawImage(image, x, y, diameter, diameter, null);
            } else {
                g.setColor(RmsColors.primaryLight());
                g.fill(clip);
                g.setColor(RmsColors.primary());
                g.setFont(getFont().deriveFont(Font.BOLD, 13f));
                String initials = "CH";
                var metrics = g.getFontMetrics();
                g.drawString(initials, x + (diameter - metrics.stringWidth(initials)) / 2,
                        y + (diameter + metrics.getAscent() - metrics.getDescent()) / 2);
            }
            g.setClip(null);
            g.setColor(RmsColors.primaryAccent());
            g.draw(clip);
            g.dispose();
        }
    }
}
