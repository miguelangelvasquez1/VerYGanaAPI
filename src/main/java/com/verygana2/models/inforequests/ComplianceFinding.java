package com.verygana2.models.inforequests;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import com.verygana2.models.enums.AccountStatus;
import com.verygana2.models.enums.inforequests.ContractualMeasure;
import com.verygana2.models.enums.inforequests.FindingClassification;
import com.verygana2.models.enums.inforequests.FindingSeverity;
import com.verygana2.models.userDetails.CommercialDetails;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Decisión de compliance sobre un hecho (4.8): qué se observó, con qué evidencia, gravedad,
 * afectación, posibilidad de subsanación y fundamento contractual. Es el único camino por el
 * que se aplica una medida sobre la cuenta. Inmutable; una medida nunca borra antecedentes.
 */
@Entity
@Immutable
@Table(name = "compliance_findings")
@Getter
@Setter
public class ComplianceFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commercial_id", nullable = false)
    private CommercialDetails commercial;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id")
    private InformationRequest request;

    @Column(name = "fact_observed", nullable = false, length = 2000)
    private String factObserved;

    @Column(length = 2000)
    private String evidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FindingClassification classification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FindingSeverity severity;

    @Column(nullable = false, length = 1000)
    private String impact;

    @Column(nullable = false)
    private boolean remediable;

    @Column(name = "contractual_basis", nullable = false, length = 1000)
    private String contractualBasis;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private ContractualMeasure measure;

    @Column(name = "measure_scope", length = 500)
    private String measureScope;

    /** Estado de cuenta aplicado por la medida, si cambió. */
    @Enumerated(EnumType.STRING)
    @Column(name = "account_status_applied", length = 30)
    private AccountStatus accountStatusApplied;

    @Column(name = "decided_by_user_id", nullable = false)
    private Long decidedByUserId;

    @Column(name = "decided_at", nullable = false)
    private ZonedDateTime decidedAt;

    @PrePersist
    protected void onCreate() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
        if (decidedAt == null) {
            decidedAt = ZonedDateTime.now();
        }
    }
}
