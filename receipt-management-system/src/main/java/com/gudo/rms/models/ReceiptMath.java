package com.gudo.rms.models;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Money helpers: all amounts are BigDecimal with two decimals, rounded half-up. */
public final class ReceiptMath {
    private static final BigDecimal VAT_RATE = new BigDecimal("0.12");

    private ReceiptMath() { }

    /** Scales an amount to two decimals, half-up. */
    public static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }

    /** Default VAT: 12% of (subtotal − discount). */
    public static BigDecimal defaultVat(BigDecimal subtotal, BigDecimal discount) {
        return money(subtotal.subtract(discount).multiply(VAT_RATE));
    }

    /** Formats an amount as pesos, e.g. {@code ₱1,234.50}. */
    public static String format(BigDecimal value) {
        DecimalFormat f = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return "₱" + f.format(money(value));
    }
}
