package com.bred.rms.ui.receipts;

import com.bred.rms.models.ReceiptMath;
import com.bred.rms.models.ValidationException;

import java.math.BigDecimal;

/** Turns what people type into numbers, with messages that name the field. */
public final class FormParsing {
    private static final BigDecimal LIMIT = new BigDecimal("999999999999");

    private FormParsing() { }

    /** Parses a required number; accepts thousands commas and a leading peso sign. */
    public static BigDecimal number(String label, String text) {
        if (text == null || text.isBlank()) throw new ValidationException(label + " is required.");
        String t = text.trim().replace("₱", "").replace(",", "").trim();
        BigDecimal v;
        try {
            v = new BigDecimal(t);
        } catch (NumberFormatException e) {
            throw new ValidationException(label + " must be a number.");
        }
        if (v.abs().compareTo(LIMIT) > 0) throw new ValidationException(label + " is too large.");
        return v;
    }

    /** Parses an amount of money (blank means zero) rounded to centavos. */
    public static BigDecimal amount(String label, String text) {
        if (text == null || text.isBlank()) return ReceiptMath.money(BigDecimal.ZERO);
        return ReceiptMath.money(number(label, text));
    }
}
