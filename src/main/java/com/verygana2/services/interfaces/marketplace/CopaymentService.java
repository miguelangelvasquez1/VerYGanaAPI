package com.verygana2.services.interfaces.marketplace;

import java.util.UUID;

public interface CopaymentService {

    /**
     * Procesa el resultado del webhook de Wompi para un CHARGE_COPAYMENT.
     *
     * Si APPROVED: confirma las llaves reservadas, mueve los fondos a
     * PAYOUTS_PENDING y entrega los códigos al comprador.
     * Si DECLINED / ERROR / VOIDED: libera las llaves reservadas y
     * devuelve el stock al pool disponible.
     *
     * @param wompiTransactionId UUID interno del WompiTransaction ya actualizado
     */
    void handleWompiResult(UUID wompiTransactionId);

    /**
     * Cancela todas las compras PENDING cuya sesión de Wompi ya venció.
     * Devuelve el stock reservado al pool disponible y libera las llaves
     * bloqueadas al consumidor.
     *
     * @param maxAgeMinutes minutos máximos que una compra puede estar PENDING
     */
    void expireStale(int maxAgeMinutes);
}
