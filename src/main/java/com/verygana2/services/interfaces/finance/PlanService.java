package com.verygana2.services.interfaces.finance;

import java.util.UUID;

import com.verygana2.dtos.finance.plans.responses.EffectivePlanStateResponseDTO;
import com.verygana2.dtos.finance.plans.responses.PlanCatalogResponseDTO;
import com.verygana2.dtos.finance.plans.responses.PlanPaymentStatusResponseDTO;
import com.verygana2.dtos.finance.plans.responses.RechargePreviewResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.ContractSummaryResponseDTO;
import com.verygana2.dtos.wompi.WompiCheckoutResponseDTO;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.userDetails.CommercialDetails;

public interface PlanService {

    WompiCheckoutResponseDTO initiatePlanPayment(
            CommercialDetails commercial,
            PlanCode planCode,
            Long amountCents);

    void handleWompiResult(UUID wompiTransactionId);

    PlanPaymentStatusResponseDTO getPaymentStatus(String reference, CommercialDetails commercial);

    EffectivePlanStateResponseDTO getEffectivePlanState(CommercialDetails commercial);

    /**
     * Catálogo completo de planes activos, marcando cuál es el plan vigente del
     * comercial (para que el front use "recargar" en vez de "cambiar de plan" en esa
     * tarjeta/fila). No requiere estar en onboarding.
     */
    PlanCatalogResponseDTO getPlanCatalog(CommercialDetails commercial);

    /**
     * Resumen de solo lectura de lo que implicaría una recarga — para que el comercial
     * lo revise antes de que se genere el otrosí y se envíe a firma. No crea nada.
     */
    RechargePreviewResponseDTO previewRecharge(CommercialDetails commercial, Long amountCents);

    /**
     * Solicita una recarga de saldo STANDARD/PREMIUM: genera el contrato específico a
     * ese monto y lo envía a firma electrónica de inmediato (sin revisión humana — el
     * monto ya está acotado por el rango del plan). El pago solo se inicia después de
     * que el contrato quede firmado, vía {@link #generateRechargeCheckout}.
     */
    ContractSummaryResponseDTO requestRecharge(CommercialDetails commercial, Long amountCents);

    /**
     * Genera el checkout de Wompi para una recarga ya firmada. Solo puede llamarse una
     * vez por contrato (falla si ya tiene un Investment vinculado).
     */
    WompiCheckoutResponseDTO generateRechargeCheckout(Long contractId, CommercialDetails commercial);

    /**
     * Genera el checkout del abono requerido por un cambio de plan ya firmado que no
     * aplica solo con la firma (PlanChangeRequest en PAYMENT_PENDING). Al confirmarse
     * el pago, el cambio de plan se aplica automáticamente.
     */
    WompiCheckoutResponseDTO generatePlanChangeTopUpCheckout(Long requestId, CommercialDetails commercial);

}
