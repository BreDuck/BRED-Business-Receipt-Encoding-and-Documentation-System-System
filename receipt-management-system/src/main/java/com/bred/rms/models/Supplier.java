package com.bred.rms.models;

import java.util.UUID;

/** A business that issued receipts. */
public record Supplier(UUID id, String businessName, String tin, String address, String contactNumber) { }
