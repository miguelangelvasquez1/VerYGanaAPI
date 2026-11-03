package com.verygana2.models.tax;

import java.time.LocalDate;

import org.hibernate.annotations.Immutable;

import com.verygana2.models.commercial.CommercialDocument;
import com.verygana2.models.enums.tax.IncomeConcept;
import com.verygana2.models.enums.tax.IncomeScope;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Ingresos, ventas o recaudos informados para un periodo (inciso e), incluidas las
 * operaciones fuera de VERYGANA. {@code scope} dice qué incluye el valor para no sumar
 * dos veces las ventas de la plataforma.
 */
@Entity
@Immutable
@Table(name = "tax_income_reports")
@Getter
@Setter
public class TaxIncomeReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tax_profile_version_id", nullable = false)
    private TaxProfileVersion version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncomeConcept concept;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private IncomeScope scope;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "support_document_id")
    private CommercialDocument supportDocument;

    @Column(length = 500)
    private String notes;
}
