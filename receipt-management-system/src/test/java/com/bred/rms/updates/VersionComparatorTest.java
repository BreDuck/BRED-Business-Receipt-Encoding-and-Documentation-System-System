package com.bred.rms.updates;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionComparatorTest {
    @Test
    void comparesNumericVersionParts() {
        assertTrue(VersionComparator.compare("1.0.0", "1.0.1") < 0);
        assertTrue(VersionComparator.compare("1.0.9", "1.0.10") < 0);
        assertTrue(VersionComparator.compare("1.1.0", "1.0.9") > 0);
        assertTrue(VersionComparator.compare("2.0.0", "1.9.9") > 0);
    }

    @Test
    void acceptsOptionalPrefixAndAppliesSemanticPrereleaseRules() {
        assertEquals(0, VersionComparator.compare("v1.2.3", "1.2.3"));
        assertTrue(VersionComparator.compare("1.2.3-rc.2", "1.2.3-rc.10") < 0);
        assertTrue(VersionComparator.compare("1.2.3-rc.1", "1.2.3") < 0);
        assertEquals(0, VersionComparator.compare("1.2.3+build.2", "1.2.3+build.10"));
    }

    @Test
    void malformedVersionsNeverBecomeUpdates() {
        assertFalse(VersionComparator.isNewer(null, "1.0.0"));
        assertFalse(VersionComparator.isNewer("not-a-version", "1.0.0"));
        assertFalse(VersionComparator.isNewer("2.0.0", ""));
        assertThrows(IllegalArgumentException.class, () -> VersionComparator.compare("1.0", "1.0.0"));
        assertTrue(VersionComparator.normalize("v1.2.3").orElseThrow().equals("1.2.3"));
    }
}
