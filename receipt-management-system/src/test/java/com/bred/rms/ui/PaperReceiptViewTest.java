package com.bred.rms.ui;

import com.bred.rms.models.Project;
import com.bred.rms.models.Receipt;
import com.bred.rms.models.ReceiptItem;
import com.bred.rms.models.ReceiptType;
import com.bred.rms.models.Supplier;
import com.bred.rms.ui.receipts.PaperReceiptView;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperReceiptViewTest {
    private final Supplier supplier = new Supplier(UUID.randomUUID(), "Sample Hardware Trading", "000-111-222-000",
            "12 Rizal St., Davao City", "0917 000 0001");
    private final Project project = new Project(UUID.randomUUID(), "Office Renovation", "PRJ-001", "General Fund");

    private Receipt receipt(List<ReceiptItem> items, UUID projectId) {
        return new Receipt(UUID.randomUUID(), "OR-1001", LocalDate.of(2026, 11, 20), ReceiptType.OFFICIAL_RECEIPT, supplier.id(), projectId,
                items, new BigDecimal("11.00"), new BigDecimal("28.80"), new BigDecimal("5.00"), List.of(), null,
                Instant.parse("2026-11-20T01:00:00Z"), Instant.parse("2026-11-21T02:00:00Z"));
    }

    @Test
    void plainTextContainsAllRequiredFields() {
        Receipt r = receipt(List.of(new ReceiptItem("Nails", new BigDecimal("2"), new BigDecimal("100.50")),
                new ReceiptItem("Hammer", BigDecimal.ONE, new BigDecimal("50"))), project.id());
        String text = new PaperReceiptView(r, supplier, project, "Ada Admin").plainText();
        for (String expected : List.of("SAMPLE HARDWARE TRADING", "12 Rizal St., Davao City", "TIN: 000-111-222-000", "0917 000 0001",
                "OFFICIAL RECEIPT", "OR-1001", "Nov 20, 2026", "Office Renovation", "PRJ-001", "General Fund",
                "Nails", "Hammer", "100.50", "201.00", "50.00", "Subtotal", "₱251.00", "Discount", "₱11.00", "VAT", "₱28.80",
                "Additional charges", "₱5.00", "TOTAL", "₱273.80", "Encoded by: Ada Admin")) {
            assertTrue(text.contains(expected), "missing '" + expected + "' in:\n" + text);
        }
    }

    @Test
    void longDescriptionsWrapWithinThePaperWidth() {
        Receipt r = receipt(List.of(new ReceiptItem("Galvanized iron sheet corrugated 0.4mm x 8ft heavy duty roofing material",
                BigDecimal.TEN, new BigDecimal("1250.00"))), null);
        String text = new PaperReceiptView(r, supplier, null, null).plainText();
        for (String line : text.split("\n")) assertTrue(line.length() <= PaperReceiptView.WIDTH, "line too wide (" + line.length() + "): " + line);
        assertTrue(text.contains("12,500.00"), text);
        assertTrue(text.contains("roofing material"), text);
    }

    @Test
    void missingProjectSupplierAndEncoderDoNotBreakIt() {
        Receipt r = receipt(List.of(new ReceiptItem("Nails", BigDecimal.ONE, BigDecimal.TEN)), null);
        String text = new PaperReceiptView(r, null, null, null).plainText();
        assertTrue(text.contains("(supplier removed)"));
        assertFalse(text.contains("Project:"));
        assertFalse(text.contains("Encoded by: null"));
    }

    @Test
    void panelBuildsHeadlessAndShowsTheSameText() {
        Receipt r = receipt(List.of(new ReceiptItem("Nails", BigDecimal.ONE, BigDecimal.TEN)), null);
        PaperReceiptView view = new PaperReceiptView(r, supplier, null, "Ada");
        assertEquals(view.plainText(), view.displayedText());
    }
}
