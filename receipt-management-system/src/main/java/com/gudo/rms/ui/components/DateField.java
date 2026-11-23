package com.gudo.rms.ui.components;

import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** A text box for dates written as YYYY-MM-DD. Blank means "no date". Invalid input is outlined in red. */
public final class DateField extends JTextField {
    public DateField() {
        super(10);
        setToolTipText("Use YYYY-MM-DD, for example 2026-11-05");
        putClientProperty("JTextField.placeholderText", "YYYY-MM-DD");
        getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { markValidity(); }
            @Override public void removeUpdate(DocumentEvent e) { markValidity(); }
            @Override public void changedUpdate(DocumentEvent e) { markValidity(); }
        });
    }

    private void markValidity() {
        putClientProperty("JComponent.outline", isValidInput() ? null : "error");
        repaint();
    }

    /** The entered date, or null when blank or invalid. */
    public LocalDate value() {
        String t = getText().trim();
        if (t.isEmpty()) return null;
        try {
            return LocalDate.parse(t);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** True when blank or a real date. */
    public boolean isValidInput() { return getText().isBlank() || value() != null; }

    public void setValue(LocalDate d) { setText(d == null ? "" : d.toString()); }
}
