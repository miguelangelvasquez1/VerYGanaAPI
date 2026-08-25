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
     * Monto adicional requerido para que el cambio aplique, calculado al crear la
     * solicitud: para destino BASIC -> toPlan.monthlyPriceCents; para STANDARD/PREMIUM
     * -> max(0, toPlan.minInvestmentCents - saldoActual). 0/null = el cambio aplica
     * solo con la firma, sin pago adicional.
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
