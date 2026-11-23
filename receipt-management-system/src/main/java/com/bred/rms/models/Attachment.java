package com.bred.rms.models;

/** A scanned copy, photo or PDF of a receipt, kept in Supabase Storage. */
public record Attachment(String fileName, String storagePath) { }
