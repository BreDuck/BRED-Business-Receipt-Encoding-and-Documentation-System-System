package com.gudo.rms.ui;

import com.gudo.rms.models.ReceiptItem;
import com.gudo.rms.ui.receipts.LineItemsTableModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineItemsTableModelTest {
    @Test
    void subtotalUpdatesWhenQuantityChanges() {
        LineItemsTableModel m = new LineItemsTableModel(List.of(new ReceiptItem("Nails", new BigDecimal("2"), new BigDecimal("100.50"))));
        assertEquals(new BigDecimal("201.00"), m.subtotal());
        int[] events = {0};
        m.addTableModelListener(e -> events[0]++);
        m.setValueAt("3", 0, LineItemsTableModel.QTY);
        assertEquals(new BigDecimal("301.50"), m.subtotal());
        assertEquals("301.50", m.getValueAt(0, LineItemsTableModel.SUBTOTAL));
        assertTrue(events[0] > 0, "listeners are told about the change");
    }

    @Test
    void blankDescriptionRowsAreIgnoredByItems() {
        LineItemsTableModel m = new LineItemsTableModel(List.of());
        m.addRow();
        assertEquals(0, m.items().size());
        m.addRow();
        m.setValueAt("Hammer", 1, LineItemsTableModel.DESCRIPTION);
        assertEquals(1, m.items().size());
        assertEquals("Hammer", m.items().get(0).description());
    }

    @Test
    void rowWithPriceButNoDescriptionIsKeptSoValidationCanComplain() {
        LineItemsTableModel m = new LineItemsTableModel(List.of());
        m.addRow();
        m.setValueAt("25", 0, LineItemsTableModel.PRICE);
        assertEquals(1, m.items().size());
    }

    @Test
    void nonNumericQuantityIsRejectedAndKeepsOldValue() {
        LineItemsTableModel m = new LineItemsTableModel(List.of(new ReceiptItem("Nails", new BigDecimal("2"), new BigDecimal("10"))));
        m.setValueAt("abc", 0, LineItemsTableModel.QTY);
        assertTrue(m.lastEditRejected());
        assertEquals(new BigDecimal("20.00"), m.subtotal());
        m.setValueAt("4", 0, LineItemsTableModel.QTY);
        assertFalse(m.lastEditRejected());
        assertEquals(new BigDecimal("40.00"), m.subtotal());
    }

    @Test
    void thousandsSeparatorsAreAccepted() {
        LineItemsTableModel m = new LineItemsTableModel(List.of());
        m.addRow();
        m.setValueAt("Generator", 0, LineItemsTableModel.DESCRIPTION);
        m.setValueAt("1,250.50", 0, LineItemsTableModel.PRICE);
        assertEquals(new BigDecimal("1250.50"), m.subtotal());
    }

    @Test
    void removeRowDropsItAndUpdatesTotals() {
        LineItemsTableModel m = new LineItemsTableModel(List.of(new ReceiptItem("A", BigDecimal.ONE, BigDecimal.TEN),
                new ReceiptItem("B", BigDecimal.ONE, new BigDecimal("5"))));
        m.removeRow(0);
        assertEquals(1, m.getRowCount());
        assertEquals(new BigDecimal("5.00"), m.subtotal());
    }

    @Test
    void rejectedEditReportsTheReasonToTheListenerAndClearsOnNextGoodEdit() {
        LineItemsTableModel m = new LineItemsTableModel(List.of(new ReceiptItem("Nails", new BigDecimal("2"), new BigDecimal("10"))));
        List<String> messages = new java.util.ArrayList<>();
        m.setEditMessageListener(messages::add);
        m.setValueAt("10 pcs", 0, LineItemsTableModel.QTY);
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).contains("Quantity"), messages.get(0));
        m.setValueAt("4", 0, LineItemsTableModel.QTY);
        assertEquals(2, messages.size());
        assertEquals("", messages.get(1));
    }
}
