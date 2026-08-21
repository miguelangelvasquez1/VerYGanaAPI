package com.verygana2.services.interfaces.finance;

import java.util.List;

import com.verygana2.models.commercial.PlanChangeRequest;
import com.verygana2.models.finance.plans.Plan.PlanCode;

/**
 * Solicitud explícita de cambio de plan — nunca automática. Siempre pasa por el
 * mismo pipeline de revisión/firma que el Contrato Marco (a diferencia de la
 * recarga, que se salta la revisión humana).
 */
public interface PlanChangeRequestService {

    PlanChangeRequest requestPlanChange(Long commercialId, PlanCode targetPlanCode, Long intendedInvestmentAmountCents);

    PlanChangeRequest cancelPlanChangeRequest(Long commercialId, Long requestId);

    PlanChangeRequest getCurrent(Long commercialId);

    /** Cola de compliance: solicitudes cuyo contrato sigue en revisión (no terminales). */
    List<PlanChangeRequest> listPendingReview();

    /**
     * Aplica el cambio de plan si la solicitud sigue en PAYMENT_PENDING — llamado por
     * PlanServiceImpl cuando confirma un Investment/Subscription vinculado a ella.
     * No-op si ya se aplicó, fue rechazada o cancelada.
     */
    void applyIfPending(Long planChangeRequestId);
}
