package com.verygana2.dtos.recognitions;

/**
 * Estado del fondo de fortalecimiento frente a los reconocimientos.
 *
 * @param fortificationBalanceCents saldo disponible hoy para nuevas compras
 * @param activeSpentCents          lo pagado en reconocimientos vigentes (no cancelados ni vencidos)
 */
public record RecognitionFundDTO(
        long fortificationBalanceCents,
        long activeSpentCents,
        long purchasedCount,
        long deliveredCount,
        long expiredCount,
        long cancelledCount) {
}
