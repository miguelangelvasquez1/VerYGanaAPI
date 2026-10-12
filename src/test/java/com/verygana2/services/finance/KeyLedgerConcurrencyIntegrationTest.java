package com.verygana2.services.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.verygana2.models.enums.finance.KeyOriginType;
import com.verygana2.models.enums.finance.KeyPocket;
import com.verygana2.models.finance.KeyTransaction;
import com.verygana2.models.finance.KeyWallet;
import com.verygana2.repositories.finance.KeyLotRepository;
import com.verygana2.repositories.finance.KeyTransactionRepository;
import com.verygana2.repositories.finance.KeyWalletRepository;
import com.verygana2.services.interfaces.finance.KeyLedgerService.Issuance;
import com.verygana2.services.interfaces.finance.TreasuryService;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Reservas simultáneas de llaves del mismo consumidor a través del Libro Mayor, cada una
 * en su propia transacción, contra H2 real (Contrato B 7.9: "saldo concurrentemente
 * reservado" y "doble aplicación"). El bloqueo de la billetera las pone en fila: lo que
 * aquí se comprueba es que, en fila, libro, lotes y saldo siguen cuadrando.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:key-ledger-concurrency-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "CASE_INSENSITIVE_IDENTIFIERS=TRUE;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.hikari.maximum-pool-size=16"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(KeyLedgerServiceImpl.class)
@DisplayName("KeyLedgerService — reservas concurrentes del mismo consumidor (integración H2)")
class KeyLedgerConcurrencyIntegrationTest {

    private static final long RESERVE_CENTS = 1_000L;
    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 10, 7, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final ZonedDateTime END_OF_OCTOBER = ZonedDateTime.of(2026, 11, 1, 5, 0, 0, 0, ZoneOffset.UTC);

    @Autowired private KeyLedgerServiceImpl service;
    @Autowired private EntityManager em;
    @Autowired private KeyWalletRepository keyWalletRepository;
    @Autowired private KeyTransactionRepository keyTransactionRepository;
    @Autowired private KeyLotRepository keyLotRepository;
    @Autowired private PlatformTransactionManager txManager;

    @MockitoBean private TreasuryService treasuryService;
    @MockitoBean private Clock clock;

    @BeforeEach
    void stubClock() {
        when(clock.instant()).thenReturn(NOW.toInstant());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("12 copagos simultáneos con llaves para 3: se reservan exactamente 3 y el libro cuadra")
    void concurrentReservations_neverReserveMoreThanTheBalance() throws Exception {
        Long consumerId = seedWalletWithIssuedKeys(3 * RESERVE_CENTS);

        int reserved = runConcurrently(12, () -> reserve(consumerId, UUID.randomUUID()));

        assertThat(reserved).isEqualTo(3);
        KeyWallet wallet = wallet(consumerId);
        assertThat(wallet.getPurchaseKeysCents()).isZero();
        assertThat(wallet.getBlockedPurchaseKeysCents()).isEqualTo(3 * RESERVE_CENTS);
        assertThat(keyLotRepository.findOpenByWalletAndPocket(wallet.getId(), KeyPocket.PURCHASE)).isEmpty();

        // Emisión + 3 reservas: secuencia sin huecos ni repetidos, y saldos encadenados.
        List<KeyTransaction> ledger = ledgerOf(wallet);
        assertThat(ledger).extracting(KeyTransaction::getSequence).containsExactly(1L, 2L, 3L, 4L);
        for (int i = 1; i < ledger.size(); i++) {
            assertThat(ledger.get(i).getPurchaseBalanceBeforeCents())
                    .isEqualTo(ledger.get(i - 1).getPurchaseBalanceAfterCents());
        }
        assertThat(ledger.get(ledger.size() - 1).getPurchaseBalanceAfterCents()).isZero();
        assertThat(service.reconcile().isClean()).isTrue();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("el mismo copago reservado 8 veces a la vez se aplica una sola vez")
    void sameCopaymentReservedConcurrently_appliesOnce() throws Exception {
        Long consumerId = seedWalletWithIssuedKeys(10 * RESERVE_CENTS);
        UUID copaymentId = UUID.randomUUID();

        int answered = runConcurrently(8, () -> reserve(consumerId, copaymentId));

        // Todas reciben respuesta (el asiento ya existente), pero el saldo baja una sola vez.
        assertThat(answered).isEqualTo(8);
        KeyWallet wallet = wallet(consumerId);
        assertThat(wallet.getPurchaseKeysCents()).isEqualTo(9 * RESERVE_CENTS);
        assertThat(wallet.getBlockedPurchaseKeysCents()).isEqualTo(RESERVE_CENTS);
        assertThat(ledgerOf(wallet)).hasSize(2); // emisión + una reserva
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Long seedWalletWithIssuedKeys(long purchaseCents) {
        return new TransactionTemplate(txManager).execute(s -> {
            KeyWallet wallet = KeyWallet.createFor(TestEntities.persistConsumer(em));
            em.persist(wallet);
            em.flush();
            service.issue(wallet, new Issuance(KeyOriginType.AD, UUID.randomUUID(), "Interacción de prueba",
                    purchaseCents, END_OF_OCTOBER, 0, END_OF_OCTOBER));
            return wallet.getConsumer().getId();
        });
    }

    /**
     * Lo que hace una compra (PurchaseServiceImpl.createPurchase): bloquear la billetera,
     * mirar el saldo gastable y reservar. 1 si reservó, 0 si no había saldo.
     *
     * El saldo se consulta antes, igual que allá: reserveForCopayment lanza si no alcanza,
     * y esa excepción deja la transacción marcada para rollback aunque se atrape.
     */
    private int reserve(Long consumerId, UUID copaymentId) {
        KeyWallet wallet = keyWalletRepository.findByConsumerIdForUpdate(consumerId).orElseThrow();
        boolean alreadyReserved = keyTransactionRepository
                .findByIdempotencyKey("RESERVE:COPAYMENT:" + copaymentId).isPresent();
        if (!alreadyReserved && service.spendablePurchaseCents(wallet) < RESERVE_CENTS) {
            return 0;
        }
        service.reserveForCopayment(wallet, RESERVE_CENTS, copaymentId);
        return 1;
    }

    private int runConcurrently(int threads, Supplier<Integer> work) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<Integer> task = () -> {
                    start.await();
                    return new TransactionTemplate(txManager).execute(s -> work.get());
                };
                futures.add(pool.submit(task));
            }
            start.countDown();
            int total = 0;
            for (Future<Integer> future : futures) {
                total += future.get(30, TimeUnit.SECONDS);
            }
            return total;
        } finally {
            pool.shutdownNow();
        }
    }

    private KeyWallet wallet(Long consumerId) {
        em.clear();
        return keyWalletRepository.findByConsumerId(consumerId).orElseThrow();
    }

    private List<KeyTransaction> ledgerOf(KeyWallet wallet) {
        return keyTransactionRepository.findAll().stream()
                .filter(t -> t.getKeyWallet().getId().equals(wallet.getId()))
                .sorted(Comparator.comparing(KeyTransaction::getSequence))
                .toList();
    }
}
