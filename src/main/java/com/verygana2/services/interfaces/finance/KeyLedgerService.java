package com.verygana2.services.interfaces.finance;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import com.verygana2.models.enums.finance.KeyOriginType;
import com.verygana2.models.finance.KeyTransaction;
import com.verygana2.models.finance.KeyWallet;

/**
 * Único punto que mueve llaves: billetera, lotes y Libro Mayor cambian juntos o no cambian.
 *
 * Todo método que recibe una {@link KeyWallet} exige que el llamador la haya leído con
 * bloqueo ({@code KeyWalletService#getByConsumerIdForUpdate}) dentro de su transacción.
 * Las operaciones son idempotentes por operación de origen: repetirlas devuelve el
 * asiento ya registrado sin volver a aplicarlo.
 */
public interface KeyLedgerService {

    /** Llaves que una interacción acredita, repartidas entre las dos bolsas con su vencimiento. */
    record Issuance(
            KeyOriginType origin,
            UUID referenceId,
            String reason,
            long purchaseCents,
            ZonedDateTime purchaseExpiresAt,
            long connectivityCents,
            ZonedDateTime connectivityExpiresAt) {
    }

    record ReconciliationResult(List<UUID> walletsOutOfSyncWithLedger, List<UUID> walletsWithLotsAboveBalance) {
        public boolean isClean() {
            return walletsOutOfSyncWithLedger.isEmpty() && walletsWithLotsAboveBalance.isEmpty();
        }
    }

    void issue(KeyWallet wallet, Issuance issuance);

    /**
     * Llaves de compra que se pueden gastar ahora: el saldo disponible menos lo que ya
     * venció y el job todavía no ha extinguido.
     */
    long spendablePurchaseCents(KeyWallet wallet);

    KeyTransaction reserveForCopayment(KeyWallet wallet, long cents, UUID copaymentId);

    KeyTransaction confirmCopayment(KeyWallet wallet, long cents, UUID copaymentId, String productNames);

    KeyTransaction releaseCopayment(KeyWallet wallet, long cents, UUID copaymentId);

    /** @param expiresAt vigencia de las llaves devueltas: no vuelven sin fecha de vencimiento. */
    KeyTransaction refundPurchaseItem(KeyWallet wallet, long cents, UUID copaymentId, Long purchaseItemId,
            ZonedDateTime expiresAt);

    KeyTransaction spendForPetGame(KeyWallet wallet, long cents, Integer itemId, String itemName,
            Long petCatalogItemId);

    /**
     * Extingue lo que queda de los lotes vencidos de una billetera y mueve su respaldo a
     * FORTIFICATION, todo en una transacción propia de esa billetera.
     *
     * @return centavos de llaves vencidas.
     */
    long expireDueLots(UUID walletId, ZonedDateTime now);

    ReconciliationResult reconcile();
}
