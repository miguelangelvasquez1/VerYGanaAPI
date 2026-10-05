package com.verygana2.dtos.finance.plans.responses;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.commercial.ContractStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Recarga en curso del comercial (otrosí generado y todavía sin pagar) — lo que
 * el frontend necesita para retomarla o cancelarla aunque haya perdido el
 * contractId (cerró la pestaña, cambió de dispositivo, volvió al día siguiente).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenRechargeResponseDTO {

    /** Paso que le falta al comercial para completar la recarga. */
    public enum NextAction {
        /** El otrosí está en firma electrónica — falta firmarlo. */
        SIGN,
        /** Firmado y sin pagar — llamar a /plans/recharge/{contractId}/checkout. */
        PAY,
        /** El último intento de pago fue rechazado — /checkout genera uno nuevo. */
        RETRY_PAYMENT,
        /** Hay un pago en proceso en Wompi — solo lo reporta /reconcile, que es quien consulta a la pasarela. */
        WAIT_PAYMENT
    }

    private Long contractId;
    private ContractStatus status;
    private NextAction nextAction;

    /** Explicación en lenguaje natural, lista para mostrar. */
    private String message;

    /** Montos en pesos colombianos (no en centavos), igual que en el preview. */
    private Long amountPesos;
    private Long vatAmountPesos;
    private Long totalToPayPesos;

    private ZonedDateTime generatedAt;
    private ZonedDateTime signedAt;

    /** Momento desde el que el job de vencimiento la cancela sola si sigue sin pagarse. */
    private ZonedDateTime expiresAt;

    /** true si ya se abrió un checkout de Wompi para esta recarga (pagado o no). */
    private boolean paymentAttempted;
}
