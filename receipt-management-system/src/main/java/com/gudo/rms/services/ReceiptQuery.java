package com.gudo.rms.services;

import java.time.LocalDate;
import java.util.UUID;

/** Search/filter for the receipt directory. Every field is optional. {@code text} matches receipt no., TIN, supplier, project and item descriptions. */
public record ReceiptQuery(String text, LocalDate from, LocalDate to, UUID supplierId, UUID projectId) {
    /** A query that matches everything. */
    public static ReceiptQuery all() { return new ReceiptQuery(null, null, null, null, null); }
}
