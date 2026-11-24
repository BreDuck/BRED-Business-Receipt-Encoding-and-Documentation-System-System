package com.bred.rms.ui.auth;

import com.bred.rms.models.Company;
import com.bred.rms.models.Role;
import com.bred.rms.ui.theme.RmsColors;
import com.bred.rms.utils.BackgroundTask;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.AbstractBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.geom.RoundRectangle2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Public registration form for joining a company or creating one as its administrator. */
final class RegistrationPanel extends JPanel {
    private static final String ENCODER_SUCCESS =
            "Your registration has been submitted successfully. Please wait for an administrator to approve your account.";
    private static final String ADMIN_SUCCESS =
            "Your company and administrator account are ready. You can now sign in.";

    private final LoginPanel.RegistrationHandler handler;
    private final Supplier<List<Company>> companyLoader;
    private final Runnable close;
    private final JTextField fullName = new RoundedTextField(28);
    private final JTextField email = new RoundedTextField(28);
    private final JTextField company = new RoundedTextField(28);
    private final JPasswordField password = new RoundedPasswordField(28);
    private final JPasswordField confirmPassword = new RoundedPasswordField(28);
    private final JLabel message = new JLabel(" ");
    private final JLabel companyLabel = new JLabel("Company to join");
    private final JLabel companyHint = new JLabel("Loading available companies…");
    private final JRadioButton encoderOption = new JRadioButton("Encoder");
    private final JRadioButton adminOption = new JRadioButton("Admin");
    private final JButton submit = new RoundedButton("Submit application");
    private final JPopupMenu suggestionsPopup = new JPopupMenu();
    private final JList<Company> suggestions = new JList<>();
    private List<Company> companies = List.of();
    private Company selectedCompany;
    private boolean loadingCompanies = true;
    private boolean acceptingSuggestion;
    private Role registrationRole = Role.ENCODER;

    static void showDialog(Window owner, LoginPanel.RegistrationHandler handler, Supplier<List<Company>> companyLoader) {
        JDialog dialog = new JDialog(owner, "Create Account", Dialog.ModalityType.APPLICATION_MODAL);
        RegistrationPanel panel = new RegistrationPanel(handler, companyLoader, dialog::dispose);
        dialog.getRootPane().setDefaultButton(panel.submit);
        dialog.setContentPane(panel);
        dialog.setMinimumSize(new Dimension(560, 680));
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(panel::loadCompanies);
        dialog.setVisible(true);
    }

