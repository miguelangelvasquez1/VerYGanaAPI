package com.verygana2.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Publicado cuando el comercio valida el PIN de un ítem físico y este pasa a CLAIMED.
 * Lo escucha el flujo de reconocimientos para dar por entregada la compra de VERYGANA
 * (Contrato B 10.16) sin acoplar PurchaseItemServiceImpl a él. Se publica dentro de la
 * transacción del reclamo: si el listener falla, el reclamo hace rollback.
 */
@Getter
public class PurchaseItemClaimedEvent extends ApplicationEvent {

    private final Long purchaseId;
    private final Long purchaseItemId;

    public PurchaseItemClaimedEvent(Object source, Long purchaseId, Long purchaseItemId) {
        super(source);
        this.purchaseId = purchaseId;
        this.purchaseItemId = purchaseItemId;
    }
}
