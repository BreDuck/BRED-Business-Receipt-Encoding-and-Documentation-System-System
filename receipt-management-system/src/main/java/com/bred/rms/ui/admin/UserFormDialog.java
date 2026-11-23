package com.bred.rms.ui.admin;

import com.bred.rms.config.AppContext;
import com.bred.rms.models.Role;
import com.bred.rms.models.User;
import com.bred.rms.ui.UiErrors;
import com.bred.rms.ui.theme.RmsColors;
import com.bred.rms.utils.BackgroundTask;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.Arrays;

/** Creates an account ({@code existing == null}) or edits name, role and active flag. */
final class UserFormDialog extends JDialog {
    private final JTextField name = new JTextField(24);
    private final JTextField email = new JTextField(24);
    private final JPasswordField password = new JPasswordField(24);
    private final JComboBox<Role> role = new JComboBox<>(Role.values());
    private final JCheckBox active = new JCheckBox("Active");
    private final JLabel error = new JLabel(" ");
    private final JButton save = new JButton("Save");

    UserFormDialog(Window owner, AppContext ctx, User existing, Runnable onSaved) {
        super(owner, existing == null ? "Add user" : "Edit user", ModalityType.APPLICATION_MODAL);
        boolean creating = existing == null;
        if (!creating) {
            name.setText(existing.fullName());
            email.setText(existing.email());
            email.setEditable(false);
            role.setSelectedItem(existing.role());
            active.setSelected(existing.active());
        } else {
            active.setSelected(true);
        }
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        int row = 0;
        row = addRow(form, row, "Full name", name);
        row = addRow(form, row, "Email", email);
        if (creating) row = addRow(form, row, "Password (min. 6)", password);
        row = addRow(form, row, "Role", role);
        if (!creating) addRow(form, row, "", active);
        error.setForeground(RmsColors.DANGER);
        error.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 20));
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        save.addActionListener(e -> submit(ctx, existing, onSaved));
        getRootPane().setDefaultButton(save);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttons.add(cancel);
        buttons.add(save);
        JPanel south = new JPanel(new BorderLayout());
        south.add(error, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.SOUTH);
        add(form, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
    }

    private static int addRow(JPanel form, int row, String label, java.awt.Component field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.insets = new Insets(4, 4, 4, 8);
        c.anchor = GridBagConstraints.WEST;
        form.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(field, c);
        return row + 1;
    }

    private void submit(AppContext ctx, User existing, Runnable onSaved) {
        String fullName = name.getText();
        String mail = email.getText();
        char[] pw = password.getPassword();
        Role chosen = (Role) role.getSelectedItem();
        boolean isActive = active.isSelected();
        save.setEnabled(false);
        error.setText(" ");
        BackgroundTask.run(() -> {
            try {
                if (existing == null) ctx.users.create(fullName, mail, chosen, pw);
                else ctx.users.update(existing.id(), fullName, chosen, isActive);
            } finally {
                Arrays.fill(pw, '\0');
            }
            return Boolean.TRUE;
        }, ok -> {
            onSaved.run();
            dispose();
        }, e -> {
            save.setEnabled(true);
            error.setText(UiErrors.friendly(e));
        });
    }
}
