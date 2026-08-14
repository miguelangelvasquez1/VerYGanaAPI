package com.verygana2.services.raffles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.verygana2.exceptions.rafflesExceptions.LimitReachedException;
import com.verygana2.mappers.raffles.RaffleTicketMapperImpl;
import com.verygana2.mappers.raffles.TicketAuditLogMapperImpl;
import com.verygana2.models.enums.raffles.DrawMethod;
import com.verygana2.models.enums.raffles.RaffleStatus;
import com.verygana2.models.enums.raffles.RaffleTicketSource;
import com.verygana2.models.enums.raffles.RaffleType;
import com.verygana2.models.enums.raffles.TicketEarningRuleType;
import com.verygana2.models.raffles.Raffle;
import com.verygana2.models.raffles.RaffleRule;
import com.verygana2.models.raffles.TicketEarningRule;
import com.verygana2.repositories.details.ConsumerDetailsRepository;
import com.verygana2.repositories.raffles.RaffleRepository;
import com.verygana2.services.interfaces.details.ConsumerDetailsService;
import com.verygana2.testsupport.TestEntities;
import com.verygana2.utils.audit.AuditContextService;
import com.verygana2.utils.validators.TargetAudienceAssembler;
import com.verygana2.utils.validators.TargetingValidator;

import jakarta.persistence.EntityManager;

