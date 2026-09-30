package com.verygana2.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resultado de aumentar el presupuesto de un activo (anuncio, encuesta o campaña).
 * Es común a los tres tipos: los montos siempre van en centavos.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetIncreaseResponseDTO {

    private Long assetId;

    /** Monto descontado de la wallet por este aumento. */
    private Long chargedCents;

    /** Presupuesto total del activo después del aumento (lo ya gastado + lo disponible). */
    private Long totalBudgetCents;

    /** Presupuesto aún no consumido del activo después del aumento. */
    private Long remainingBudgetCents;

    /** Estado del activo después del aumento (el nombre del enum de estado de cada tipo). */
    private String status;

    /** {@code true} si el activo estaba COMPLETED y el aumento lo volvió a poner en circulación. */
    private boolean reopened;

    /** Saldo de la wallet del comercial después del cobro. */
    private Long walletBalanceCents;
}
