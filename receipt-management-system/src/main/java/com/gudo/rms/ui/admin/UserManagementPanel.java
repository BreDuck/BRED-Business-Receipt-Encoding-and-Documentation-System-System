package com.gudo.rms.ui.admin;

import com.gudo.rms.config.AppContext;
import com.gudo.rms.models.User;
import com.gudo.rms.ui.RefreshablePanel;
import com.gudo.rms.ui.UiErrors;
import com.gudo.rms.ui.theme.RmsColors;
import com.gudo.rms.utils.BackgroundTask;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Administrators add accounts, change names/roles and activate or deactivate people. */
public final class UserManagementPanel extends JPanel implements RefreshablePanel {
    private final AppContext ctx;
    private final DefaultTableModel model = new DefaultTableModel(new String[]{"Name", "Email", "Role", "Status"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JLabel status = new JLabel(" ");
    private final JButton edit = new JButton("Edit");
    private final JButton toggle = new JButton("Deactivate");
    private List<User> shown = new ArrayList<>();

    /** Builds the panel and loads accounts. */
    public UserManagementPanel(AppContext ctx) {
        super(new BorderLayout(0, 10));
        this.ctx = ctx;
        JButton add = new JButton("Add user");
        add.addActionListener(e -> openForm(null));
        edit.addActionListener(e -> selected().ifPresent(this::openForm));
        toggle.addActionListener(e -> selected().ifPresent(this::toggleActive));
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.add(add);
        bar.add(edit);
        bar.add(toggle);
        add(bar, BorderLayout.NORTH);
        table.setRowHeight(28);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.getSelectionModel().addListSelectionListener(e -> updateButtons());
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) selected().ifPresent(UserManagementPanel.this::openForm);
            }
        });
        add(new JScrollPane(table), BorderLayout.CENTER);
        status.setForeground(RmsColors.SECONDARY_TEXT);
        status.setBorder(BorderFactory.createEmptyBorder(2, 2, 0, 2));
        add(status, BorderLayout.SOUTH);
        updateButtons();
        refreshData();
    }

    @Override
    public void refreshData() {
        BackgroundTask.run(ctx.users::list, this::fill, e -> status.setText(UiErrors.friendly(e)));
    }

    private void fill(List<User> users) {
        shown = new ArrayList<>(users);
        shown.sort((a, b) -> a.fullName().compareToIgnoreCase(b.fullName()));
        model.setRowCount(0);
        for (User u : shown) model.addRow(new Object[]{u.fullName(), u.email(), u.role().label(), u.active() ? "Active" : "Inactive"});
        status.setText(shown.size() + " user(s)");
        updateButtons();
    }

    private Optional<User> selected() {
        int row = table.getSelectedRow();
        return row < 0 || row >= shown.size() ? Optional.empty() : Optional.of(shown.get(row));
    }

    private void updateButtons() {
        Optional<User> u = selected();
        edit.setEnabled(u.isPresent());
        toggle.setEnabled(u.isPresent());
        toggle.setText(u.isPresent() && !u.get().active() ? "Activate" : "Deactivate");
    }

    private void openForm(User existing) {
        new UserFormDialog(SwingUtilities.getWindowAncestor(this), ctx, existing, this::refreshData).setVisible(true);
    }

    private void toggleActive(User u) {
        toggle.setEnabled(false);
        BackgroundTask.run(() -> {
            ctx.users.update(u.id(), u.fullName(), u.role(), !u.active());
            return Boolean.TRUE;
        }, ok -> refreshData(), e -> {
            status.setText(UiErrors.friendly(e));
            updateButtons();
        });
    }
}
