package com.bred.rms.ui;

import com.bred.rms.models.ValidationException;
import com.bred.rms.ui.receipts.FormParsing;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FormParsingTest {
    @Test
    void blankAmountIsZero() {
        assertEquals(new BigDecimal("0.00"), FormParsing.amount("Discount", "  "));
        assertEquals(new BigDecimal("0.00"), FormParsing.amount("Discount", null));
    }

    @Test
    void parsesCommasAndPesoSignAndRoundsToCentavos() {
        assertEquals(new BigDecimal("1234.50"), FormParsing.amount("VAT", "₱1,234.5"));
        assertEquals(new BigDecimal("0.13"), FormParsing.amount("VAT", "0.125"));
    }

    @Test
    void garbageGivesNamedMessage() {
        ValidationException e = assertThrows(ValidationException.class, () -> FormParsing.amount("Discount", "abc"));
        assertEquals("Discount must be a number.", e.getMessage());
        assertThrows(ValidationException.class, () -> FormParsing.amount("VAT", "1e999999999"));
    }

    @Test
    void decimalHelperKeepsQuantityPrecision() {
        assertEquals(new BigDecimal("2.5"), FormParsing.number("Quantity", "2.5"));
        assertThrows(ValidationException.class, () -> FormParsing.number("Quantity", ""));
    }
}
