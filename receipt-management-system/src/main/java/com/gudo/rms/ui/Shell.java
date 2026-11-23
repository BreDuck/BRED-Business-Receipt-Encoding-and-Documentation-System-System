package com.gudo.rms.ui;

import com.gudo.rms.config.AppContext;
import com.gudo.rms.models.Role;
import com.gudo.rms.models.User;
import com.gudo.rms.security.AccessPolicy;
import com.gudo.rms.security.Module;
import com.gudo.rms.ui.components.StatusBadge;
import com.gudo.rms.ui.components.SurfacePanel;
import com.gudo.rms.ui.theme.RmsColors;
import com.gudo.rms.ui.admin.ActivityLogPanel;
import com.gudo.rms.ui.admin.UserManagementPanel;
import com.gudo.rms.ui.lookups.ProjectPanel;
import com.gudo.rms.ui.lookups.SupplierPanel;
import com.gudo.rms.ui.receipts.ReceiptDetailPanel;
import com.gudo.rms.ui.reports.ReportsPanel;
import com.gudo.rms.ui.receipts.ReceiptDirectoryPanel;
import com.gudo.rms.ui.receipts.ReceiptFormDialog;
import com.gudo.rms.ui.theme.ThemeManager;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** The signed-in frame: sidebar menu (filtered by role), header with user info, and the active screen. */
public final class Shell extends JPanel {
    private final AppContext ctx;
    private final List<Module> modules;
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final Map<Module, JComponent> built = new EnumMap<>(Module.class);
    private final Map<Module, JToggleButton> navButtons = new EnumMap<>(Module.class);
    private final JLabel title = new JLabel();
    private static final String DETAIL_CARD = "RECEIPT_DETAIL";
    private ReceiptDirectoryPanel directory;
    private ReceiptDetailPanel detail;

    public Shell(AppContext ctx, Runnable onSignOut) {
        super(new BorderLayout());
        this.ctx = ctx;
        User user = ctx.currentUser.get();
        Role role = user == null ? Role.ENCODER : user.role();
        this.modules = AccessPolicy.modulesFor(role);

        add(buildSidebar(), BorderLayout.WEST);
        JPanel right = new JPanel(new BorderLayout());
        right.add(buildHeader(user, onSignOut), BorderLayout.NORTH);
        content.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        right.add(content, BorderLayout.CENTER);
        add(right, BorderLayout.CENTER);
        show(modules.get(0));
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
        if (b != null) b.setSelected(true);
    }

    /** Creates the screen for a module. */
    private JComponent build(Module m) {
        return switch (m) {
            case RECEIPTS -> {
                directory = new ReceiptDirectoryPanel(ctx, this::openReceipt, this::addReceipt);
                yield directory;
            }
            case DASHBOARD -> new DashboardPanel(ctx, this::openReceipt);
            case REPORTS -> new ReportsPanel(ctx);
            case USERS -> new UserManagementPanel(ctx);
            case ACTIVITY_LOG -> new ActivityLogPanel(ctx);
            case SETTINGS -> new SettingsPanel(ctx);
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
        if (b != null) b.setSelected(true);
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

    private JComponent buildSidebar() {
        SurfacePanel side = new SurfacePanel(null, RmsColors::surface);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(BorderFactory.createEmptyBorder(16, 12, 16, 12));
        side.setPreferredSize(new Dimension(210, 0));
        JLabel brand = new JLabel("Receipts");
        brand.setFont(brand.getFont().deriveFont(Font.BOLD, 20f));
        brand.setForeground(RmsColors.PRIMARY);
        JLabel sub = new JLabel("Management System");
        sub.setForeground(RmsColors.SECONDARY_TEXT);
        brand.setAlignmentX(LEFT_ALIGNMENT);
        sub.setAlignmentX(LEFT_ALIGNMENT);
        side.add(brand);
        side.add(sub);
        side.add(Box.createVerticalStrut(18));
        ButtonGroup group = new ButtonGroup();
        for (Module m : modules) {
            JToggleButton b = new JToggleButton(m.title());
            b.putClientProperty("JButton.buttonType", "toolBar");
            b.setHorizontalAlignment(JToggleButton.LEFT);
            b.setAlignmentX(LEFT_ALIGNMENT);
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
            b.setFocusable(false);
            b.addActionListener(e -> show(m));
            group.add(b);
            navButtons.put(m, b);
            side.add(b);
            side.add(Box.createVerticalStrut(2));
        }
        side.add(Box.createVerticalGlue());
        return side;
    }

    private JComponent buildHeader(User user, Runnable onSignOut) {
        SurfacePanel header = new SurfacePanel(new BorderLayout(), RmsColors::surface);
        header.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        header.add(title, BorderLayout.WEST);

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        tools.setOpaque(false);
        if (user != null) {
            tools.add(new JLabel(user.fullName()));
            tools.add(StatusBadge.of(user.role().label(), user.role() == Role.ADMIN ? RmsColors.PRIMARY : RmsColors.INFO));
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
}
