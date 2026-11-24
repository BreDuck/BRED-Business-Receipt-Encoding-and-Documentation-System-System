package com.bred.rms.ui;

import com.bred.rms.config.AppContext;
import com.bred.rms.models.AccountStatus;
import com.bred.rms.models.Company;
import com.bred.rms.models.User;
import com.bred.rms.ui.components.StyledScrollPane;
import com.bred.rms.ui.theme.RmsColors;
import com.bred.rms.utils.BackgroundTask;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Lets the Creator send a stored message to all, selected accounts, or selected companies. */
final class BroadcastDialog extends JDialog {
    private record RecipientData(List<User> accounts, List<Company> companies) { }

    private enum RecipientMode {
        ALL("All active accounts"),
        ACCOUNTS("Choose accounts"),
        COMPANIES("Choose companies");

        private final String label;
        RecipientMode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private final AppContext ctx;
    private final JComboBox<RecipientMode> mode = new JComboBox<>(
            new RecipientMode[]{RecipientMode.ALL, RecipientMode.ACCOUNTS});
    private final DefaultListModel<Object> recipients = new DefaultListModel<>();
    private final JList<Object> recipientList = new JList<>(recipients);
    private final JTextArea body = new JTextArea(5, 42);
    private final JLabel status = new JLabel("Loading recipients…");
    private final JButton send = new JButton("Send broadcast");
    private List<User> activeAccounts = List.of();
    private List<Company> companies = List.of();

    BroadcastDialog(Window owner, AppContext ctx) {
        super(owner, "Broadcast message", ModalityType.APPLICATION_MODAL);
        this.ctx = ctx;
        setLayout(new BorderLayout(10, 10));

        JPanel targets = new JPanel(new BorderLayout(6, 6));
        targets.setBorder(BorderFactory.createTitledBorder("Recipients"));
        targets.add(mode, BorderLayout.NORTH);
        recipientList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        recipientList.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean selected, boolean focus) {
                String label = value instanceof User user
                        ? user.fullName() + (user.companyName().isBlank() ? "" : " — " + user.companyName())
                        : value instanceof Company company ? company.name() : String.valueOf(value);
                return super.getListCellRendererComponent(list, label, index, selected, focus);
            }
        });
        recipientList.setVisibleRowCount(8);
        targets.add(new StyledScrollPane(recipientList), BorderLayout.CENTER);

        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        body.setBorder(BorderFactory.createTitledBorder("Message"));
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));
        content.add(targets, BorderLayout.NORTH);
        content.add(new StyledScrollPane(body), BorderLayout.CENTER);

        status.setForeground(RmsColors.SECONDARY_TEXT);
        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        footer.add(status, BorderLayout.CENTER);
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(event -> dispose());
        send.addActionListener(event -> send());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(cancel);
        buttons.add(send);
        footer.add(buttons, BorderLayout.EAST);

        add(content, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
        mode.addActionListener(event -> fillRecipients());
        setPreferredSize(new Dimension(560, 500));
        pack();
        setLocationRelativeTo(owner);
        loadRecipients();
    }

    private void loadRecipients() {
        mode.setEnabled(false);
        send.setEnabled(false);
        BackgroundTask.run(() -> {
            User actor = ctx.currentUser.get();
            List<User> users = ctx.users.list().stream()
                    .filter(User::active)
                    .filter(user -> user.accountStatus() == AccountStatus.APPROVED)
                    .filter(user -> actor == null || !user.id().equals(actor.id()))
                    .toList();
            List<Company> allCompanies = ctx.companies.isSupported() ? ctx.companies.list() : List.of();
            return new RecipientData(users, allCompanies);
        }, result -> {
            activeAccounts = result.accounts();
            companies = result.companies();
            if (!companies.isEmpty()) mode.addItem(RecipientMode.COMPANIES);
            mode.setEnabled(true);
            send.setEnabled(true);
            fillRecipients();
        }, error -> {
            status.setText(UiErrors.friendly(error));
            mode.setEnabled(true);
        });
    }

    private void fillRecipients() {
        recipients.clear();
        RecipientMode selected = (RecipientMode) mode.getSelectedItem();
        if (selected == RecipientMode.ACCOUNTS) {
            activeAccounts.forEach(recipients::addElement);
        } else if (selected == RecipientMode.COMPANIES) {
            companies.forEach(recipients::addElement);
        }
        boolean choose = selected != RecipientMode.ALL;
        recipientList.setEnabled(choose);
        status.setText(selected == RecipientMode.ALL
                ? activeAccounts.size() + " active account(s) will receive this broadcast."
                : "Select one or more " + (selected == RecipientMode.COMPANIES ? "companies." : "accounts."));
    }

    private void send() {
        List<UUID> ids = selectedRecipientIds();
        if (ids.isEmpty()) {
            status.setText("Choose at least one recipient.");
            return;
        }
        String text = body.getText();
        send.setEnabled(false);
        mode.setEnabled(false);
        status.setText("Sending…");
        BackgroundTask.run(() -> ctx.messageService.sendBroadcast(ids, text), count -> {
            status.setText("Broadcast saved and sent to " + count + " account(s).");
            dispose();
        }, error -> {
            status.setText(UiErrors.friendly(error));
            send.setEnabled(true);
            mode.setEnabled(true);
        });
    }

    private List<UUID> selectedRecipientIds() {
        RecipientMode selected = (RecipientMode) mode.getSelectedItem();
        if (selected == RecipientMode.ALL) {
            return activeAccounts.stream().map(User::id).toList();
        }

        List<Object> selectedItems = recipientList.getSelectedValuesList();
        if (selected == RecipientMode.ACCOUNTS) {
            return selectedItems.stream().filter(User.class::isInstance)
                    .map(User.class::cast).map(User::id).toList();
        }
        if (selected == RecipientMode.COMPANIES) {
            List<UUID> companyIds = selectedItems.stream().filter(Company.class::isInstance)
                    .map(Company.class::cast).map(Company::id).toList();
            return activeAccounts.stream().filter(user -> companyIds.contains(user.companyId()))
                    .map(User::id).toList();
        }
        return new ArrayList<>();
    }
}
