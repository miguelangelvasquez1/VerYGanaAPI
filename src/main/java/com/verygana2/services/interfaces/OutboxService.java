package com.verygana2.services.interfaces;


public interface OutboxService {
    /**
     * Guarda un evento de referido completado en la tabla outbox.
     * Debe llamarse dentro de la misma transacción del registro del usuario.
     *
     * @param referrerId ID del usuario que refirió (quien recibe el ticket)
     * @param referralId ID del nuevo usuario registrado (usado como sourceId)
     */

    void saveReferralEvent(Long referrerId, Long referralId);

    /**
     * Job que procesa los eventos pendientes en la tabla outbox.
     * Se ejecuta automáticamente cada 30 segundos.
     */
    void processOutboxEvents();
}