    private RegistrationPanel(LoginPanel.RegistrationHandler handler, Supplier<List<Company>> companyLoader,
                              Runnable close) {
        super(new BorderLayout());
        this.handler = handler;
        this.companyLoader = companyLoader;
        this.close = close;
        setBackground(RmsColors.background());
        setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        setOpaque(false);

        JPanel card = new RoundedCardPanel();
        card.setLayout(new BorderLayout(0, 0));
        card.setBorder(BorderFactory.createEmptyBorder(24, 28, 20, 28));

        JPanel heading = new JPanel(new BorderLayout(0, 6));
        heading.setOpaque(false);
        JLabel eyebrow = new JLabel("BRED ACCOUNT");
        eyebrow.setForeground(RmsColors.primary());
        eyebrow.setFont(eyebrow.getFont().deriveFont(Font.BOLD, 11f));
        JLabel title = new JLabel("Apply to join");
        title.setForeground(RmsColors.text());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 25f));
        JLabel subtitle = new JLabel("Choose your company and submit a registration request.");
        subtitle.setForeground(RmsColors.SECONDARY_TEXT);
        subtitle.setFont(subtitle.getFont().deriveFont(13f));
        JPanel titleStack = new JPanel(new BorderLayout(0, 4));
        titleStack.setOpaque(false);
        titleStack.add(title, BorderLayout.NORTH);
        titleStack.add(subtitle, BorderLayout.CENTER);
        heading.add(eyebrow, BorderLayout.NORTH);
        heading.add(titleStack, BorderLayout.CENTER);
        heading.setBorder(BorderFactory.createEmptyBorder(0, 0, 18, 0));

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        ButtonGroup registrationTypes = new ButtonGroup();
        registrationTypes.add(encoderOption);
        registrationTypes.add(adminOption);
        encoderOption.setSelected(true);
        JPanel typeSelector = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        typeSelector.setOpaque(false);
        typeSelector.add(new JLabel("Register as:"));
        typeSelector.add(encoderOption);
        typeSelector.add(adminOption);
        GridBagConstraints typeRow = constraints(0, 0);
        typeRow.gridwidth = 2;
        fields.add(typeSelector, typeRow);
        encoderOption.addActionListener(e -> setRegistrationRole(Role.ENCODER));
        adminOption.addActionListener(e -> setRegistrationRole(Role.ADMIN));

        int row = 1;
        row = addField(fields, row, "Full name", fullName, "Your first and last name");
        row = addField(fields, row, "Email address", email, "Use an email address you can access");
        row = addField(fields, row, companyLabel, company, "Start typing a company name");
        companyHint.setForeground(RmsColors.SECONDARY_TEXT);
        companyHint.setFont(companyHint.getFont().deriveFont(11f));
        GridBagConstraints hint = constraints(row++, 0);
        hint.gridwidth = 2;
        hint.insets = new Insets(-2, 0, 9, 0);
        fields.add(companyHint, hint);
        row = addField(fields, row, "Password", password, "At least 8 characters, including a letter and a number");
        addField(fields, row, "Confirm password", confirmPassword, null);
        styleField(fullName);
        styleField(email);
        styleField(company);
        styleField(password);
        styleField(confirmPassword);
        configureSuggestions();

        message.setForeground(RmsColors.DANGER);
        message.setFont(message.getFont().deriveFont(12f));
        message.setBorder(BorderFactory.createEmptyBorder(4, 0, 6, 0));
        JButton cancel = new RoundedButton("Cancel");
        cancel.addActionListener(e -> close.run());
        submit.setFont(submit.getFont().deriveFont(Font.BOLD, 13f));
        submit.setBackground(RmsColors.primaryAccent());
        submit.setForeground(RmsColors.primaryForeground());
        submit.setFocusPainted(false);
        submit.setPreferredSize(new Dimension(190, 42));
        submit.addActionListener(e -> submit());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(cancel);
        actions.add(submit);
        JPanel footer = new JPanel(new BorderLayout(0, 10));
        footer.setOpaque(false);
        JLabel approvalNote = new JLabel("<html>Encoder accounts need approval. Admin accounts create a company and are ready immediately.</html>");
        approvalNote.setForeground(RmsColors.SECONDARY_TEXT);
        approvalNote.setFont(approvalNote.getFont().deriveFont(11f));
        footer.add(approvalNote, BorderLayout.NORTH);
        footer.add(message, BorderLayout.CENTER);
        footer.add(actions, BorderLayout.SOUTH);

        card.add(heading, BorderLayout.NORTH);
        card.add(fields, BorderLayout.CENTER);
        card.add(footer, BorderLayout.SOUTH);
        add(card, BorderLayout.CENTER);
    }

    private int addField(JPanel fields, int row, String labelText, java.awt.Component field, String helperText) {
        return addField(fields, row, new JLabel(labelText), field, helperText);
    }

    private int addField(JPanel fields, int row, JLabel label, java.awt.Component field, String helperText) {
        label.setForeground(RmsColors.text());
        label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
        GridBagConstraints labelConstraints = constraints(row++, 0);
        fields.add(label, labelConstraints);

        GridBagConstraints input = constraints(row++, 0);
        input.gridwidth = 2;
        input.fill = GridBagConstraints.HORIZONTAL;
        fields.add(field, input);
        if (helperText != null) {
            JLabel helper = new JLabel(helperText);
            helper.setForeground(RmsColors.SECONDARY_TEXT);
            helper.setFont(helper.getFont().deriveFont(11f));
            GridBagConstraints helperConstraints = constraints(row++, 0);
            helperConstraints.gridwidth = 2;
            helperConstraints.insets = new Insets(-2, 0, 9, 0);
            fields.add(helper, helperConstraints);
        }
        return row;
    }

    private static GridBagConstraints constraints(int row, int column) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.gridx = column;
        c.anchor = GridBagConstraints.WEST;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(3, 0, 5, 0);
        return c;
    }

    private static void styleField(JTextField field) {
        field.setFont(field.getFont().deriveFont(14f));
        field.setBackground(RmsColors.surface());
        field.setForeground(RmsColors.text());
        field.setCaretColor(RmsColors.primary());
        field.setBorder(BorderFactory.createCompoundBorder(
                new RoundedFieldBorder(RmsColors.border(), 14),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        field.setPreferredSize(new Dimension(360, 42));
    }

    private void configureSuggestions() {
        suggestions.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        suggestions.setVisibleRowCount(6);
        suggestions.setCellRenderer((list, value, index, selected, focused) -> {
            JLabel item = new JLabel(value.name());
            item.setOpaque(true);
            item.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            item.setBackground(selected ? RmsColors.primaryLight() : RmsColors.surface());
            item.setForeground(RmsColors.text());
            return item;
        });
        JScrollPane list = new JScrollPane(suggestions);
        list.setPreferredSize(new Dimension(340, 150));
        list.setBorder(new RoundedFieldBorder(RmsColors.border(), 12));
        list.getViewport().setOpaque(true);
        list.getViewport().setBackground(RmsColors.surface());
        suggestionsPopup.setBorder(new RoundedFieldBorder(RmsColors.border(), 12));
        suggestionsPopup.add(list);
        suggestions.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) acceptSuggestion();
            }
        });
        company.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { changed(); }
            @Override public void removeUpdate(DocumentEvent e) { changed(); }
            @Override public void changedUpdate(DocumentEvent e) { changed(); }
            private void changed() {
                if (acceptingSuggestion) return;
                selectedCompany = null;
                SwingUtilities.invokeLater(RegistrationPanel.this::updateSuggestions);
            }
        });
        company.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (registrationRole == Role.ADMIN) return;
                if (e.getKeyCode() == KeyEvent.VK_TAB) {
                    if (suggestionsPopup.isVisible()) acceptSuggestion();
                    else company.transferFocus();
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER && suggestionsPopup.isVisible()) {
                    acceptSuggestion();
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN && !suggestionsPopup.isVisible()) {
                    updateSuggestions();
                    if (suggestionsPopup.isVisible()) e.consume();
                }
            }
        });
        company.setFocusTraversalKeysEnabled(false);
    }

    private void loadCompanies() {
        BackgroundTask.run(companyLoader::get, available -> {
            companies = available.stream()
                    .filter(c -> c.id() != null && c.name() != null && !c.name().isBlank())
                    .toList();
            loadingCompanies = false;
            company.setEnabled(registrationRole == Role.ADMIN || !companies.isEmpty());
            submit.setEnabled(registrationRole == Role.ADMIN || !companies.isEmpty());
            companyHint.setText(companies.isEmpty()
                    ? "No companies are currently accepting applications."
                    : companies.size() + " company option(s) · type a name, then press Tab to complete");
            if (companies.isEmpty() && registrationRole == Role.ENCODER) showError("There are no companies available for registration.");
        }, error -> {
            loadingCompanies = false;
            company.setEnabled(registrationRole == Role.ADMIN);
            submit.setEnabled(registrationRole == Role.ADMIN);
            companyHint.setText("Could not load available companies.");
            if (registrationRole == Role.ENCODER) {
                showError(error.getMessage() == null ? "Could not load available companies." : error.getMessage());
            }
        });
    }

    private void setRegistrationRole(Role role) {
        registrationRole = role;
        selectedCompany = null;
        suggestionsPopup.setVisible(false);
        company.setText("");
        companyLabel.setText(role == Role.ADMIN ? "Company to create" : "Company to join");
        submit.setText(role == Role.ADMIN ? "Create company account" : "Submit application");
        companyHint.setText(role == Role.ADMIN
                ? "Enter a new company name. This account will become its administrator."
                : loadingCompanies ? "Loading available companies…" : companies.size()
                    + " company option(s) · type a name, then press Tab to complete");
        company.setEnabled(role == Role.ADMIN || !loadingCompanies && !companies.isEmpty());
        submit.setEnabled(role == Role.ADMIN || !loadingCompanies && !companies.isEmpty());
        company.setEditable(true);
        company.setToolTipText(role == Role.ADMIN
                ? "Enter the name of the company you want to create"
                : "Choose a company from the suggestions");
        message.setText(" ");
    }

    private void updateSuggestions() {
        if (registrationRole != Role.ENCODER || loadingCompanies || !company.isEnabled()) return;
        String query = company.getText().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            suggestionsPopup.setVisible(false);
            return;
        }
        List<Company> matches = companies.stream()
                .filter(c -> c.name().toLowerCase(Locale.ROOT).startsWith(query))
                .limit(8)
                .toList();
        suggestions.setListData(matches.toArray(Company[]::new));
        if (matches.isEmpty() || !company.isShowing()) {
            suggestionsPopup.setVisible(false);
            return;
        }
        suggestions.setSelectedIndex(0);
        suggestionsPopup.show(company, 0, company.getHeight());
    }

    private void acceptSuggestion() {
        Company chosen = suggestions.getSelectedValue();
        if (chosen == null && suggestions.getModel().getSize() > 0) chosen = suggestions.getModel().getElementAt(0);
        if (chosen == null) return;
        acceptingSuggestion = true;
        company.setText(chosen.name());
        acceptingSuggestion = false;
        selectedCompany = chosen;
        suggestionsPopup.setVisible(false);
        message.setText(" ");
    }

    private void submit() {
        String name = fullName.getText().trim();
        String mail = email.getText().trim();
        char[] value = password.getPassword();
        char[] confirmation = confirmPassword.getPassword();
        if (name.isBlank() || mail.isBlank() || company.getText().isBlank()
                || value.length == 0 || confirmation.length == 0) {
            showError("Complete all fields.");
            Arrays.fill(value, '\0');
            Arrays.fill(confirmation, '\0');
            return;
        }
        if (registrationRole == Role.ENCODER
                && (selectedCompany == null || !selectedCompany.name().equalsIgnoreCase(company.getText().trim()))) {
            showError("Choose a company from the suggestions. Type its name and press Tab.");
            Arrays.fill(value, '\0');
            Arrays.fill(confirmation, '\0');
            return;
        }
        if (!Arrays.equals(value, confirmation)) {
            showError("The passwords do not match.");
            Arrays.fill(value, '\0');
            Arrays.fill(confirmation, '\0');
            return;
        }

        submit.setEnabled(false);
        message.setForeground(RmsColors.SECONDARY_TEXT);
        message.setText("Submitting your application…");
        Company applicationCompany = selectedCompany;
        Role applicationRole = registrationRole;
        String companyName = applicationRole == Role.ADMIN ? company.getText().trim() : null;
        Consumer<String> done = error -> {
            Arrays.fill(value, '\0');
            Arrays.fill(confirmation, '\0');
            submit.setEnabled(registrationRole == Role.ADMIN || !companies.isEmpty());
            if (error != null && !error.isBlank()) {
                showError(error);
                return;
            }
            String success = applicationRole == Role.ADMIN ? ADMIN_SUCCESS : ENCODER_SUCCESS;
            JOptionPane.showMessageDialog(this, success, "Registration complete", JOptionPane.INFORMATION_MESSAGE);
            close.run();
        };
        try {
            handler.handle(name, mail, applicationRole, applicationCompany == null ? null : applicationCompany.id(),
                    companyName, value, done);
        } catch (RuntimeException e) {
            done.accept(e.getMessage() == null ? "Registration could not be submitted." : e.getMessage());
        }
    }

    private void showError(String text) {
        message.setForeground(RmsColors.DANGER);
        message.setText(text);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(getBackground());
        g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 18, 18));
        g.dispose();
        super.paintComponent(graphics);
    }

    private static final class RoundedCardPanel extends JPanel {
        private static final int RADIUS = 22;

        private RoundedCardPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int width = getWidth();
            int height = getHeight();
            g.setColor(RmsColors.surface());
            g.fill(new RoundRectangle2D.Float(0, 0, width - 1, height - 1, RADIUS, RADIUS));
            g.setColor(RmsColors.border());
            g.setStroke(new BasicStroke(1f));
            g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, width - 1.5f, height - 1.5f, RADIUS, RADIUS));
            g.dispose();
            super.paintComponent(graphics);
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
        public Insets getBorderInsets(java.awt.Component component) {
            return new Insets(0, 0, 0, 0);
        }

        @Override
        public void paintBorder(java.awt.Component component, Graphics graphics, int x, int y,
                                int width, int height) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color);
            g.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f,
                    width - 1, height - 1, radius, radius));
            g.dispose();
        }
    }

    private static class RoundedButton extends JButton {
        private RoundedButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Shape shape = new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16);
            g.setColor(getBackground());
            g.fill(shape);
            g.clip(shape);
            super.paintComponent(g);
            g.dispose();
        }
    }

    private static class RoundedTextField extends JTextField {
        private RoundedTextField(int columns) {
            super(columns);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(getBackground());
            g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 14, 14));
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static class RoundedPasswordField extends JPasswordField {
        private RoundedPasswordField(int columns) {
            super(columns);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(getBackground());
            g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 14, 14));
            g.dispose();
            super.paintComponent(graphics);
        }
    }
}
