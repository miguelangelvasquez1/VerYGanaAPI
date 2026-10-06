package com.verygana2.repositories.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.verygana2.models.enums.finance.TreasuryAccountCode;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * N gastos de llaves a la vez contra KEYS_RESERVE, cada uno en su propia transacción
 * (igual que en producción vía el proxy {@code @Transactional} de
 * {@code spendKeysForPetGame}). Se ejercita el par
 * {@link TreasuryAccountRepository#debitIfCovered} + {@link TreasuryAccountRepository#credit}
 * directamente contra H2 real.
 *
 * <p>Antes el gasto tomaba {@code SELECT … FOR UPDATE} sobre la cuenta y escribía el
 * saldo calculado en memoria. Con el UPDATE atómico condicionado
 * ({@code WHERE balanceCents >= monto}) la base serializa los débitos sobre la fila:
 * ninguno se pierde, y cuando la reserva no alcanza para todos, los que sobran
 * afectan 0 filas en vez de dejarla en negativo.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:treasury-concurrency-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "CASE_INSENSITIVE_IDENTIFIERS=TRUE;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.hikari.maximum-pool-size=16"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("TreasuryAccountRepository — gastos de llaves concurrentes (integración H2)")
class TreasuryAccountConcurrencyIntegrationTest {

    private static final long SPEND_CENTS = 1_000L;

    @Autowired private EntityManager em;
    @Autowired private TreasuryAccountRepository treasuryAccountRepository;
    @Autowired private PlatformTransactionManager txManager;

    /** Los tests corren sin transacción envolvente, así que nada revierte solo. */
    @AfterEach
    void cleanUp() {
        new TransactionTemplate(txManager).executeWithoutResult(s -> treasuryAccountRepository.deleteAllInBatch());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("24 gastos simultáneos con reserva de sobra: el saldo final es exacto y ninguno se pierde")
    void concurrentSpends_noneIsLost() throws Exception {
        seed(100 * SPEND_CENTS);

        int succeeded = spendConcurrently(24);

        assertThat(succeeded).isEqualTo(24);
        assertThat(balance(TreasuryAccountCode.KEYS_RESERVE)).isEqualTo(76 * SPEND_CENTS);
        assertThat(balance(TreasuryAccountCode.OPERATIONS)).isEqualTo(24 * SPEND_CENTS);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("24 gastos simultáneos con reserva para 5: prosperan exactamente 5 y la reserva queda en 0, no en negativo")
    void concurrentSpends_neverOverdrawTheReserve() throws Exception {
        seed(5 * SPEND_CENTS);

        int succeeded = spendConcurrently(24);

        assertThat(succeeded).isEqualTo(5);
        assertThat(balance(TreasuryAccountCode.KEYS_RESERVE)).isZero();
        assertThat(balance(TreasuryAccountCode.OPERATIONS))
                .as("OPERATIONS solo recibe lo que de verdad salió de la reserva")
                .isEqualTo(5 * SPEND_CENTS);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("la reserva no cubre el gasto: afecta 0 filas y el saldo no cambia")
    void uncoveredSpend_leavesBalanceUntouched() {
        seed(SPEND_CENTS - 1);

        Integer rows = new TransactionTemplate(txManager).execute(s -> treasuryAccountRepository
                .debitIfCovered(TreasuryAccountCode.KEYS_RESERVE, SPEND_CENTS, ZonedDateTime.now()));

        assertThat(rows).isZero();
        assertThat(balance(TreasuryAccountCode.KEYS_RESERVE)).isEqualTo(SPEND_CENTS - 1);
    }

    private void seed(long keysReserveCents) {
        new TransactionTemplate(txManager).executeWithoutResult(s -> {
            TestEntities.persistTreasuryAccount(em, TreasuryAccountCode.KEYS_RESERVE, keysReserveCents);
            TestEntities.persistTreasuryAccount(em, TreasuryAccountCode.OPERATIONS, 0L);
            em.flush();
        });
    }

    private long balance(TreasuryAccountCode code) {
        em.clear();
        return treasuryAccountRepository.findByCode(code).orElseThrow().getBalanceCents();
    }

    /** Lanza todos los gastos a la vez; devuelve cuántos debitaron la reserva. */
    private int spendConcurrently(int concurrentRequests) throws Exception {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        ExecutorService pool = Executors.newFixedThreadPool(concurrentRequests);
        CountDownLatch allThreadsReady = new CountDownLatch(concurrentRequests);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < concurrentRequests; i++) {
            Callable<Integer> task = () -> {
                allThreadsReady.countDown();
                startGate.await();
                return tx.execute(s -> {
                    ZonedDateTime now = ZonedDateTime.now();
                    int debited = treasuryAccountRepository
                            .debitIfCovered(TreasuryAccountCode.KEYS_RESERVE, SPEND_CENTS, now);
                    if (debited == 1) {
                        treasuryAccountRepository.credit(TreasuryAccountCode.OPERATIONS, SPEND_CENTS, now);
                    }
                    return debited;
                });
            };
            futures.add(pool.submit(task));
        }

        allThreadsReady.await(10, TimeUnit.SECONDS);
        startGate.countDown();

        int succeeded = 0;
        for (Future<Integer> future : futures) {
            succeeded += future.get(20, TimeUnit.SECONDS);
        }
        pool.shutdown();
        return succeeded;
    }
}
