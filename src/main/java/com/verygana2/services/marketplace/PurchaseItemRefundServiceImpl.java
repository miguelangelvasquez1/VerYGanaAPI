package com.verygana2.services.marketplace;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.models.enums.marketplace.PurchaseItemStatus;
import com.verygana2.models.enums.pqrs.MarketplaceIssueReason;
import com.verygana2.models.finance.Copayment;
import com.verygana2.models.finance.KeyWallet;
import com.verygana2.models.finance.PurchaseItemCashRefund;
import com.verygana2.models.marketplace.ProductStock;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.pqrs.Pqrs;
import com.verygana2.repositories.finance.CopaymentRepository;
import com.verygana2.repositories.finance.KeyWalletRepository;
import com.verygana2.repositories.finance.PurchaseItemCashRefundRepository;
import com.verygana2.repositories.marketplace.ProductStockRepository;
import com.verygana2.repositories.marketplace.PurchaseItemRepository;
import com.verygana2.services.interfaces.finance.KeyLedgerService;
import com.verygana2.services.interfaces.finance.KeyWalletService;
import com.verygana2.services.interfaces.finance.ProsperityService;
import com.verygana2.services.interfaces.finance.TreasuryService;
import com.verygana2.services.interfaces.marketplace.KeyCoveragePromotionService;
import com.verygana2.services.interfaces.marketplace.PurchaseItemRefundService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseItemRefundServiceImpl implements PurchaseItemRefundService {

    private final PurchaseItemRepository purchaseItemRepository;
    private final ProductStockRepository productStockRepository;
    private final TreasuryService treasuryService;
    private final ProsperityService prosperityService;
    private final CopaymentRepository copaymentRepository;
    private final KeyWalletRepository keyWalletRepository;
    private final KeyLedgerService keyLedgerService;
    private final KeyWalletService keyWalletService;
    private final PurchaseItemCashRefundRepository purchaseItemCashRefundRepository;
    private final KeyCoveragePromotionService keyCoveragePromotionService;

    @Override
    @Transactional
    public PurchaseItemCashRefund refund(PurchaseItem item, MarketplaceIssueReason reason, Pqrs pqrs) {
        if (item.getStatus() == PurchaseItemStatus.REFUNDED) {
            log.info("[REFUND] PurchaseItem {} ya estaba REFUNDED, no se reprocesa", item.getId());
            return null;
        }

        PurchaseItemCashRefund cashRefund = reverseFinancials(item, pqrs);

        // El código ya fue mostrado al comprador desde el momento de la compra
        // (deliveredCode se entrega instantáneamente, sin importar el estado del
        // ítem), así que el stock NUNCA vuelve a AVAILABLE en un reembolso —
        // reutilizar el mismo código para otro comprador filtraría el secreto ya
        // revelado. Si el motivo es un código inválido se marca INVALID para que
        // quede excluido del inventario; en cualquier otro caso queda tal cual
        // (SOLD) y el comerciante debe reponer stock nuevo.
        ProductStock stock = item.getAssignedProductStock();
        if (stock != null && reason == MarketplaceIssueReason.CODE_INVALID) {
            stock.markAsInvalid();
            productStockRepository.save(stock);
        }

        // maxKeysPct nunca llega a 100 (20 o 50 según el plan del comercial — ver
        // Product.maxKeysPct), así que todo ítem tiene una porción en efectivo
        // mínima obligatoria: cashRefund nunca es null aquí. El ítem se queda
        // IN_REVIEW — CashRefundServiceImpl.markPaid es el único lugar donde
        // puede llegar a REFUNDED.
        log.info("[REFUND] PurchaseItem {} en espera de pago manual del reembolso, sigue IN_REVIEW (reason={})",
                item.getId(), reason);

        return cashRefund;
    }

    @Override
    @Transactional
    public void expireUnclaimed(PurchaseItem item) {
        if (item.getStatus() == PurchaseItemStatus.EXPIRED_UNCLAIMED) {
            log.info("[REFUND] PurchaseItem {} ya estaba EXPIRED_UNCLAIMED, no se reprocesa", item.getId());
            return;
        }

        reverseFinancials(item, null);

        // El código nunca estuvo mal — el comprador simplemente no lo reclamó a
        // tiempo — así que a diferencia de refund(reason=CODE_INVALID) el stock
        // nunca se marca INVALID aquí.
        item.setStatus(PurchaseItemStatus.EXPIRED_UNCLAIMED);
        purchaseItemRepository.save(item);

        log.info("[REFUND] PurchaseItem {} vencido sin reclamar, reembolsado internamente", item.getId());
    }

    /**
     * Mecánica financiera compartida entre refund() y expireUnclaimed():
     * reversa comisión + porción en llaves/efectivo, acredita llaves de
     * vuelta al KeyWallet si aplica, y crea el reembolso en efectivo
     * pendiente de pago manual si aplica (vinculado a {@code pqrs}, si no es
     * null). No toca el status del ítem ni el stock — eso lo decide cada
     * método público según el motivo del reembolso.
     *
     * @return el PurchaseItemCashRefund creado, o null si no hubo porción en efectivo.
     */
    private PurchaseItemCashRefund reverseFinancials(PurchaseItem item, Pqrs pqrs) {
        Copayment copayment = copaymentRepository.findByPurchaseId(item.getPurchase().getId())
                .orElseThrow(() -> new IllegalStateException(
                        "[REFUND] Copayment no encontrado para purchaseId=" + item.getPurchase().getId()));

        long itemTotalCents = item.getCommissionCents() + item.getNetToCommercialCents();
        long keysPortionCents = computeItemKeysShareCents(item, copayment);
        long cashPortionCents = itemTotalCents - keysPortionCents;

        // La billetera se bloquea antes que tesorería, igual que en el gasto y en el
        // copago. Al revés, un reembolso y un gasto simultáneos del mismo usuario se
        // esperan mutuamente (deadlock).
        KeyWallet wallet = keysPortionCents > 0 ? lockWallet(copayment) : null;

        // La venta no se perfeccionó: lo que absorbió el Saldo de Prosperidad vuelve a él.
        // Va antes que tesorería por el mismo motivo que la billetera: el depósito y el
        // copago bloquean prosperidad → tesorería, y este debe seguir ese orden.
        prosperityService.reintegrateRefund(item);

        // Si el ítem usó una promoción especial, su presupuesto vuelve a estar disponible.
        // Se bloquea antes que tesorería, que es el orden de la compra y del copago.
        KeyCoveragePromotionService.ExcessRefund excessRefund = keyCoveragePromotionService.restoreForRefund(item);

        // La comisión que se revierte ya es solo la de la porción no absorbida.
        treasuryService.reversePurchaseItemForRefund(
                item.getCommissionCents(), item.getCommissionVatCents(),
                keysPortionCents, cashPortionCents, copayment.getId());

        // KEYS_RESERVE acaba de recuperar todas las llaves de la venta, incluido el exceso
        // que en su momento le repuso el Fondo: esa parte se le devuelve, para que cada
        // bolsillo quede como estaba antes de la venta.
        if (excessRefund.cents() > 0) {
            treasuryService.refundPromotionExcess(excessRefund.cents(), excessRefund.fundPocketStillHeld(),
                    excessRefund.treasuryReference());
        }

        if (wallet != null) {
            creditBackKeys(wallet, item, copayment, keysPortionCents);
        }

        if (cashPortionCents > 0) {
            PurchaseItemCashRefund cashRefund = PurchaseItemCashRefund.builder()
                    .purchaseItem(item)
                    .pqrs(pqrs)
                    .amountCents(cashPortionCents)
                    .build();
            purchaseItemCashRefundRepository.save(cashRefund);
            log.info("[REFUND] Reembolso en efectivo pendiente de pago manual creado: purchaseItemId={}, amountCents={}",
                    item.getId(), cashPortionCents);
            return cashRefund;
        }

        return null;
    }

    /**
     * Porción del precio del ítem pagada con llaves: exactamente la que quedó registrada
     * en el ítem al comprar (PurchaseItem.keysValueCents).
     *
     * Los ítems anteriores a MP-12 no la tienen: ahí el reparto solo existía a nivel de
     * copago y se aproxima por el peso del ítem dentro del total de la compra.
     */
    private long computeItemKeysShareCents(PurchaseItem item, Copayment copayment) {
        if (item.getKeysValueCents() != null) {
            return item.getKeysValueCents();
        }

        if (copayment.getKeysValueCents() == null || copayment.getKeysValueCents() <= 0) {
            return 0;
        }

        Purchase purchase = item.getPurchase();
        long purchaseTotal = purchase.getTotalCents() == null ? 0 : purchase.getTotalCents();
        if (purchaseTotal <= 0) {
            return 0;
        }

        return Math.round(copayment.getKeysValueCents() * (item.getSubtotalCents() / (double) purchaseTotal));
    }

    private KeyWallet lockWallet(Copayment copayment) {
        return keyWalletRepository.findByConsumerIdForUpdate(copayment.getConsumer().getId())
                .orElseThrow(() -> new IllegalStateException(
                        "[REFUND] KeyWallet no encontrado: consumerId=" + copayment.getConsumer().getId()));
    }

    private void creditBackKeys(KeyWallet wallet, PurchaseItem item, Copayment copayment, long keysPortionCents) {
        // Vuelven con la vigencia de las llaves que se ganan hoy: antes regresaban sin
        // fecha de vencimiento y no vencían nunca.
        keyLedgerService.refundPurchaseItem(wallet, keysPortionCents, copayment.getId(), item.getId(),
                keyWalletService.calculatePurchaseExpiry());

        log.info("[REFUND] Llaves acreditadas de vuelta: purchaseItemId={}, amountCents={}",
                item.getId(), keysPortionCents);
    }
}
