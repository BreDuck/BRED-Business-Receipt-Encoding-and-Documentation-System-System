package com.bred.rms.ui.lookups;

import com.bred.rms.ui.UiErrors;
import com.bred.rms.ui.theme.RmsColors;
import com.bred.rms.utils.BackgroundTask;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** A small modal form of labelled text fields. The save action runs off the UI thread; errors stay in the dialog. */
final class SimpleFormDialog extends JDialog {
    /** Saves the trimmed field values; throws to report a problem. */
    interface Saver { void save(List<String> values) throws Exception; }

    private final List<JTextField> fields = new ArrayList<>();
    private final JLabel error = new JLabel(" ");
    private final JButton save = new JButton("Save");

    SimpleFormDialog(Window owner, String title, List<String> labels, List<String> initial, Saver saver, Runnable onSaved) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        for (int i = 0; i < labels.size(); i++) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = i;
            c.insets = new Insets(4, 4, 4, 8);
            c.anchor = GridBagConstraints.WEST;
            form.add(new JLabel(labels.get(i)), c);
            JTextField f = new JTextField(initial.get(i), 26);
            fields.add(f);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            form.add(f, c);
        }
        error.setForeground(RmsColors.DANGER);
        error.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 20));
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        save.addActionListener(e -> submit(saver, onSaved));
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

    private void submit(Saver saver, Runnable onSaved) {
        List<String> values = new ArrayList<>();
        for (JTextField f : fields) values.add(f.getText().trim());
        save.setEnabled(false);
        error.setText(" ");
        BackgroundTask.run(() -> {
            saver.save(values);
            return Boolean.TRUE;
        }, (Boolean ok) -> {
            onSaved.run();
            dispose();
        }, (Throwable e) -> {
            save.setEnabled(true);
            error.setText(UiErrors.friendly(e));
        });
    }
}