/**
 * Reproduce el escenario "carrera por el último ticket": N usuarios pidiendo 1
 * ticket cada uno, simultáneamente, contra una rifa que solo tiene M &lt; N
 * tickets disponibles (maxTotalTickets). No existe endpoint HTTP para esto
 * (los tickets se "ganan", no se compran directamente), así que se ejercita
 * {@link RaffleTicketServiceImpl#issueTickets} directamente contra H2 real,
 * con cada llamada en su propia transacción REQUIRES_NEW (igual que en
 * producción).
 *
 * Antes de {@code RaffleRepository.findByIdForUpdate} (lock pesimista), este
 * mismo test con 20 hilos contra un cupo de 5 tickets dejaba pasar solo 1
 * solicitud: los 20 hilos leían totalTicketsIssued=0 a la vez, generaban el
 * mismo ticket_number y 19 de ellos chocaban contra el UNIQUE constraint con
 * una DataIntegrityViolationException cruda (mapeada a 500), en vez de fallar
 * ordenadamente con LimitReachedException cuando correspondía. Con el lock,
 * la emisión se serializa por rifa: cada hilo espera su turno, lee el
 * contador ya actualizado y usa exactamente el cupo disponible.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:raffle-tickets-concurrency-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.hikari.maximum-pool-size=20"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        RaffleTicketServiceImpl.class,
        RaffleRuleServiceImpl.class,
        AuditContextService.class,
        TargetAudienceAssembler.class,
        RaffleTicketMapperImpl.class,
        TicketAuditLogMapperImpl.class,
        JacksonAutoConfiguration.class,
        RaffleTicketConcurrencyIntegrationTest.TestBeansConfig.class
})
@DisplayName("RaffleTicketService — emisión concurrente cerca del límite (integración H2)")
class RaffleTicketConcurrencyIntegrationTest {

    @Autowired private EntityManager em;
    @Autowired private RaffleTicketServiceImpl raffleTicketService;
    @Autowired private RaffleRepository raffleRepository;
    @Autowired private PlatformTransactionManager txManager;

    // No se usa (targetAudience de la rifa de prueba es null), pero
    // TargetAudienceAssembler la requiere en su constructor.
    @MockitoBean private TargetingValidator targetingValidator;

    /**
     * ConsumerDetailsService real arrastra demasiadas dependencias ajenas a
     * este test (mappers, XP/wallet, etc.). Este mock delega el único método
     * que issueTickets realmente invoca (getConsumerById) al repositorio
     * real, así cada hilo lee el estado vigente en su propia transacción,
     * igual que en producción.
     */
    @TestConfiguration
    static class TestBeansConfig {

        @Bean
        ConsumerDetailsService consumerDetailsService(ConsumerDetailsRepository consumerDetailsRepository) {
            ConsumerDetailsService consumerDetailsService = mock(ConsumerDetailsService.class);
            when(consumerDetailsService.getConsumerById(anyLong())).thenAnswer(invocation -> {
                Long consumerId = invocation.getArgument(0);
                return consumerDetailsRepository.findById(consumerId).orElseThrow();
            });
            return consumerDetailsService;
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("20 usuarios piden 1 ticket a la vez sobre una rifa con solo 5 disponibles: se usan los 5 y el resto falla con LimitReachedException")
    void concurrentIssuance_usesExactlyTheAvailableCap() throws Exception {
        int maxTickets = 5;
        int concurrentRequests = 20;

        TransactionTemplate setupTx = new TransactionTemplate(txManager);
        List<Long> consumerIds = new ArrayList<>();

        Long raffleId = setupTx.execute(status -> {
            Raffle raffle = new Raffle();
            raffle.setTitle("Rifa concurrencia");
            raffle.setDescription("Fixture de test");
            raffle.setRaffleType(RaffleType.STANDARD);
            raffle.setStartDate(ZonedDateTime.now().minusDays(1));
            raffle.setEndDate(ZonedDateTime.now().plusDays(5));
            raffle.setDrawDate(ZonedDateTime.now().plusDays(6));
            raffle.setMaxTotalTickets(maxTickets);
            raffle.setRequiresPet(false);
            raffle.setDrawMethod(DrawMethod.SYSTEM_RANDOM);
            raffle.setCreatedBy(1L);
            em.persist(raffle);
            em.flush();

            // onCreate() fuerza DRAFT al persistir; la activamos después.
            raffle.setRaffleStatus(RaffleStatus.ACTIVE);
            em.flush();

            TicketEarningRule earningRule = new TicketEarningRule();
            earningRule.setRuleName("Compra - concurrencia");
            earningRule.setRuleType(TicketEarningRuleType.PURCHASE);
            earningRule.setPriority(1);
            earningRule.setTicketsToAward(1);
            em.persist(earningRule);

            RaffleRule raffleRule = new RaffleRule();
            raffleRule.setRaffle(raffle);
            raffleRule.setTicketEarningRule(earningRule);
            raffleRule.setMaxTicketsBySource(null); // sin límite propio: el límite lo pone maxTotalTickets
            em.persist(raffleRule);

            for (int i = 0; i < concurrentRequests; i++) {
                consumerIds.add(TestEntities.persistConsumer(em).getId());
            }

            em.flush();
            return raffle.getId();
        });

        // ── Disparo concurrente ────────────────────────────────────────────
        ExecutorService pool = Executors.newFixedThreadPool(concurrentRequests);
        CountDownLatch allThreadsReady = new CountDownLatch(concurrentRequests);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Exception>> futures = new ArrayList<>();

        for (int i = 0; i < concurrentRequests; i++) {
            Long consumerId = consumerIds.get(i);
            long sourceId = 1000L + i;

            // Retorna null en éxito, o la excepción con la que falló.
            Callable<Exception> task = () -> {
                allThreadsReady.countDown();
                startGate.await();
                try {
                    raffleTicketService.issueTickets(consumerId, raffleId, 1, RaffleTicketSource.PURCHASE, sourceId);
                    return null;
                } catch (RuntimeException ex) {
                    return ex;
                }
            };
            futures.add(pool.submit(task));
        }

        allThreadsReady.await(10, TimeUnit.SECONDS);
        startGate.countDown();

        int successCount = 0;
        List<Exception> failures = new ArrayList<>();
        for (Future<Exception> future : futures) {
            Exception failure = future.get(15, TimeUnit.SECONDS);
            if (failure == null) {
                successCount++;
            } else {
                failures.add(failure);
            }
        }
        pool.shutdown();

        // ── Verificación contra el estado real en BD ───────────────────────
        em.clear();

        Long actualTicketsInDb = em.createQuery(
                        "SELECT COUNT(t) FROM RaffleTicket t WHERE t.raffle.id = :raffleId", Long.class)
                .setParameter("raffleId", raffleId)
                .getSingleResult();

        Raffle reloadedRaffle = raffleRepository.findById(raffleId).orElseThrow();

        // Con el lock pesimista, la emisión se serializa: se usan EXACTAMENTE
        // los 5 cupos disponibles, ni más (oversell) ni menos (falsos negativos
        // por la carrera de antes).
        assertThat(actualTicketsInDb)
                .as("deben quedar exactamente maxTotalTickets tickets reales en BD")
                .isEqualTo((long) maxTickets);

        assertThat(successCount)
                .as("deben tener éxito exactamente maxTotalTickets solicitudes")
                .isEqualTo(maxTickets);

        // El contador de la rifa debe reflejar EXACTAMENTE lo que hay en BD:
        // si esto falla, hay un lost-update en el contador (issueTicketsAux
        // hace read-then-increment-then-save).
        assertThat(reloadedRaffle.getTotalTicketsIssued())
                .as("el contador de la rifa no debe perder ni duplicar incrementos bajo concurrencia")
                .isEqualTo(actualTicketsInDb.intValue());

        // Las solicitudes que no alcanzaron cupo deben fallar con la excepción
        // de dominio (LimitReachedException), NO con una excepción cruda de BD
        // (DataIntegrityViolationException) como pasaba sin el lock.
        assertThat(failures)
                .as("las solicitudes sin cupo deben fallar todas con LimitReachedException")
                .hasSize(concurrentRequests - maxTickets)
                .allSatisfy(ex -> assertThat(ex).isInstanceOf(LimitReachedException.class));
    }
}
