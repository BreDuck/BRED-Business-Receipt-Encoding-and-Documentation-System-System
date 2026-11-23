package com.bred.rms.ui;

import com.bred.rms.models.Role;
import com.bred.rms.ui.receipts.ReceiptDetailPanel;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceiptDetailPanelTest {
    private static List<String> buttonLabels(Container c) {
        List<String> labels = new ArrayList<>();
        for (Component child : c.getComponents()) {
            if (child instanceof AbstractButton b) labels.add(b.getText());
            if (child instanceof Container inner) labels.addAll(buttonLabels(inner));
        }
        return labels;
    }

    @Test
    void adminSeesEditAndDelete() {
        var panel = new ReceiptDetailPanel(TestContexts.withUser(Role.ADMIN), UUID.randomUUID(), () -> { }, () -> { });
        List<String> labels = buttonLabels(panel);
        assertTrue(labels.contains("Edit"), labels.toString());
        assertTrue(labels.contains("Delete"), labels.toString());
    }

    @Test
    void encoderSeesEditButNotDelete() {
        var panel = new ReceiptDetailPanel(TestContexts.withUser(Role.ENCODER), UUID.randomUUID(), () -> { }, () -> { });
        List<String> labels = buttonLabels(panel);
        assertTrue(labels.contains("Edit"), labels.toString());
        assertFalse(labels.contains("Delete"), labels.toString());
    }
}
