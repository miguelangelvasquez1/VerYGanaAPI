package com.verygana2.models.tax;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.Immutable;

import com.verygana2.models.commercial.CommercialDocument;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Un envío de la ficha tributaria (incisos a–h del 4.7), tal como lo declaró el empresario.
 * Inmutable: una corrección es una versión nueva, y la anterior queda como antecedente.
 * Lo acreditado va aparte, en {@link TaxProfileSectionReview}.
 */
@Entity
@Immutable
@Table(name = "tax_profile_versions",
        uniqueConstraints = @UniqueConstraint(name = "uk_tax_profile_version", columnNames = { "tax_profile_id", "version_number" }))
@Getter
@Setter
public class TaxProfileVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tax_profile_id", nullable = false)
    private TaxProfile taxProfile;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    // ---- a) RUT ----

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rut_document_id")
    private CommercialDocument rutDocument;

    @ElementCollection
    @CollectionTable(name = "tax_profile_version_rut_responsibilities",
            joinColumns = @JoinColumn(name = "tax_profile_version_id"))
    @OrderColumn(name = "position")
    @Column(name = "code", nullable = false, length = 30)
    private List<String> rutResponsibilityCodes = new ArrayList<>();

    // ---- b) Régimen y actividades ----

    @Column(name = "regime_code", length = 30)
    private String regimeCode;

    @OneToMany(mappedBy = "version", cascade = CascadeType.PERSIST)
    private List<TaxProfileActivity> activities = new ArrayList<>();

    // ---- c) IVA e INC ----

    @Column(name = "vat_condition_code", length = 30)
    private String vatConditionCode;

    @Column(name = "inc_condition_code", length = 30)
    private String incConditionCode;

    /** Alcance de la condición: a qué productos/servicios aplica, con sus matices. */
    @Column(name = "tax_condition_scope", length = 1000)
    private String taxConditionScope;

    // ---- d) Facturación y retención ----

    @Column(name = "invoicing_obligated", nullable = false)
    private boolean invoicingObligated;

    @Column(name = "invoicing_method_code", length = 30)
    private String invoicingMethodCode;

    @Column(name = "invoicing_notes", length = 1000)
    private String invoicingNotes;

    @Column(name = "withholding_agent", nullable = false)
    private boolean withholdingAgent;

    @Column(name = "withholding_notes", length = 1000)
    private String withholdingNotes;

    // ---- e) Ingresos o ventas ----

    @OneToMany(mappedBy = "version", cascade = CascadeType.PERSIST)
    private List<TaxIncomeReport> incomeReports = new ArrayList<>();

    // ---- f) Establecimientos ----

    /**
     * Establecimientos activos al momento del envío. Se explica con los registros de
     * {@link CommercialEstablishment} vigentes en {@code submittedAt}.
     */
    @Column(name = "establishments_count", nullable = false)
    private int establishmentsCount;

    // ---- g) Capacidad de crecimiento ----

    /** Lo que hoy puede atender (límites operativos actuales). */
    @Column(name = "current_capacity", nullable = false, length = 2000)
    private String currentCapacity;

    /** Escenario de crecimiento previsto. Informativo: no habilita nada. */
    @Column(name = "growth_scenario", length = 2000)
    private String growthScenario;

    @Column(name = "growth_measures", length = 2000)
    private String growthMeasures;

    // ---- h) Asesoría contable o tributaria ----

    /** Solo informativo: no crea usuarios ni da acceso a nadie. */
    @Column(name = "has_accounting_advisor", nullable = false)
    private boolean hasAccountingAdvisor;

    @Column(name = "advisor_scope", length = 1000)
    private String advisorScope;

    // ---- Evidencia del envío ----

    @Column(name = "submitted_by_user_id", nullable = false)
    private Long submittedByUserId;

    @Column(name = "submitted_at", nullable = false)
    private ZonedDateTime submittedAt;

    @Column(name = "submitted_ip", length = 64)
    private String submittedIp;

    @Column(name = "submitted_user_agent", length = 500)
    private String submittedUserAgent;
}
