package com.verygana2.dtos.purchase.responses;

import java.time.Instant;

import com.verygana2.models.enums.marketplace.PurchaseStatus;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InitiatePurchaseResponseDTO {
    private Long purchaseId;
    private String referenceId;
    /** Monto en centavos que Wompi debe cobrar (totalCents - keysValueCents). */
    private Long cashAmountCents;
    /** Precio total de la compra en centavos. */
    private Long totalAmountCents;
    /** Parte cubierta con llaves en centavos (keysUsed × 1.000). */
    private Long keysValueCents;
    private PurchaseStatus status;
    /** URL del checkout de Wompi a la que el frontend debe redirigir al usuario. */
    private String checkoutUrl;
    private Instant timestamp;
}
