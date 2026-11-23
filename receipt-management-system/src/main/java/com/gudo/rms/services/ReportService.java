package com.gudo.rms.services;

import com.gudo.rms.models.Project;
import com.gudo.rms.models.Receipt;
import com.gudo.rms.models.ReceiptMath;
import com.gudo.rms.models.Supplier;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** Expense totals and CSV export. Totals use each receipt's grand total. */
public final class ReportService {
    /** One report row: a label, how many receipts it covers and their combined grand total. */
    public record Total(String label, int receiptCount, BigDecimal amount) { }

    /** Headline numbers for the dashboard. */
    public record Summary(int monthCount, BigDecimal monthSpend, int totalCount, int projectsWithSpend) { }

    private static final String[] HEADER = {"Receipt No.", "Date", "Type", "Supplier", "TIN", "Project", "Subtotal", "Discount",
            "VAT", "Additional Charges", "Grand Total"};

    private final ReceiptService receipts;
    private final SupplierService suppliers;
    private final ProjectService projects;

    /** Creates the service over the existing services (their sign-in checks apply). */
    public ReportService(ReceiptService receipts, SupplierService suppliers, ProjectService projects) {
        this.receipts = receipts;
        this.suppliers = suppliers;
        this.projects = projects;
    }

    /** Returns receipts dated within [from, to]; either bound may be null. Newest first. */
    public List<Receipt> receipts(LocalDate from, LocalDate to) {
        return receipts.search(new ReceiptQuery(null, from, to, null, null));
    }

    /** Totals per project, biggest first; receipts without a project are grouped as "Unassigned". */
    public List<Total> totalsByProject(LocalDate from, LocalDate to) {
        Map<UUID, String> names = new HashMap<>();
        for (Project p : projects.findAll()) names.put(p.id(), p.name());
        return group(receipts(from, to), r -> r.projectId() == null ? "Unassigned" : names.getOrDefault(r.projectId(), "(removed project)"), true);
    }

    /** Totals per supplier, biggest first. */
    public List<Total> totalsBySupplier(LocalDate from, LocalDate to) {
        Map<UUID, String> names = new HashMap<>();
        for (Supplier s : suppliers.findAll()) names.put(s.id(), s.businessName());
        return group(receipts(from, to), r -> names.getOrDefault(r.supplierId(), "(removed supplier)"), true);
    }

    /** Totals per calendar month (label yyyy-MM), oldest first. */
    public List<Total> totalsByMonth(LocalDate from, LocalDate to) {
        return group(receipts(from, to), r -> String.format("%04d-%02d", r.date().getYear(), r.date().getMonthValue()), false);
    }

    /** Computes the dashboard headline numbers for the given month over all receipts. */
    public static Summary summarize(List<Receipt> all, java.time.YearMonth month) {
        int count = 0;
        BigDecimal spend = BigDecimal.ZERO;
        java.util.Set<UUID> projectsUsed = new java.util.HashSet<>();
        for (Receipt r : all) {
            if (java.time.YearMonth.from(r.date()).equals(month)) {
                count++;
                spend = spend.add(r.grandTotal());
            }
            if (r.projectId() != null) projectsUsed.add(r.projectId());
        }
        return new Summary(count, ReceiptMath.money(spend), all.size(), projectsUsed.size());
    }

    private static List<Total> group(List<Receipt> list, Function<Receipt, String> key, boolean biggestFirst) {
        Map<String, BigDecimal> sums = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Receipt r : list) {
            String k = key.apply(r);
            sums.merge(k, r.grandTotal(), BigDecimal::add);
            counts.merge(k, 1, Integer::sum);
        }
        List<Total> out = new ArrayList<>();
        sums.forEach((k, v) -> out.add(new Total(k, counts.get(k), ReceiptMath.money(v))));
        out.sort(biggestFirst ? Comparator.comparing(Total::amount).reversed().thenComparing(Total::label)
                : Comparator.comparing(Total::label));
        return out;
    }

    /** Writes the receipts as UTF-8 CSV (with a byte-order mark so Excel reads it correctly). */
    public void exportCsv(List<Receipt> list, Path file) throws IOException {
        Map<UUID, Supplier> supplierById = new HashMap<>();
        for (Supplier s : suppliers.findAll()) supplierById.put(s.id(), s);
        Map<UUID, Project> projectById = new HashMap<>();
        for (Project p : projects.findAll()) projectById.put(p.id(), p);

        StringBuilder sb = new StringBuilder("﻿");
        sb.append(String.join(",", HEADER)).append("\r\n");
        for (Receipt r : list) {
            Supplier s = supplierById.get(r.supplierId());
            Project p = r.projectId() == null ? null : projectById.get(r.projectId());
            sb.append(String.join(",",
                    text(r.receiptNo()), r.date().toString(), text(r.type().label()),
                    text(s == null ? "" : s.businessName()), text(s == null ? "" : s.tin()), text(p == null ? "" : p.name()),
                    ReceiptMath.money(r.subtotal()).toPlainString(), ReceiptMath.money(r.discount()).toPlainString(),
                    ReceiptMath.money(r.vat()).toPlainString(), ReceiptMath.money(r.additionalCharges()).toPlainString(),
                    ReceiptMath.money(r.grandTotal()).toPlainString())).append("\r\n");
        }
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    /** Escapes a text cell: neutralises spreadsheet formulas, then quotes when needed. */
    static String text(String value) {
        String v = value == null ? "" : value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
