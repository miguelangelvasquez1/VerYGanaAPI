package com.verygana2.models.commercial;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.commercial.ContractPurpose;
import com.verygana2.models.enums.commercial.ContractStatus;
import com.verygana2.models.finance.plans.Investment;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.finance.plans.Subscription;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Contrato generado (PDF) para un comercial, y su ciclo de revisión
 * (empresario -> VERYGANA -> firma electrónica). Un comercial puede tener
 * varios a lo largo de su vida: el Contrato Marco del onboarding (pasos 9-11,
 * purpose=ONBOARDING), uno por cada recarga STANDARD/PREMIUM (purpose=RECHARGE)
 * y uno por cada cambio explícito de plan (purpose=PLAN_CHANGE).
 */
@Entity
@Table(name = "commercial_contracts")
@Data
public class CommercialContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commercial_id", nullable = false)
    private CommercialDetails commercial;

    /** Solo poblado cuando purpose = ONBOARDING. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commercial_onboarding_id")
    private CommercialOnboarding onboarding;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContractPurpose purpose;

    /**
     * Depósito vinculado tras la firma: para RECHARGE es la recarga misma; para
     * PLAN_CHANGE con destino STANDARD/PREMIUM es el abono requerido para aplicar
     * el cambio (ver requiredTopUpAmountCents en PlanChangeRequest).
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investment_id")
    private Investment investment;

    /** Igual que investment, pero cuando el abono requerido de un PLAN_CHANGE es hacia BASIC. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    /** Solo para purpose = PLAN_CHANGE — el plan solicitado. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_plan_id")
    private Plan targetPlan;

    /** Solo para purpose = RECHARGE — monto exacto que cubre este contrato, congelado al generarlo. */
    @Column(name = "amount_cents_snapshot")
    private Long amountCentsSnapshot;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ContractStatus status;

    @Column(name = "generated_at", nullable = false)
    private ZonedDateTime generatedAt;

    @Column(name = "business_approved_at")
    private ZonedDateTime businessApprovedAt;

    @Column(name = "admin_reviewer_user_id")
    private Long adminReviewerUserId;

    @Column(name = "admin_reviewed_at")
    private ZonedDateTime adminReviewedAt;

    @Column(name = "admin_decision_notes", length = 1000)
    private String adminDecisionNotes;

    // ==================== FIRMA ELECTRÓNICA (PASO 11b) ====================

    @Column(name = "esignature_provider", length = 30)
    private String esignatureProvider;

    @Column(name = "esignature_envelope_id", length = 200)
    private String esignatureEnvelopeId;

    @Column(name = "esignature_sent_at")
    private ZonedDateTime esignatureSentAt;

    @Column(name = "esignature_signer_email", length = 255)
    private String esignatureSignerEmail;

    @Column(name = "esignature_signed_at")
    private ZonedDateTime esignatureSignedAt;

    @PrePersist
    protected void onCreate() {
        if (generatedAt == null) {
            generatedAt = ZonedDateTime.now();
        }
        if (version == 0) {
            version = 1;
        }
    }
}
