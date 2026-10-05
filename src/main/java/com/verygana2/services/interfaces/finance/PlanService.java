package com.verygana2.services.interfaces.finance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.verygana2.dtos.finance.plans.responses.EffectivePlanStateResponseDTO;
import com.verygana2.dtos.finance.plans.responses.OpenRechargeResponseDTO;
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
     * Genera el checkout de Wompi para una recarga ya firmada. Puede volver a llamarse
     * si el pago quedó a medias: primero concilia con Wompi el checkout anterior y
     * luego lo retoma (si nunca se pagó) o genera uno nuevo (si fue rechazado). Falla
     * si el pago anterior está aprobado o todavía en proceso.
     */
    WompiCheckoutResponseDTO generateRechargeCheckout(Long contractId, CommercialDetails commercial);

    /**
     * Recarga en curso del comercial (otrosí generado y sin pagar), con el paso que le
     * falta — para que el frontend la retome o la cancele sin haber guardado el
     * contractId. Solo lectura: no consulta a Wompi.
     */
    Optional<OpenRechargeResponseDTO> getOpenRecharge(CommercialDetails commercial);

    /**
     * Concilia con Wompi el pago de una recarga en curso y aplica el resultado si el
     * webhook nunca llegó. Empty si la recarga ya no está en curso (quedó pagada, o ya
     * estaba cancelada/rechazada).
     */
    Optional<OpenRechargeResponseDTO> reconcileRecharge(Long contractId, CommercialDetails commercial);

    /**
     * Cancela una recarga en curso a pedido del comercial. Si ya tenía un checkout
     * abierto, concilia antes con Wompi y se niega si el pago está aprobado o en proceso.
     */
    ContractSummaryResponseDTO cancelRecharge(Long contractId, CommercialDetails commercial);

    /** Recargas en curso que ya superaron el plazo de vencimiento configurado. */
    List<Long> findStaleRechargeContractIds();

    /**
     * Vence (cancela) una recarga que superó el plazo sin pagarse, conciliando antes
     * con Wompi.
     *
     * @return false si no se canceló porque el pago resultó aprobado o sigue en proceso
     */
    boolean expireRecharge(Long contractId);

    /**
     * Genera el checkout del abono requerido por un cambio de plan ya firmado que no
     * aplica solo con la firma (PlanChangeRequest en PAYMENT_PENDING). Al confirmarse
     * el pago, el cambio de plan se aplica automáticamente.
     */
    WompiCheckoutResponseDTO generatePlanChangeTopUpCheckout(Long requestId, CommercialDetails commercial);

}
