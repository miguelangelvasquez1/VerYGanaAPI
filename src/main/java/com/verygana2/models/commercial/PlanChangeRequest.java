package com.verygana2.models.commercial;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.finance.plans.PlanChangeRequestStatus;
import com.verygana2.models.finance.plans.Plan;
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
 * Solicitud explícita de un comercial para cambiar de plan (BASIC/STANDARD/PREMIUM).
 * Un comercial nunca cambia de plan automáticamente — solo puede renovar su plan
 * actual salvo que abra una de estas solicitudes, que siempre requiere aprobación
 * de VerYGana vía el mismo pipeline de revisión/firma que el Contrato Marco.
 */
@Entity
@Table(name = "plan_change_requests")
@Data
public class PlanChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commercial_id", nullable = false)
    private CommercialDetails commercial;

    /** Snapshot del plan actual al momento de pedir el cambio, para auditoría/histórico. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_plan_id", nullable = false)
    private Plan fromPlan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_plan_id", nullable = false)
    private Plan toPlan;

    /** Solo relevante si toPlan es STANDARD/PREMIUM. */
    @Column(name = "requested_investment_amount_cents")
    private Long requestedInvestmentAmountCents;

    /**
     * Abono requerido para que el cambio aplique, calculado al crear la solicitud e
     * independiente del saldo actual o de abonos anteriores: para destino BASIC ->
     * toPlan.monthlyPriceCents; para STANDARD/PREMIUM -> el monto a invertir indicado
     * por el comercial (acotado al rango del plan) o, si no lo indica,
     * toPlan.minInvestmentCents. 0/null = el cambio aplica solo con la firma.
     */
    @Column(name = "required_top_up_amount_cents")
    private Long requiredTopUpAmountCents;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    private CommercialContract contract;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PlanChangeRequestStatus status;

    @Column(name = "requested_at", nullable = false)
    private ZonedDateTime requestedAt;

    @Column(name = "applied_at")
    private ZonedDateTime appliedAt;

    /**
     * Motivo del rechazo copiado del contrato vinculado cuando VerYGana lo rechaza
     * (status = REJECTED). Se muestra al comercial hasta que lo da por leído.
     */
    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    /**
     * Momento en que el comercial dio por leído el rechazo. Mientras sea null y el
     * status sea REJECTED, {@code getCurrent} sigue devolviendo esta solicitud con el
     * motivo; una vez marcado, {@code getCurrent} responde como si no hubiera solicitud
     * y el comercial puede crear una nueva.
     */
    @Column(name = "rejection_acknowledged_at")
    private ZonedDateTime rejectionAcknowledgedAt;

    @PrePersist
    protected void onCreate() {
        if (requestedAt == null) {
            requestedAt = ZonedDateTime.now();
        }
        if (status == null) {
            status = PlanChangeRequestStatus.REQUESTED;
        }
    }
}
