package com.verygana2.services.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
import com.verygana2.services.interfaces.finance.KeyLedgerService.Issuance;
import com.verygana2.services.interfaces.finance.TreasuryService;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Libro Mayor de Llaves (MP-12) contra H2 real: cubre las pruebas mínimas del contrato
 * (emisión, uso, vencimiento, reverso, saldo insuficiente, doble aplicación) y lo que
 * los lotes agregan: consumo por orden de vencimiento y vencimiento exacto.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:key-ledger-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        // V1__baseline.sql es MySQL puro: el esquema de prueba lo genera Hibernate.
        "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(KeyLedgerServiceImpl.class)
@DisplayName("KeyLedgerService — Libro Mayor y lotes de llaves (integración H2)")
class KeyLedgerServiceIntegrationTest {

    private static final ZonedDateTime TODAY = ZonedDateTime.of(2026, 10, 6, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final ZonedDateTime END_OF_OCTOBER = ZonedDateTime.of(2026, 11, 1, 5, 0, 0, 0, ZoneOffset.UTC);
    private static final ZonedDateTime END_OF_NOVEMBER = ZonedDateTime.of(2026, 12, 1, 5, 0, 0, 0, ZoneOffset.UTC);

    @Autowired private KeyLedgerServiceImpl service;
    @Autowired private EntityManager em;
    @Autowired private KeyTransactionRepository transactionRepository;
    @Autowired private KeyLotRepository lotRepository;
    @Autowired private KeyLotMovementRepository lotMovementRepository;

    @MockitoBean private TreasuryService treasuryService;
    @MockitoBean private Clock clock;

    private final AtomicReference<Instant> now = new AtomicReference<>(TODAY.toInstant());

    @BeforeEach
    void stubClock() {
        now.set(TODAY.toInstant());
        when(clock.instant()).thenAnswer(invocation -> now.get());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    // ─── Fixtures ─────────────────────────────────────────────────────────────

    private KeyWallet newWallet() {
        KeyWallet wallet = KeyWallet.createFor(TestEntities.persistConsumer(em));
        em.persist(wallet);
        em.flush();
        return wallet;
    }

    private void issuePurchase(KeyWallet wallet, long cents, ZonedDateTime expiresAt) {
        service.issue(wallet, new Issuance(KeyOriginType.AD, UUID.randomUUID(), "Interacción de prueba",
                cents, expiresAt, 0, expiresAt));
    }

    private List<KeyTransaction> ledgerOf(KeyWallet wallet) {
        return transactionRepository.findAll().stream()
                .filter(t -> t.getKeyWallet().getId().equals(wallet.getId()))
                .sorted(Comparator.comparing(KeyTransaction::getSequence))
                .toList();
    }

    private KeyLot lot(KeyWallet wallet, KeyPocket pocket, ZonedDateTime expiresAt) {
        return lotRepository.findByKeyWalletIdAndPocketAndExpiresAt(wallet.getId(), pocket, expiresAt).orElseThrow();
    }

    // ─── Emisión ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("emisión")
    class Issue {

        @Test
        @DisplayName("acredita cada bolsa con su asiento, su saldo anterior/posterior y su lote")
        void creditsBothPocketsWithBalancesAndLots() {
            KeyWallet wallet = newWallet();
            ZonedDateTime tomorrow = TODAY.plusDays(1);

            service.issue(wallet, new Issuance(KeyOriginType.GAME, UUID.randomUUID(), "Sesión de juego",
                    750, END_OF_OCTOBER, 250, tomorrow));

            assertThat(wallet.getPurchaseKeysCents()).isEqualTo(750);
            assertThat(wallet.getConnectivityKeysCents()).isEqualTo(250);

            List<KeyTransaction> ledger = ledgerOf(wallet);
            assertThat(ledger).hasSize(2);
            KeyTransaction purchase = ledger.get(0);
            assertThat(purchase.getSequence()).isEqualTo(1);
            assertThat(purchase.getType()).isEqualTo(KeyTransactionType.CREDIT_INTERACTION);
            assertThat(purchase.getOriginType()).isEqualTo(KeyOriginType.GAME);
            assertThat(purchase.getPurchaseKeysDeltaCents()).isEqualTo(750);
            assertThat(purchase.getConnectivityKeysDeltaCents()).isNull();
            assertThat(purchase.getPurchaseBalanceBeforeCents()).isZero();
            assertThat(purchase.getPurchaseBalanceAfterCents()).isEqualTo(750);
            KeyTransaction connectivity = ledger.get(1);
            assertThat(connectivity.getSequence()).isEqualTo(2);
            assertThat(connectivity.getConnectivityKeysDeltaCents()).isEqualTo(250);
            assertThat(connectivity.getConnectivityBalanceAfterCents()).isEqualTo(250);
            // El saldo de compra no cambió en el segundo asiento, pero queda registrado.
            assertThat(connectivity.getPurchaseBalanceAfterCents()).isEqualTo(750);

            assertThat(lot(wallet, KeyPocket.PURCHASE, END_OF_OCTOBER).getRemainingCents()).isEqualTo(750);
            assertThat(lot(wallet, KeyPocket.CONNECTIVITY, tomorrow).getRemainingCents()).isEqualTo(250);
        }

        @Test
        @DisplayName("dos emisiones con el mismo vencimiento comparten lote")
        void sameExpiryAccumulatesInOneLot() {
            KeyWallet wallet = newWallet();

            issuePurchase(wallet, 300, END_OF_OCTOBER);
            issuePurchase(wallet, 200, END_OF_OCTOBER);

            assertThat(lotRepository.findOpenByWalletAndPocket(wallet.getId(), KeyPocket.PURCHASE)).hasSize(1);
            assertThat(lot(wallet, KeyPocket.PURCHASE, END_OF_OCTOBER).getRemainingCents()).isEqualTo(500);
        }

        @Test
        @DisplayName("doble aplicación: repetir la misma interacción no acredita dos veces")
        void sameInteractionTwice_creditsOnce() {
            KeyWallet wallet = newWallet();
            Issuance issuance = new Issuance(KeyOriginType.SURVEY, UUID.randomUUID(), "Encuesta",
                    750, END_OF_OCTOBER, 250, TODAY.plusDays(1));

            service.issue(wallet, issuance);
            service.issue(wallet, issuance);

            assertThat(wallet.getPurchaseKeysCents()).isEqualTo(750);
            assertThat(wallet.getConnectivityKeysCents()).isEqualTo(250);
            assertThat(ledgerOf(wallet)).hasSize(2);
            assertThat(lot(wallet, KeyPocket.PURCHASE, END_OF_OCTOBER).getIssuedCents()).isEqualTo(750);
        }
    }

    // ─── Copago ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("copago: reserva, confirmación y liberación")
    class Copayment {

        @Test
        @DisplayName("uso: la reserva baja el disponible y el lote; la confirmación solo baja el bloqueado")
        void reserveThenConfirm() {
            KeyWallet wallet = newWallet();
            issuePurchase(wallet, 1_000, END_OF_OCTOBER);
            UUID copaymentId = UUID.randomUUID();

            KeyTransaction reserve = service.reserveForCopayment(wallet, 400, copaymentId);

            assertThat(wallet.getPurchaseKeysCents()).isEqualTo(600);
            assertThat(wallet.getBlockedPurchaseKeysCents()).isEqualTo(400);
            assertThat(reserve.getType()).isEqualTo(KeyTransactionType.RESERVE_COPAYMENT_PENDING);
            assertThat(reserve.getPurchaseKeysDeltaCents()).isEqualTo(-400);
            assertThat(reserve.getBlockedPurchaseDeltaCents()).isEqualTo(400);
            assertThat(reserve.getPurchaseBalanceBeforeCents()).isEqualTo(1_000);
            assertThat(reserve.getPurchaseBalanceAfterCents()).isEqualTo(600);
            assertThat(lot(wallet, KeyPocket.PURCHASE, END_OF_OCTOBER).getRemainingCents()).isEqualTo(600);

            KeyTransaction confirm = service.confirmCopayment(wallet, 400, copaymentId, "Bono de prueba");

            assertThat(wallet.getPurchaseKeysCents()).isEqualTo(600);
            assertThat(wallet.getBlockedPurchaseKeysCents()).isZero();
            assertThat(confirm.getType()).isEqualTo(KeyTransactionType.DEBIT_COPAYMENT);
            assertThat(confirm.getPurchaseKeysDeltaCents()).isNull();
            assertThat(confirm.getBlockedPurchaseDeltaCents()).isEqualTo(-400);
            assertThat(confirm.getPurchaseBalanceAfterCents()).isEqualTo(600);
            // La confirmación no vuelve a descontar del lote.
            assertThat(lot(wallet, KeyPocket.PURCHASE, END_OF_OCTOBER).getRemainingCents()).isEqualTo(600);
        }

        @Test
        @DisplayName("doble aplicación: reservar o confirmar dos veces el mismo copago se aplica una sola vez")
        void reserveAndConfirmAreIdempotent() {
            KeyWallet wallet = newWallet();
            issuePurchase(wallet, 1_000, END_OF_OCTOBER);
            UUID copaymentId = UUID.randomUUID();

            KeyTransaction firstReserve = service.reserveForCopayment(wallet, 400, copaymentId);
            KeyTransaction secondReserve = service.reserveForCopayment(wallet, 400, copaymentId);
            KeyTransaction firstConfirm = service.confirmCopayment(wallet, 400, copaymentId, "Bono");
            KeyTransaction secondConfirm = service.confirmCopayment(wallet, 400, copaymentId, "Bono");

            assertThat(secondReserve.getId()).isEqualTo(firstReserve.getId());
            assertThat(secondConfirm.getId()).isEqualTo(firstConfirm.getId());
            assertThat(wallet.getPurchaseKeysCents()).isEqualTo(600);
            assertThat(wallet.getBlockedPurchaseKeysCents()).isZero();
            assertThat(ledgerOf(wallet)).hasSize(3); // emisión + reserva + confirmación
        }

        @Test
        @DisplayName("pago fallido: la liberación devuelve las llaves a su lote y compensa a la reserva")
        void releaseGivesKeysBackToTheirLot() {
            KeyWallet wallet = newWallet();
            issuePurchase(wallet, 1_000, END_OF_OCTOBER);
            UUID copaymentId = UUID.randomUUID();
            KeyTransaction reserve = service.reserveForCopayment(wallet, 400, copaymentId);

            KeyTransaction release = service.releaseCopayment(wallet, 400, copaymentId);
            service.releaseCopayment(wallet, 400, copaymentId); // reintento del webhook

            assertThat(wallet.getPurchaseKeysCents()).isEqualTo(1_000);
            assertThat(wallet.getBlockedPurchaseKeysCents()).isZero();
            assertThat(release.getType()).isEqualTo(KeyTransactionType.RELEASE_COPAYMENT_CANCELLED);
            assertThat(release.getPurchaseKeysDeltaCents()).isEqualTo(400);
            assertThat(release.getBlockedPurchaseDeltaCents()).isEqualTo(-400);
            assertThat(release.getReverses().getId()).isEqualTo(reserve.getId());
            assertThat(lot(wallet, KeyPocket.PURCHASE, END_OF_OCTOBER).getRemainingCents()).isEqualTo(1_000);
            assertThat(ledgerOf(wallet)).hasSize(3); // emisión + reserva + una sola liberación
        }

        @Test
        @DisplayName("saldo insuficiente: no reserva ni deja asiento")
        void insufficientBalance_rejectsWithoutWriting() {
            KeyWallet wallet = newWallet();
            issuePurchase(wallet, 300, END_OF_OCTOBER);

            assertThatThrownBy(() -> service.reserveForCopayment(wallet, 301, UUID.randomUUID()))
                    .isInstanceOf(InsufficientFundsException.class);

            assertThat(wallet.getPurchaseKeysCents()).isEqualTo(300);
            assertThat(wallet.getBlockedPurchaseKeysCents()).isZero();
            assertThat(ledgerOf(wallet)).hasSize(1);
        }

        @Test
        @DisplayName("el gasto consume primero el lote que vence antes")
        void spendsEarliestExpiringLotFirst() {
            KeyWallet wallet = newWallet();
            // Se emiten en orden inverso para que el orden de consumo no sea el de llegada.
            issuePurchase(wallet, 500, END_OF_NOVEMBER);
            issuePurchase(wallet, 300, END_OF_OCTOBER);

            KeyTransaction reserve = service.reserveForCopayment(wallet, 400, UUID.randomUUID());

            assertThat(lot(wallet, KeyPocket.PURCHASE, END_OF_OCTOBER).getRemainingCents()).isZero();
            assertThat(lot(wallet, KeyPocket.PURCHASE, END_OF_NOVEMBER).getRemainingCents()).isEqualTo(400);
            assertThat(lotMovementRepository.findByTransactionId(reserve.getId()))
                    .extracting(KeyLotMovement::getDeltaCents)
                    .containsExactly(-300L, -100L);
        }

        @Test
        @DisplayName("reembolso: las llaves vuelven con vigencia, una sola vez por ítem, compensando al pago")
        void refundCreditsBackWithExpiryOncePerItem() {
            KeyWallet wallet = newWallet();
            issuePurchase(wallet, 1_000, END_OF_OCTOBER);
            UUID copaymentId = UUID.randomUUID();
            service.reserveForCopayment(wallet, 400, copaymentId);
            KeyTransaction payment = service.confirmCopayment(wallet, 400, copaymentId, "Bono");

            KeyTransaction refund = service.refundPurchaseItem(wallet, 400, copaymentId, 77L, END_OF_NOVEMBER);
            service.refundPurchaseItem(wallet, 400, copaymentId, 77L, END_OF_NOVEMBER);

            assertThat(wallet.getPurchaseKeysCents()).isEqualTo(1_000);
            assertThat(refund.getType()).isEqualTo(KeyTransactionType.CREDIT_COPAYMENT_REFUND);
            assertThat(refund.getOriginType()).isEqualTo(KeyOriginType.PURCHASE_ITEM_REFUND);
            assertThat(refund.getReverses().getId()).isEqualTo(payment.getId());
            assertThat(refund.getExpiredAt()).isEqualTo(END_OF_NOVEMBER);
            assertThat(lot(wallet, KeyPocket.PURCHASE, END_OF_NOVEMBER).getRemainingCents()).isEqualTo(400);
        }
    }

    // ─── Vencimiento ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("vencimiento")
    class Expiry {

        @Test
        @DisplayName("vence solo lo que queda del lote vencido: 300 ganadas, 200 gastadas y 50 nuevas → vencen 100")
        void expiresExactlyTheRemainderOfTheDueLot() {
            KeyWallet wallet = newWallet();
            issuePurchase(wallet, 300, END_OF_OCTOBER);
            service.spendForPetGame(wallet, 200, 12, "manzana", null);
            now.set(END_OF_OCTOBER.plusHours(2).toInstant());
            issuePurchase(wallet, 50, END_OF_NOVEMBER);

            long expired = service.expireDueLots(wallet.getId(), ZonedDateTime.now(clock));

            assertThat(expired).isEqualTo(100);
            assertThat(wallet.getPurchaseKeysCents()).isEqualTo(50);
            assertThat(lot(wallet, KeyPocket.PURCHASE, END_OF_OCTOBER).getRemainingCents()).isZero();
            assertThat(lot(wallet, KeyPocket.PURCHASE, END_OF_NOVEMBER).getRemainingCents()).isEqualTo(50);

            List<KeyTransaction> ledger = ledgerOf(wallet);
            KeyTransaction expiry = ledger.get(ledger.size() - 1);
            assertThat(expiry.getType()).isEqualTo(KeyTransactionType.EXPIRED);
            assertThat(expiry.getPurchaseKeysDeltaCents()).isEqualTo(-100);
            assertThat(expiry.getPurchaseBalanceBeforeCents()).isEqualTo(150);
            assertThat(expiry.getPurchaseBalanceAfterCents()).isEqualTo(50);
            // El movimiento identifica el lote de origen (6.22.7).
            assertThat(lotMovementRepository.findByTransactionId(expiry.getId()))
                    .singleElement()
                    .satisfies(m -> {
                        assertThat(m.getDeltaCents()).isEqualTo(-100);
                        assertThat(m.getKeyLot().getExpiresAt()).isEqualTo(END_OF_OCTOBER);
                    });
            verify(treasuryService).moveExpiredKeysToFortification(eq(100L), eq(expiry.getReferenceId()));
        }

        @Test
        @DisplayName("un lote ya gastado por completo no vence nada ni mueve tesorería")
        void fullySpentLot_expiresNothing() {
            KeyWallet wallet = newWallet();
            issuePurchase(wallet, 300, END_OF_OCTOBER);
            service.spendForPetGame(wallet, 300, 12, "manzana", null);
            now.set(END_OF_OCTOBER.plusHours(2).toInstant());

            long expired = service.expireDueLots(wallet.getId(), ZonedDateTime.now(clock));

            assertThat(expired).isZero();
            assertThat(ledgerOf(wallet)).hasSize(2); // emisión + gasto, sin asiento de vencimiento
            verify(treasuryService, never()).moveExpiredKeysToFortification(any(), any());
        }

        @Test
        @DisplayName("correr el vencimiento dos veces no expira dos veces")
        void runningTwice_expiresOnce() {
            KeyWallet wallet = newWallet();
            issuePurchase(wallet, 300, END_OF_OCTOBER);
            now.set(END_OF_OCTOBER.plusHours(2).toInstant());

            long first = service.expireDueLots(wallet.getId(), ZonedDateTime.now(clock));
            long second = service.expireDueLots(wallet.getId(), ZonedDateTime.now(clock));

            assertThat(first).isEqualTo(300);
            assertThat(second).isZero();
            assertThat(wallet.getPurchaseKeysCents()).isZero();
        }

        @Test
        @DisplayName("intento de uso: una llave vencida que el job aún no extinguió no se puede gastar")
        void expiredButNotYetExtinguishedKeysCannotBeSpent() {
            KeyWallet wallet = newWallet();
            issuePurchase(wallet, 300, END_OF_OCTOBER);
            now.set(END_OF_OCTOBER.plusMinutes(1).toInstant());
            issuePurchase(wallet, 50, END_OF_NOVEMBER);

            assertThat(service.spendablePurchaseCents(wallet)).isEqualTo(50);
            assertThatThrownBy(() -> service.reserveForCopayment(wallet, 51, UUID.randomUUID()))
                    .isInstanceOf(InsufficientFundsException.class);
        }

        @Test
        @DisplayName("las llaves reservadas en un copago en curso no vencen")
        void reservedKeysAreNotExpired() {
            KeyWallet wallet = newWallet();
            issuePurchase(wallet, 300, END_OF_OCTOBER);
            service.reserveForCopayment(wallet, 200, UUID.randomUUID());
            now.set(END_OF_OCTOBER.plusHours(2).toInstant());

            long expired = service.expireDueLots(wallet.getId(), ZonedDateTime.now(clock));

            assertThat(expired).isEqualTo(100);
            assertThat(wallet.getBlockedPurchaseKeysCents()).isEqualTo(200);
        }
    }

    // ─── Saldos anteriores a MP-12 y conciliación ─────────────────────────────

    @Test
    @DisplayName("un saldo anterior al Libro Mayor (sin lote) se puede gastar y no vence")
    void legacyBalanceWithoutLot_isSpendableAndNeverExpires() {
        KeyWallet wallet = newWallet();
        wallet.setPurchaseKeysCents(500L);
        em.flush();

        KeyTransaction spend = service.spendForPetGame(wallet, 300, 12, "manzana", null);
        now.set(END_OF_NOVEMBER.plusDays(1).toInstant());
        long expired = service.expireDueLots(wallet.getId(), ZonedDateTime.now(clock));

        assertThat(wallet.getPurchaseKeysCents()).isEqualTo(200);
        assertThat(lotMovementRepository.findByTransactionId(spend.getId())).isEmpty();
        assertThat(expired).isZero();
    }

    @Test
    @DisplayName("conciliación: limpia mientras todo pasa por el libro, y detecta un saldo tocado por fuera")
    void reconcileDetectsBalanceChangedOutsideTheLedger() {
        KeyWallet wallet = newWallet();
        issuePurchase(wallet, 300, END_OF_OCTOBER);
        service.reserveForCopayment(wallet, 100, UUID.randomUUID());
        em.flush();

        assertThat(service.reconcile().isClean()).isTrue();

        wallet.setPurchaseKeysCents(50L); // por debajo del último asiento y de sus lotes
        em.flush();

        assertThat(service.reconcile().walletsOutOfSyncWithLedger()).containsExactly(wallet.getId());
        assertThat(service.reconcile().walletsWithLotsAboveBalance()).containsExactly(wallet.getId());
    }
}
