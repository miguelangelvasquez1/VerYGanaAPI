package com.verygana2.services.marketplace;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.models.enums.finance.CopaymentStatus;
import com.verygana2.models.enums.finance.WompiTransactionStatus;
import com.verygana2.models.enums.marketplace.PurchaseItemStatus;
import com.verygana2.models.finance.Copayment;
import com.verygana2.models.finance.KeyTransaction;
import com.verygana2.models.finance.KeyWallet;
import com.verygana2.models.finance.WompiTransaction;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.marketplace.ProductStock;
import com.verygana2.repositories.finance.CopaymentRepository;
import com.verygana2.repositories.finance.KeyTransactionRepository;
import com.verygana2.repositories.finance.KeyWalletRepository;
import com.verygana2.repositories.finance.WompiTransactionRepository;
import com.verygana2.repositories.marketplace.ProductStockRepository;
import com.verygana2.repositories.marketplace.PurchaseRepository;
import com.verygana2.services.interfaces.finance.TreasuryService;
import com.verygana2.services.interfaces.marketplace.CopaymentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CopaymentServiceImpl implements CopaymentService {

    private final CopaymentRepository copaymentRepository;
    private final WompiTransactionRepository wompiTransactionRepository;
    private final PurchaseRepository purchaseRepository;
    private final KeyWalletRepository keyWalletRepository;
    private final KeyTransactionRepository keyTransactionRepository;
    private final ProductStockRepository productStockRepository;
    private final TreasuryService treasuryService;

    /**
     * Punto de entrada del webhook de Wompi para CHARGE_COPAYMENT.
     *
     * La transacción @Transactional garantiza que si cualquier paso falla,
     * todos los cambios (llaves, tesorería, stock, estado) se revierten
     * atómicamente. El dispatcher reintentará si hay excepción no controlada.
     */
    @Override
    @Transactional
    public void handleWompiResult(UUID wompiTransactionId) {
        Objects.requireNonNull(wompiTransactionId, "wompiTransactionId no puede ser null");
        log.info("[COPAYMENT] Procesando webhook: wompiTxId={}", wompiTransactionId);

        WompiTransaction wompiTx = wompiTransactionRepository.findById(wompiTransactionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "[COPAYMENT] WompiTransaction no encontrada: " + wompiTransactionId));

        Copayment copayment = copaymentRepository
                .findByPurchaseReferenceIdWithDetails(wompiTx.getReference())
                .orElseThrow(() -> new IllegalArgumentException(
                        "[COPAYMENT] Copayment no encontrado para reference: " + wompiTx.getReference()));

        // Idempotencia: si ya fue procesado, no hacer nada
        if (copayment.getStatus() != CopaymentStatus.PENDING) {
            log.warn("[COPAYMENT] Ya procesado: copaymentId={}, status={}",
                    copayment.getId(), copayment.getStatus());
            return;
        }

        Purchase purchase = Objects.requireNonNull(copayment.getPurchase(),
                "La compra asociada al copago no puede ser null");
        WompiTransactionStatus status = wompiTx.getStatus();

        log.info("[COPAYMENT] Procesando copaymentId={}, wompiStatus={}", copayment.getId(), status);

        if (status == WompiTransactionStatus.APPROVED) {
            handleApproved(copayment, purchase, wompiTx);
        } else {
            handleFailed(copayment, purchase, status.name());
        }

        copaymentRepository.save(copayment);
        purchaseRepository.save(purchase);

        log.info("[COPAYMENT] Finalizado: copaymentId={}, nuevoStatus={}",
                copayment.getId(), copayment.getStatus());
    }

    // ─── Flujo APPROVED ──────────────────────────────────────────────────────────

    private void handleApproved(Copayment copayment, Purchase purchase, WompiTransaction wompiTx) {
        log.info("[COPAYMENT] APPROVED: copaymentId={}", copayment.getId());

        long keysUsed = copayment.getKeysUsed();
        long keysValueCents = copayment.getKeysValueCents();
        long cashAmountCents = copayment.getCashAmountCents();

        // 1. Confirmar llaves reservadas (blockedPurchaseKeys → 0, definitivamente gastadas)
        if (keysUsed > 0) {
            KeyWallet keyWallet = keyWalletRepository
                    .findByConsumerId(copayment.getConsumer().getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "[COPAYMENT] KeyWallet no encontrado: consumerId="
                                    + copayment.getConsumer().getId()));

            keyWallet.confirmReservedPurchaseKeys(keysUsed);
            keyWalletRepository.save(keyWallet);

            // Ledger inmutable: débito definitivo de llaves
            keyTransactionRepository.save(Objects.requireNonNull(
                    KeyTransaction.forCopaymentConfirm(
                            keyWallet, keysUsed, copayment.getId(), buildProductNames(purchase))));

            // Tesorería: KEYS_RESERVE → PAYOUTS_PENDING por el valor de las llaves
            treasuryService.convertKeysToPayoutPending(keysValueCents, copayment.getId());
        }

        // 2. Tesorería: efectivo (ya en Bancolombia vía Wompi) → PAYOUTS_PENDING
        treasuryService.moveCashToPayoutPending(cashAmountCents, copayment.getId());

        // 3. Retener comisión inmediatamente: PAYOUTS_PENDING → OPERATIONS
        // La venta está confirmada — el ingreso está ganado en este momento.
        // PAYOUTS_PENDING queda con el neto real del empresario (precio - comisión).
        long commissionCents = purchase.getCommissionCents();
        if (commissionCents > 0) {
            treasuryService.retainCommission(commissionCents, copayment.getId(), "COPAYMENT");
        }

        // 4. Entregar códigos de producto al comprador
        deliverProducts(purchase);

        // 4. Completar compra y copago
        purchase.markAsCompleted();
        copayment.setWompiTransaction(wompiTx);
        copayment.setStatus(CopaymentStatus.COMPLETED);

        log.info("[COPAYMENT] Completado: keysUsed={}, keysValueCents={}, cashAmountCents={}",
                keysUsed, keysValueCents, cashAmountCents);
    }

    // ─── Flujo FAILED / DECLINED / ERROR ─────────────────────────────────────────

    private void handleFailed(Copayment copayment, Purchase purchase, String reason) {
        log.info("[COPAYMENT] FAILED: copaymentId={}, reason={}", copayment.getId(), reason);

        long keysUsed = copayment.getKeysUsed();

        // 1. Devolver llaves reservadas al saldo disponible del usuario
        if (keysUsed > 0) {
            KeyWallet keyWallet = keyWalletRepository
                    .findByConsumerId(copayment.getConsumer().getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "[COPAYMENT] KeyWallet no encontrado: consumerId="
                                    + copayment.getConsumer().getId()));

            keyWallet.releasePurchaseKeys(keysUsed);
            keyWalletRepository.save(keyWallet);

            // Ledger inmutable: liberación de llaves bloqueadas
            keyTransactionRepository.save(Objects.requireNonNull(
                    KeyTransaction.forCopaymentRelease(keyWallet, keysUsed, copayment.getId())));
        }

        // 2. Devolver stock reservado al pool disponible
        releaseReservedStock(purchase);

        // 3. Marcar copago como fallido
        copayment.setStatus(CopaymentStatus.FAILED);
        copayment.setFailureReason(reason);
        if (keysUsed > 0) {
            copayment.setKeysRefundedAt(ZonedDateTime.now(ZoneOffset.UTC));
        }

        log.info("[COPAYMENT] Llaves devueltas y stock liberado: copaymentId={}", copayment.getId());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private void deliverProducts(Purchase purchase) {
        List<PurchaseItem> items = purchase.getItems();
        for (PurchaseItem item : items) {
            ProductStock stock = item.getAssignedProductStock();
            stock.markAsSold(item);
            productStockRepository.save(stock);
            item.setDeliveredCode(stock.getCode());
            item.setDeliveredAt(ZonedDateTime.now(ZoneOffset.UTC));
            item.setStatus(PurchaseItemStatus.DELIVERED);
        }
        log.debug("[COPAYMENT] {} código(s) entregado(s) para purchaseId={}", items.size(), purchase.getId());
    }

    private void releaseReservedStock(Purchase purchase) {
        for (PurchaseItem item : purchase.getItems()) {
            ProductStock stock = item.getAssignedProductStock();
            if (stock != null) {
                stock.markAsAvailable();
                productStockRepository.save(stock);
            }
        }
    }

    private String buildProductNames(Purchase purchase) {
        return purchase.getItems().stream()
                .map(i -> i.getProduct().getName())
                .distinct()
                .collect(Collectors.joining(", "));
    }

    // ─── Expiración de compras PENDING ───────────────────────────────────────────

    @Override
    @Transactional
    public void expireStale(int maxAgeMinutes) {
        ZonedDateTime cutoff = ZonedDateTime.now(ZoneOffset.UTC).minusMinutes(maxAgeMinutes);
        List<Copayment> stale = copaymentRepository.findExpiredPending(CopaymentStatus.PENDING, cutoff);

        if (stale.isEmpty()) return;

        log.info("[COPAYMENT-EXPIRY] Expirando {} copago(s) PENDING anteriores a {}", stale.size(), cutoff);

        for (Copayment copayment : stale) {
            try {
                Purchase purchase = Objects.requireNonNull(copayment.getPurchase());
                handleFailed(copayment, purchase, "PAYMENT_SESSION_EXPIRED");
                copaymentRepository.save(copayment);
                purchaseRepository.save(purchase);
                log.info("[COPAYMENT-EXPIRY] Expirado: copaymentId={}", copayment.getId());
            } catch (Exception e) {
                log.error("[COPAYMENT-EXPIRY] Error expirando copaymentId={}: {}",
                        copayment.getId(), e.getMessage(), e);
            }
        }
    }
}
