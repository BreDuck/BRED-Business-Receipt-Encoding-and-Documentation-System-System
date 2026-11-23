package com.gudo.rms.models;

import java.math.BigDecimal;

/** One purchased line on a receipt. */
public record ReceiptItem(String description, BigDecimal quantity, BigDecimal unitPrice) {
    /** Returns quantity × unit price, rounded to centavos. */
    public BigDecimal subtotal() { return ReceiptMath.money(quantity.multiply(unitPrice)); }
}
