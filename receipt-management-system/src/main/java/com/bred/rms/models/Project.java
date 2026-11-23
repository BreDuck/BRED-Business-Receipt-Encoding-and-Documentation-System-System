package com.bred.rms.models;

import java.util.UUID;

/** A project that receipts can be charged to. */
public record Project(UUID id, String name, String code, String fundSource) { }
