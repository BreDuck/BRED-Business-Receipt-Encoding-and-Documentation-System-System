package com.gudo.rms.ui;

import com.gudo.rms.models.Project;
import com.gudo.rms.models.Receipt;
import com.gudo.rms.models.Role;
import com.gudo.rms.models.Supplier;
import com.gudo.rms.support.Fakes;
import com.gudo.rms.ui.lookups.ProjectPanel;
import com.gudo.rms.ui.lookups.SupplierPanel;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import java.awt.Component;
import java.awt.Container;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LookupPanelsTest {
    private static List<String> buttonLabels(Container c) {
        List<String> labels = new ArrayList<>();
        for (Component child : c.getComponents()) {
            if (child instanceof AbstractButton b) labels.add(b.getText());
            if (child instanceof Container inner) labels.addAll(buttonLabels(inner));
        }
        return labels;
    }

    @Test
    void spendByProjectSumsGrandTotalsAndSkipsUnassigned() {
        Supplier s = new Fakes.Suppliers().add("S", "1");
        Project a = new Fakes.Projects().add("A", "A-1");
        Project b = new Fakes.Projects().add("B", "B-1");
        LocalDate d = LocalDate.of(2026, 11, 1);
        List<Receipt> receipts = List.of(Fakes.receipt("1", s, a, d, "x", "100.00"), Fakes.receipt("2", s, a, d, "y", "50.25"),
                Fakes.receipt("3", s, b, d, "z", "10.00"), Fakes.receipt("4", s, null, d, "w", "999.00"));
        Map<UUID, BigDecimal> spend = ProjectPanel.spendByProject(receipts);
        assertEquals(new BigDecimal("150.25"), spend.get(a.id()));
        assertEquals(new BigDecimal("10.00"), spend.get(b.id()));
        assertEquals(2, spend.size());
    }

    @Test
    void adminSeesDeleteOnBothPanelsEncoderDoesNot() {
        for (Role role : Role.values()) {
            var ctx = TestContexts.withUser(role);
            for (Container panel : List.<Container>of(new SupplierPanel(ctx), new ProjectPanel(ctx))) {
                List<String> labels = buttonLabels(panel);
                assertTrue(labels.contains("Edit"), labels.toString());
                assertEquals(role == Role.ADMIN, labels.contains("Delete"), role + " " + labels);
            }
        }
        assertFalse(buttonLabels(new SupplierPanel(TestContexts.withUser(Role.ENCODER))).contains("Delete"));
    }
}
