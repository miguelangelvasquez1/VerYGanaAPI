package com.verygana2.services.interfaces;


import com.verygana2.models.OutboxEvent;

public interface OutboxEventProcessor {

    /**
     * Procesa un evento individual del outbox en su propia transacción.
     * Si falla, actualiza retry_count y devuelve el evento a PENDING o FAILED.
     *
     * @param event evento a procesar
     */
    void process(OutboxEvent event);
}
