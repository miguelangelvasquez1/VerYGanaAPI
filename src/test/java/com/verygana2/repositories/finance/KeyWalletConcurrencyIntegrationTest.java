package com.verygana2.repositories.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.verygana2.models.finance.KeyWallet;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Operaciones simultáneas sobre la billetera de llaves de un mismo consumidor, cada
 * una en su propia transacción, contra H2 real.
 *
 * <p>Antes la billetera se leía sin bloqueo y sin {@code @Version}: dos gastos a la vez
 * leían el mismo saldo, cada uno restaba lo suyo y el último en guardar pisaba al otro.
 * El usuario gastaba dos veces y solo se le descontaba una. Con
 * {@link KeyWalletRepository#findByConsumerIdForUpdate} la base los pone en fila; y si
 * una ruta se salta el bloqueo, {@code @Version} hace fallar la escritura vieja.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:key-wallet-concurrency-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "CASE_INSENSITIVE_IDENTIFIERS=TRUE;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.hikari.maximum-pool-size=16"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("KeyWallet — operaciones concurrentes del mismo consumidor (integración H2)")
class KeyWalletConcurrencyIntegrationTest {

    private static final long SPEND_CENTS = 1_000L;

    @Autowired private EntityManager em;
    @Autowired private KeyWalletRepository keyWalletRepository;
    @Autowired private PlatformTransactionManager txManager;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("12 gastos simultáneos con saldo de sobra: se descuentan los 12")
    void concurrentSpends_noneIsLost() throws Exception {
        Long consumerId = seedWallet(50 * SPEND_CENTS);

        int succeeded = spendConcurrently(consumerId, 12);

        assertThat(succeeded).isEqualTo(12);
        assertThat(purchaseKeys(consumerId)).isEqualTo(38 * SPEND_CENTS);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("12 gastos simultáneos con saldo para 3: prosperan exactamente 3 y el saldo queda en 0")
    void concurrentSpends_neverSpendMoreThanTheBalance() throws Exception {
        Long consumerId = seedWallet(3 * SPEND_CENTS);

        int succeeded = spendConcurrently(consumerId, 12);

        assertThat(succeeded).isEqualTo(3);
        assertThat(purchaseKeys(consumerId)).isZero();
    }

    /**
     * La red de seguridad: una ruta que lee sin bloqueo y guarda después de que otra
     * transacción ya cambió el saldo no debe poder pisarlo.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("escritura con saldo viejo (lectura sin bloqueo): falla por @Version en vez de pisar el cambio")
    void staleWrite_isRejectedByVersion() {
        Long consumerId = seedWallet(10 * SPEND_CENTS);
        TransactionTemplate outer = new TransactionTemplate(txManager);
        TransactionTemplate inner = new TransactionTemplate(txManager);
        inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> outer.executeWithoutResult(s -> {
            KeyWallet stale = keyWalletRepository.findByConsumerId(consumerId).orElseThrow();

            // Otra transacción gasta y confirma mientras esta sigue con lo que leyó.
            inner.executeWithoutResult(s2 -> spend(consumerId));

            stale.creditKeysCents(5 * SPEND_CENTS, 0);
            keyWalletRepository.save(stale);
        })).isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(purchaseKeys(consumerId))
                .as("queda el gasto confirmado; la escritura vieja no lo pisó")
                .isEqualTo(9 * SPEND_CENTS);
    }

    private Long seedWallet(long purchaseKeysCents) {
        return new TransactionTemplate(txManager).execute(s -> {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            KeyWallet wallet = KeyWallet.createFor(consumer);
            wallet.setPurchaseKeysCents(purchaseKeysCents);
            em.persist(wallet);
            em.flush();
            return consumer.getId();
        });
    }

    private long purchaseKeys(Long consumerId) {
        em.clear();
        return keyWalletRepository.findByConsumerId(consumerId).orElseThrow().getPurchaseKeysCents();
    }

    /** El leer-modificar-guardar de un gasto. Devuelve 1 si gastó, 0 si no había saldo. */
    private int spend(Long consumerId) {
        KeyWallet wallet = keyWalletRepository.findByConsumerIdForUpdate(consumerId).orElseThrow();
        if (!wallet.hasSufficientPurchaseKeysCents(SPEND_CENTS)) {
            return 0;
        }
        wallet.expirePurchaseKeysCents(SPEND_CENTS);
        keyWalletRepository.save(wallet);
        return 1;
    }

    private int spendConcurrently(Long consumerId, int concurrentRequests) throws Exception {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        ExecutorService pool = Executors.newFixedThreadPool(concurrentRequests);
        CountDownLatch allThreadsReady = new CountDownLatch(concurrentRequests);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < concurrentRequests; i++) {
            Callable<Integer> task = () -> {
                allThreadsReady.countDown();
                startGate.await();
                return tx.execute(s -> spend(consumerId));
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
