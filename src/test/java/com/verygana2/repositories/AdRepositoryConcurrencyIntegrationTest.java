package com.verygana2.repositories;

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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.verygana2.models.ads.Ad;
import com.verygana2.models.enums.AdStatus;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Reproduce el escenario "carrera por el último like": N consumidores dando
 * like a la vez al mismo anuncio, contra un anuncio con solo M &lt; N cupos
 * ({@code maxLikes}). Se ejercita {@link AdRepository#incrementLikeIfAvailable}
 * directamente contra H2 real, cada llamada en su propia transacción (igual que
 * en producción vía el proxy {@code @Transactional} de {@code processAdLike}).
 *
 * <p>Antes, {@code processAdLike} hacía read-then-increment-then-save sobre la
 * entidad {@code Ad} y confiaba en {@code @Version} + reintento: dos likes
 * simultáneos leían {@code currentLikes = 0}, ambos incrementaban y el perdedor
 * recibía un HTTP 500 genérico (el fallo de {@code @Version} afloraba sin
 * traducir). Con el UPDATE atómico condicionado ({@code WHERE currentLikes <
 * maxLikes}) la BD serializa los incrementos sobre la fila: se usan exactamente
 * los cupos disponibles y el resto obtiene 0 filas afectadas.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:ad-like-concurrency-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "CASE_INSENSITIVE_IDENTIFIERS=TRUE;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.hikari.maximum-pool-size=16"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("AdRepository.incrementLikeIfAvailable — carrera por el último like (integración H2)")
class AdRepositoryConcurrencyIntegrationTest {

    @Autowired private EntityManager em;
    @Autowired private AdRepository adRepository;
    @Autowired private PlatformTransactionManager txManager;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("maxLikes = 1 con 12 likes simultáneos: prospera exactamente 1, current_likes = 1 y el anuncio queda COMPLETED")
    void concurrentLikes_lastSlot_neverOversells() throws Exception {
        assertExactlyCapWins(1, 12);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("maxLikes = 4 con 12 likes simultáneos: prosperan exactamente 4 y el anuncio queda COMPLETED")
    void concurrentLikes_partialCapacity_usesExactlyTheCap() throws Exception {
        assertExactlyCapWins(4, 12);
    }

    private void assertExactlyCapWins(int maxLikes, int concurrentRequests) throws Exception {
        TransactionTemplate tx = new TransactionTemplate(txManager);

        Long adId = tx.execute(status -> {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Ad ad = Ad.builder()
                    .title("Anuncio carrera de likes")
                    .description("Fixture del test de concurrencia de likes")
                    .rewardPerLike(100L)
                    .maxLikes(maxLikes)
                    .currentLikes(0)
                    .status(AdStatus.ACTIVE)
                    .createdAt(ZonedDateTime.now())
                    .commercial(commercial)
                    .build();
            em.persist(ad);
            em.flush();
            return ad.getId();
        });

        ExecutorService pool = Executors.newFixedThreadPool(concurrentRequests);
        CountDownLatch allThreadsReady = new CountDownLatch(concurrentRequests);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < concurrentRequests; i++) {
            Callable<Integer> task = () -> {
                allThreadsReady.countDown();
                startGate.await();
                return tx.execute(s -> adRepository.incrementLikeIfAvailable(adId, ZonedDateTime.now()));
            };
            futures.add(pool.submit(task));
        }

        allThreadsReady.await(10, TimeUnit.SECONDS);
        startGate.countDown();

        int registeredLikes = 0;
        for (Future<Integer> future : futures) {
            registeredLikes += future.get(20, TimeUnit.SECONDS);
        }
        pool.shutdown();

        // ── Verificación contra el estado real en BD ───────────────────────
        em.clear();
        Ad reloaded = adRepository.findById(adId).orElseThrow();

        assertThat(registeredLikes)
                .as("deben prosperar exactamente maxLikes incrementos")
                .isEqualTo(maxLikes);
        assertThat(reloaded.getCurrentLikes())
                .as("el contador no debe exceder maxLikes ni perder incrementos bajo concurrencia")
                .isEqualTo(maxLikes);
        assertThat(reloaded.getStatus())
                .as("el anuncio debe cerrarse al alcanzar el tope")
                .isEqualTo(AdStatus.COMPLETED);
    }
}
