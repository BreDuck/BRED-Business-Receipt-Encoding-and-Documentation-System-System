package com.gudo.rms.services;

import com.gudo.rms.models.Supplier;
import com.gudo.rms.models.Receipt;
import com.gudo.rms.models.ValidationException;
import com.gudo.rms.repositories.ReceiptRepository;
import com.gudo.rms.repositories.SupplierRepository;

import java.util.List;
import java.util.UUID;

/** Supplier rules: required fields, unique tin, and delete only when no receipt uses it (admin only). */
public final class SupplierService {
    private final SupplierRepository repo;
    private final ReceiptRepository receipts;
    private final CurrentUser who;

    public SupplierService(SupplierRepository repo, ReceiptRepository receipts, CurrentUser who) {
        this.repo = repo;
        this.receipts = receipts;
        this.who = who;
    }

    public List<Supplier> findAll() { return repo.findAll(); }

    /** Validates and saves (null id creates). */
    public Supplier save(Supplier v) {
        Guard.requireSignedIn(who);
        if (Guard.blank(v.businessName())) throw new ValidationException("Business name is required.");
        if (Guard.blank(v.tin())) throw new ValidationException("TIN is required.");
        for (Supplier other : repo.findAll()) {
            if (!other.id().equals(v.id()) && Guard.norm(other.tin()).equals(Guard.norm(v.tin()))) {
                throw new ValidationException("A supplier with that TIN already exists.");
            }
        }
        return repo.save(v);
    }

    /** Deletes a supplier; blocked while receipts still use it. Administrators only. */
    public void delete(UUID id) {
        Guard.requireAdmin(who, "Only administrators can delete suppliers.");
        long used = receipts.findAll().stream().filter((Receipt r) -> id.equals(r.supplierId())).count();
        if (used > 0) throw new ValidationException("Cannot delete: " + used + " receipt(s) use this supplier.");
        repo.delete(id);
    }
}
