package com.gudo.rms.repositories.supabase;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Small readers for PostgREST rows. */
final class Json {
    private Json() { }

    static String text(JsonNode n, String field) { return n.hasNonNull(field) ? n.get(field).asText() : ""; }

    static UUID uuid(JsonNode n, String field) { return n.hasNonNull(field) ? UUID.fromString(n.get(field).asText()) : null; }

    static BigDecimal decimal(JsonNode n, String field) { return n.hasNonNull(field) ? n.get(field).decimalValue() : BigDecimal.ZERO; }

    static LocalDate date(JsonNode n, String field) { return n.hasNonNull(field) ? LocalDate.parse(n.get(field).asText()) : null; }

    static Instant instant(JsonNode n, String field) {
        return n.hasNonNull(field) ? OffsetDateTime.parse(n.get(field).asText()).toInstant() : null;
    }
}
