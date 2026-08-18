package com.verygana2.services.interfaces.marketplace;

import com.verygana2.models.enums.pqrs.MarketplaceIssueReason;
import com.verygana2.models.finance.PurchaseItemCashRefund;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.pqrs.Pqrs;

public interface PurchaseItemRefundService {

    /**
     * Reembolsa un PurchaseItem: lo marca REFUNDED (queda excluido del payout
     * para siempre), revierte la comisión retenida y el neto correspondiente
     * en tesorería, acredita de vuelta las llaves usadas (si aplica) y marca
     * el stock INVALID cuando el motivo es un código inválido.
     *
     * Alcance de esta fase: solo la mecánica interna (tesorería + llaves). El
     * reembolso del efectivo al medio de pago original vía Wompi no está
     * automatizado todavía — queda para gestión manual de soporte.
     *
     * @param pqrs el PQRS que originó este reembolso (nullable — null si se
     *             llama fuera del flujo de PQRS). Si hay porción en efectivo,
     *             el PurchaseItemCashRefund creado queda vinculado a este PQRS
     *             para que PqrsServiceImpl decida si puede resolverlo ya o
     *             debe esperar a que el admin confirme el pago manual.
     * @return el PurchaseItemCashRefund creado si hubo porción en efectivo
     *         pendiente de pago manual, o null si todo se cubrió con llaves
     *         (o el monto era cero).
     *
     * Idempotente: si el ítem ya está REFUNDED, no hace nada y retorna null.
     */
    PurchaseItemCashRefund refund(PurchaseItem item, MarketplaceIssueReason reason, Pqrs pqrs);

    /**
     * Vence un ítem físico que nadie reclamó dentro del plazo (ver
     * PurchaseItemExpirationScheduler): misma mecánica financiera que
     * {@link #refund}, pero termina en EXPIRED_UNCLAIMED en vez de REFUNDED
     * —el código nunca estuvo mal, el comprador simplemente no lo reclamó—
     * así que el stock nunca se marca INVALID.
     *
     * Idempotente: si el ítem ya está EXPIRED_UNCLAIMED, no hace nada.
     */
    void expireUnclaimed(PurchaseItem item);
}
