package com.verygana2.services.interfaces.finance;

import java.util.List;

import com.verygana2.dtos.finance.plans.responses.PlanChangePreviewResponseDTO;
import com.verygana2.models.commercial.PlanChangeRequest;
import com.verygana2.models.finance.plans.Plan.PlanCode;

/**
 * Solicitud explícita de cambio de plan — nunca automática. Siempre pasa por el
 * mismo pipeline de revisión/firma que el Contrato Marco (a diferencia de la
 * recarga, que se salta la revisión humana).
 */
public interface PlanChangeRequestService {

    PlanChangeRequest requestPlanChange(Long commercialId, PlanCode targetPlanCode, Long intendedInvestmentAmountCents);

    /**
     * Resumen de solo lectura de lo que implicaría el cambio — para que el comercial lo
     * revise antes de que se genere el otrosí y se envíe a revisión/firma. No crea nada.
     */
    PlanChangePreviewResponseDTO previewPlanChange(Long commercialId, PlanCode targetPlanCode, Long intendedInvestmentAmountCents);

    PlanChangeRequest cancelPlanChangeRequest(Long commercialId, Long requestId);

    /**
     * Solicitud vigente del comercial. Si no hay ninguna abierta pero existe un rechazo
     * que todavía no dio por leído, devuelve ese rechazo (status REJECTED + rejectionReason)
     * para que el frontend muestre el motivo. Devuelve null cuando no hay nada pendiente.
     */
    PlanChangeRequest getCurrent(Long commercialId);

    /**
     * El comercial da por leído el motivo de un rechazo. A partir de aquí {@code getCurrent}
     * responde como si no hubiera solicitud y puede abrir una nueva. Idempotente.
     */
    PlanChangeRequest acknowledgeRejection(Long commercialId, Long requestId);

    /** Cola de compliance: solicitudes cuyo contrato sigue en revisión (no terminales). */
    List<PlanChangeRequest> listPendingReview();

    /**
     * Aplica el cambio de plan si la solicitud sigue en PAYMENT_PENDING — llamado por
     * PlanServiceImpl cuando confirma un Investment/Subscription vinculado a ella.
     * No-op si ya se aplicó, fue rechazada o cancelada.
     */
    void applyIfPending(Long planChangeRequestId);
}
