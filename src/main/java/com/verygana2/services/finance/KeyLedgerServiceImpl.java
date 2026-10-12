package com.verygana2.services.finance;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.exceptions.InsufficientFundsException;
import com.verygana2.models.enums.finance.KeyOriginType;
import com.verygana2.models.enums.finance.KeyPocket;
import com.verygana2.models.enums.finance.KeyTransactionType;
import com.verygana2.models.finance.KeyLot;
import com.verygana2.models.finance.KeyLotMovement;
import com.verygana2.models.finance.KeyTransaction;
import com.verygana2.models.finance.KeyWallet;
import com.verygana2.repositories.finance.KeyLotMovementRepository;
import com.verygana2.repositories.finance.KeyLotRepository;
import com.verygana2.repositories.finance.KeyTransactionRepository;
import com.verygana2.repositories.finance.KeyWalletRepository;
import com.verygana2.services.interfaces.finance.KeyLedgerService;
import com.verygana2.services.interfaces.finance.TreasuryService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeyLedgerServiceImpl implements KeyLedgerService {

    private static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z");
    private static final int REASON_MAX_LENGTH = 255;

    private final KeyWalletRepository keyWalletRepository;
    private final KeyTransactionRepository keyTransactionRepository;
    private final KeyLotRepository keyLotRepository;
    private final KeyLotMovementRepository keyLotMovementRepository;
    private final TreasuryService treasuryService;
    private final Clock clock;

    // ─── Emisión ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void issue(KeyWallet wallet, Issuance issuance) {
        issueToPocket(wallet, issuance, KeyPocket.PURCHASE,
                issuance.purchaseCents(), issuance.purchaseExpiresAt());
        issueToPocket(wallet, issuance, KeyPocket.CONNECTIVITY,
                issuance.connectivityCents(), issuance.connectivityExpiresAt());
    }

    /** Un asiento por bolsa: cada una tiene su propio vencimiento. */
    private void issueToPocket(KeyWallet wallet, Issuance issuance, KeyPocket pocket, long cents,
            ZonedDateTime expiresAt) {
        if (cents <= 0) {
            return;
        }
        String key = "ISSUE:" + issuance.origin() + ":" + issuance.referenceId() + ":" + pocket;
        if (keyTransactionRepository.findByIdempotencyKey(key).isPresent()) {
            return;
        }
        Draft draft = new Draft(KeyTransactionType.CREDIT_INTERACTION, issuance.origin(),
                issuance.referenceId(), issuance.reason(), key, expiresAt, null, null);
        KeyTransaction entry = append(wallet, draft, w -> {
            if (pocket == KeyPocket.PURCHASE) {
                w.creditKeysCents(cents, 0);
            } else {
                w.creditKeysCents(0, cents);
            }
        });
        creditLot(entry, pocket, expiresAt, cents);
    }

    // ─── Copago ───────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public long spendablePurchaseCents(KeyWallet wallet) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        long expiredNotYetExtinguished = keyLotRepository
                .findOpenByWalletAndPocket(wallet.getId(), KeyPocket.PURCHASE).stream()
                .filter(lot -> lot.isExpiredAt(now))
                .mapToLong(KeyLot::getRemainingCents)
                .sum();
        return Math.max(0, wallet.getPurchaseKeysCents() - expiredNotYetExtinguished);
    }

    @Override
    @Transactional
    public KeyTransaction reserveForCopayment(KeyWallet wallet, long cents, UUID copaymentId) {
        String key = reserveKey(copaymentId);
        Optional<KeyTransaction> previous = keyTransactionRepository.findByIdempotencyKey(key);
        if (previous.isPresent()) {
            return previous.get();
        }
        requireSpendable(wallet, cents);
        Draft draft = new Draft(KeyTransactionType.RESERVE_COPAYMENT_PENDING, KeyOriginType.COPAYMENT,
                copaymentId, "Reserva de llaves para copago en proceso", key, null, null, null);
        KeyTransaction entry = append(wallet, draft, w -> w.reservePurchaseKeysCents(cents));
        consumePurchaseLots(entry, cents);
        return entry;
    }

    @Override
    @Transactional
    public KeyTransaction confirmCopayment(KeyWallet wallet, long cents, UUID copaymentId, String productNames) {
        String key = confirmKey(copaymentId);
        Optional<KeyTransaction> previous = keyTransactionRepository.findByIdempotencyKey(key);
        if (previous.isPresent()) {
            return previous.get();
        }
        // Los lotes no se tocan: ya se descontaron al reservar.
        Draft draft = new Draft(KeyTransactionType.DEBIT_COPAYMENT, KeyOriginType.COPAYMENT,
                copaymentId, "Pago con llaves: " + productNames, key, null, null, null);
        return append(wallet, draft, w -> w.confirmReservedPurchaseKeysCents(cents));
    }

    @Override
    @Transactional
    public KeyTransaction releaseCopayment(KeyWallet wallet, long cents, UUID copaymentId) {
        String key = "RELEASE:COPAYMENT:" + copaymentId;
        Optional<KeyTransaction> previous = keyTransactionRepository.findByIdempotencyKey(key);
        if (previous.isPresent()) {
            return previous.get();
        }
        // Un copago reservado antes de MP-12 no tiene asiento de reserva: sus llaves
        // vuelven al saldo sin lote, como estaban.
        KeyTransaction reserve = keyTransactionRepository.findByIdempotencyKey(reserveKey(copaymentId)).orElse(null);
        Draft draft = new Draft(KeyTransactionType.RELEASE_COPAYMENT_CANCELLED, KeyOriginType.COPAYMENT,
                copaymentId, "Devolución de llaves: copago cancelado o rechazado", key, null, reserve, null);
        KeyTransaction entry = append(wallet, draft, w -> w.releasePurchaseKeysCents(cents));

        if (reserve != null) {
            // Vuelven a los mismos lotes de donde salieron, con su vencimiento original.
            for (KeyLotMovement taken : keyLotMovementRepository.findByTransactionId(reserve.getId())) {
                KeyLot lot = taken.getKeyLot();
                long back = -taken.getDeltaCents();
                lot.giveBack(back);
                keyLotRepository.save(lot);
                keyLotMovementRepository.save(KeyLotMovement.of(entry, lot, back));
            }
        }
        return entry;
    }

    @Override
    @Transactional
    public KeyTransaction refundPurchaseItem(KeyWallet wallet, long cents, UUID copaymentId, Long purchaseItemId,
            ZonedDateTime expiresAt) {
        String key = "REFUND:ITEM:" + purchaseItemId;
        Optional<KeyTransaction> previous = keyTransactionRepository.findByIdempotencyKey(key);
        if (previous.isPresent()) {
            return previous.get();
        }
        KeyTransaction payment = keyTransactionRepository.findByIdempotencyKey(confirmKey(copaymentId)).orElse(null);
        Draft draft = new Draft(KeyTransactionType.CREDIT_COPAYMENT_REFUND, KeyOriginType.PURCHASE_ITEM_REFUND,
                copaymentId, "Reembolso de llaves: ítem de compra reembolsado", key, expiresAt, payment, null);
        KeyTransaction entry = append(wallet, draft, w -> w.creditKeysCents(cents, 0));
        creditLot(entry, KeyPocket.PURCHASE, expiresAt, cents);
        return entry;
    }

    // ─── Juego de mascotas ────────────────────────────────────────────────────

    @Override
    @Transactional
    public KeyTransaction spendForPetGame(KeyWallet wallet, long cents, Integer itemId, String itemName,
            Long petCatalogItemId) {
        requireSpendable(wallet, cents);
        // El juego no manda un identificador de la compra, así que no hay con qué
        // reconocer un reintento: cada llamada es un gasto nuevo.
        UUID referenceId = UUID.randomUUID();
        Draft draft = new Draft(KeyTransactionType.DEBIT_PET_GAME, KeyOriginType.PET_GAME, referenceId,
                "Mascota virtual: " + itemName + " (itemId=" + itemId + ")", "PET_GAME:" + referenceId,
                null, null, petCatalogItemId);
        KeyTransaction entry = append(wallet, draft, w -> w.spendPurchaseKeysCents(cents));
        consumePurchaseLots(entry, cents);
        return entry;
    }

    // ─── Vencimiento ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public long expireDueLots(UUID walletId, ZonedDateTime now) {
        KeyWallet wallet = keyWalletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new EntityNotFoundException("KeyWallet " + walletId + " not found"));

        // Se leen con la billetera ya bloqueada: un gasto que entró justo antes ya
        // descontó sus lotes.
        List<KeyLot> due = keyLotRepository.findDueByWallet(walletId, now);
        long purchaseDue = remainingOf(due, KeyPocket.PURCHASE);
        long connectivityDue = remainingOf(due, KeyPocket.CONNECTIVITY);

        // Los lotes nunca deberían sumar más que el saldo; si pasa, no se expira de más.
        long purchaseCents = Math.min(purchaseDue, wallet.getPurchaseKeysCents());
        long connectivityCents = Math.min(connectivityDue, wallet.getConnectivityKeysCents());
        if (purchaseCents != purchaseDue || connectivityCents != connectivityDue) {
            log.error("[KEY-LEDGER] Lotes vencidos por encima del saldo en wallet {}: compra {}>{}, conectividad {}>{}",
                    walletId, purchaseDue, wallet.getPurchaseKeysCents(),
                    connectivityDue, wallet.getConnectivityKeysCents());
        }

        long totalCents = purchaseCents + connectivityCents;
        KeyTransaction entry = null;
        UUID batchId = UUID.randomUUID();
        if (totalCents > 0) {
            String period = now.withZoneSameInstant(COLOMBIA).format(PERIOD_FMT);
            Draft draft = new Draft(KeyTransactionType.EXPIRED, KeyOriginType.EXPIRY, batchId,
                    "Vencimiento de llaves - período " + period, "EXPIRY:" + batchId, null, null, null);
            entry = append(wallet, draft, w -> {
                if (purchaseCents > 0) {
                    w.expirePurchaseKeysCents(purchaseCents);
                }
                if (connectivityCents > 0) {
                    w.expireConnectivityKeysCents(connectivityCents);
                }
            });
        }

        for (KeyLot lot : due) {
            long extinguished = lot.getRemainingCents();
            lot.consume(extinguished);
            keyLotRepository.save(lot);
            if (entry != null) {
                keyLotMovementRepository.save(KeyLotMovement.of(entry, lot, -extinguished));
            }
        }

        if (totalCents > 0) {
            // El beneficio se extingue; lo que se reasigna es su respaldo (6.22.7).
            treasuryService.moveExpiredKeysToFortification(totalCents, batchId);
        }
        return totalCents;
    }

    // ─── Conciliación ─────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public ReconciliationResult reconcile() {
        ReconciliationResult result = new ReconciliationResult(
                keyWalletRepository.findIdsOutOfSyncWithLedger(),
                keyLotRepository.findWalletIdsWithLotsAboveBalance());
        if (result.isClean()) {
            log.info("[KEY-LEDGER-RECONCILIATION] Libro de llaves conciliado sin descuadres");
        } else {
            result.walletsOutOfSyncWithLedger().forEach(id -> log.error(
                    "[KEY-LEDGER-RECONCILIATION] DESCUADRE: el saldo de la wallet {} no coincide con su último asiento", id));
            result.walletsWithLotsAboveBalance().forEach(id -> log.error(
                    "[KEY-LEDGER-RECONCILIATION] DESCUADRE: los lotes de la wallet {} suman más que su saldo", id));
        }
        return result;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private record Draft(
            KeyTransactionType type,
            KeyOriginType origin,
            UUID referenceId,
            String reason,
            String idempotencyKey,
            ZonedDateTime expiresAt,
            KeyTransaction reverses,
            Long petCatalogItemId) {
    }

    /**
     * Aplica el efecto sobre la billetera y registra el asiento con los deltas que ese
     * efecto produjo de verdad. Derivarlos de la billetera, en vez de recibirlos, es lo
     * que impide que el libro diga una cosa y el saldo otra.
     */
    private KeyTransaction append(KeyWallet wallet, Draft draft, Consumer<KeyWallet> effect) {
        long purchaseBefore = wallet.getPurchaseKeysCents();
        long connectivityBefore = wallet.getConnectivityKeysCents();
        long blockedBefore = wallet.getBlockedPurchaseKeysCents();

        effect.accept(wallet);

        long purchaseAfter = wallet.getPurchaseKeysCents();
        long connectivityAfter = wallet.getConnectivityKeysCents();

        KeyTransaction entry = KeyTransaction.builder()
                .keyWallet(wallet)
                .sequence(wallet.nextLedgerSequence())
                .type(draft.type())
                .originType(draft.origin())
                .purchaseKeysDeltaCents(nullIfZero(purchaseAfter - purchaseBefore))
                .connectivityKeysDeltaCents(nullIfZero(connectivityAfter - connectivityBefore))
                .blockedPurchaseDeltaCents(nullIfZero(wallet.getBlockedPurchaseKeysCents() - blockedBefore))
                .purchaseBalanceBeforeCents(purchaseBefore)
                .purchaseBalanceAfterCents(purchaseAfter)
                .connectivityBalanceBeforeCents(connectivityBefore)
                .connectivityBalanceAfterCents(connectivityAfter)
                .petCatalogItemId(draft.petCatalogItemId())
                .reason(truncate(draft.reason()))
                .referenceId(draft.referenceId())
                .reverses(draft.reverses())
                .idempotencyKey(draft.idempotencyKey())
                .expiredAt(draft.expiresAt())
                .build();

        keyWalletRepository.save(wallet);
        return keyTransactionRepository.save(entry);
    }

    private void creditLot(KeyTransaction entry, KeyPocket pocket, ZonedDateTime expiresAt, long cents) {
        KeyWallet wallet = entry.getKeyWallet();
        // La BD guarda microsegundos: sin truncar, el lote recién creado no se
        // encontraría en la siguiente emisión con la misma fecha.
        ZonedDateTime lotExpiry = expiresAt.truncatedTo(ChronoUnit.MICROS);
        KeyLot lot = keyLotRepository.findByKeyWalletIdAndPocketAndExpiresAt(wallet.getId(), pocket, lotExpiry)
                .orElseGet(() -> KeyLot.open(wallet, pocket, lotExpiry));
        lot.issue(cents);
        lot = keyLotRepository.save(lot);
        keyLotMovementRepository.save(KeyLotMovement.of(entry, lot, cents));
    }

    /**
     * Descuenta de los lotes vigentes, primero el que vence antes. Lo que no alcance a
     * cubrirse con lotes es saldo anterior a MP-12, que no tiene lote ni vencimiento.
     */
    private void consumePurchaseLots(KeyTransaction entry, long cents) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        long pending = cents;
        for (KeyLot lot : keyLotRepository.findOpenByWalletAndPocket(entry.getKeyWallet().getId(), KeyPocket.PURCHASE)) {
            if (pending == 0) {
                break;
            }
            if (lot.isExpiredAt(now)) {
                continue;
            }
            long taken = Math.min(pending, lot.getRemainingCents());
            lot.consume(taken);
            keyLotRepository.save(lot);
            keyLotMovementRepository.save(KeyLotMovement.of(entry, lot, -taken));
            pending -= taken;
        }
    }

    private void requireSpendable(KeyWallet wallet, long cents) {
        long spendable = spendablePurchaseCents(wallet);
        if (cents > spendable) {
            throw new InsufficientFundsException(
                    "Insufficient purchase keys. Spendable cents: " + spendable + ", required: " + cents);
        }
    }

    private static long remainingOf(List<KeyLot> lots, KeyPocket pocket) {
        return lots.stream().filter(lot -> lot.getPocket() == pocket).mapToLong(KeyLot::getRemainingCents).sum();
    }

    private static String reserveKey(UUID copaymentId) {
        return "RESERVE:COPAYMENT:" + copaymentId;
    }

    private static String confirmKey(UUID copaymentId) {
        return "CONFIRM:COPAYMENT:" + copaymentId;
    }

    private static Long nullIfZero(long delta) {
        return delta == 0 ? null : delta;
    }

    private static String truncate(String reason) {
        return reason.length() <= REASON_MAX_LENGTH ? reason : reason.substring(0, REASON_MAX_LENGTH);
    }
}
