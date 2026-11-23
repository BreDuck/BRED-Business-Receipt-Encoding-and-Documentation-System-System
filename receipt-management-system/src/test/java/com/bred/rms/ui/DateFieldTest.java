package com.bred.rms.ui;

import com.bred.rms.ui.components.DateField;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DateFieldTest {
    @Test
    void blankIsEmptyButValid() {
        DateField f = new DateField();
        f.setText("   ");
        assertNull(f.value());
        assertTrue(f.isValidInput());
    }

    @Test
    void parsesIsoDates() {
        DateField f = new DateField();
        f.setText(" 2026-11-05 ");
        assertEquals(LocalDate.of(2026, 11, 5), f.value());
        assertTrue(f.isValidInput());
    }

    @Test
    void rejectsGarbageAndImpossibleDates() {
        DateField f = new DateField();
        f.setText("11/05/2026");
        assertNull(f.value());
        assertFalse(f.isValidInput());
        f.setText("2026-02-30");
        assertFalse(f.isValidInput());
    }

    @Test
    void setValueRoundTrips() {
        DateField f = new DateField();
        f.setValue(LocalDate.of(2026, 12, 25));
        assertEquals("2026-12-25", f.getText());
        f.setValue(null);
        assertEquals("", f.getText());
    }
}
