package com.verygana2.models.tax;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import com.verygana2.models.enums.tax.TaxCatalogType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Código tributario autorizado (régimen, responsabilidad RUT, condición IVA/INC, método de
 * facturación). Lo administra compliance con fundamento y vigencia: el código no inventa
 * condiciones legales. Un código no se edita ni se borra; se retira fijando {@code validTo}.
 */
@Entity
@Table(name = "tax_catalog_entries",
        uniqueConstraints = @UniqueConstraint(name = "uk_tax_catalog_type_code", columnNames = { "catalog_type", "code" }))
@Getter
@Setter
public class TaxCatalogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "catalog_type", nullable = false, length = 30, updatable = false)
    private TaxCatalogType catalogType;

    @Column(nullable = false, length = 30, updatable = false)
    private String code;

    @Column(nullable = false, length = 200, updatable = false)
    private String label;

    /** Norma o fuente que respalda el código. */
    @Column(name = "legal_basis", nullable = false, length = 500, updatable = false)
    private String legalBasis;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "retired_by_user_id")
    private Long retiredByUserId;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
    }

    public boolean isActiveOn(LocalDate date) {
        return !validFrom.isAfter(date) && (validTo == null || !validTo.isBefore(date));
    }
}
