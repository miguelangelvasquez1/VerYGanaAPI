package com.verygana2.repositories.raffles;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.verygana2.models.enums.raffles.DrawMethod;
import com.verygana2.models.enums.raffles.PrizeStatus;
import com.verygana2.models.enums.raffles.PrizeType;
import com.verygana2.models.enums.raffles.RaffleTicketSource;
import com.verygana2.models.enums.raffles.RaffleType;
import com.verygana2.models.raffles.Prize;
import com.verygana2.models.raffles.Raffle;
import com.verygana2.models.raffles.RaffleResult;
import com.verygana2.models.raffles.RaffleTicket;
import com.verygana2.models.raffles.RaffleWinner;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para RaffleWinnerRepository.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        // NON_KEYWORDS=VALUE: Prize.value se mapea a una columna "value", que
        // H2 2.x reserva como palabra clave por defecto y rompe el CREATE TABLE
        // de raffle_prizes (aunque en MySQL real no es reservada).
        "spring.datasource.url=jdbc:h2:mem:raffle-winner-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;NON_KEYWORDS=VALUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("RaffleWinnerRepository (integración H2)")
class RaffleWinnerRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private RaffleWinnerRepository raffleWinnerRepository;

    // ==================== HELPERS ====================

    private static ZonedDateTime now() {
        return ZonedDateTime.now();
    }

    private Raffle persistRaffle(String title) {
        Raffle raffle = new Raffle();
        raffle.setTitle(title);
        raffle.setDescription("desc " + title);
        raffle.setRaffleType(RaffleType.STANDARD);
        raffle.setStartDate(now().minusDays(10));
        raffle.setEndDate(now().minusDays(2));
        raffle.setDrawDate(now().minusDays(1));
        raffle.setDrawMethod(DrawMethod.SYSTEM_RANDOM);
        raffle.setCreatedBy(1L);
        em.persist(raffle);
        em.flush();
        return raffle;
    }

    private RaffleResult persistRaffleResult(Raffle raffle) {
        RaffleResult result = new RaffleResult();
        result.setRaffle(raffle);
        em.persist(result);
        em.flush();
        return result;
    }

    private Prize persistPrize(Raffle raffle, int position) {
        Prize prize = new Prize();
        prize.setRaffle(raffle);
        prize.setTitle("Premio " + position + " - " + raffle.getTitle());
        prize.setValue(BigDecimal.valueOf(100));
        prize.setPosition(position);
        prize.setQuantity(1);
        prize.setPrizeType(PrizeType.PHYSICAL);
        prize.setClaimCode("CLAIM-" + raffle.getId() + "-" + position);
        em.persist(prize);
        em.flush();
        return prize;
    }

    private RaffleTicket persistWinningTicket(Raffle raffle, ConsumerDetails owner, String ticketNumber) {
        RaffleTicket ticket = new RaffleTicket();
        ticket.setRaffle(raffle);
        ticket.setTicketOwner(owner);
        ticket.setTicketNumber(ticketNumber);
        ticket.setSource(RaffleTicketSource.PURCHASE);
        ticket.setSourceId(1L);
        em.persist(ticket);
        em.flush();
        ticket.setIsWinner(true);
        em.flush();
        return ticket;
    }

    private RaffleWinner persistRaffleWinner(RaffleResult result, Prize prize, ConsumerDetails winner,
            RaffleTicket winningTicket) {
        RaffleWinner raffleWinner = new RaffleWinner();
        raffleWinner.setRaffleResult(result);
        raffleWinner.setPrize(prize);
        raffleWinner.setWinner(winner);
        raffleWinner.setWinningTicket(winningTicket);
        em.persist(raffleWinner);
        em.flush();
        return raffleWinner;
    }

    private void setPrizeStatus(Prize prize, PrizeStatus status) {
        prize.setPrizeStatus(status);
        em.flush();
    }

    private void setCreatedAt(RaffleWinner winner, ZonedDateTime createdAt) {
        winner.setCreatedAt(createdAt);
        em.flush();
    }

    // ==================== findByRaffleResultId ====================

    @Nested
    @DisplayName("findByRaffleResultId")
    class FindByRaffleResultId {

        @Test
        @DisplayName("trae todos los ganadores de un resultado y ninguno de otro")
        void returnsAllWinnersOfResultOnly() {
            ConsumerDetails winner1 = TestEntities.persistConsumer(em);
            ConsumerDetails winner2 = TestEntities.persistConsumer(em);
            Raffle raffle = persistRaffle("Rifa con dos ganadores");
            RaffleResult result = persistRaffleResult(raffle);
            RaffleResult otherResult = persistRaffleResult(persistRaffle("Otra rifa con resultado"));

            Prize prize1 = persistPrize(raffle, 1);
            Prize prize2 = persistPrize(raffle, 2);
            RaffleTicket ticket1 = persistWinningTicket(raffle, winner1, "RB-1");
            RaffleTicket ticket2 = persistWinningTicket(raffle, winner2, "RB-2");

            persistRaffleWinner(result, prize1, winner1, ticket1);
            persistRaffleWinner(result, prize2, winner2, ticket2);

            List<RaffleWinner> found = raffleWinnerRepository.findByRaffleResultId(result.getId());
            List<RaffleWinner> foundOther = raffleWinnerRepository.findByRaffleResultId(otherResult.getId());

            assertThat(found).hasSize(2);
            assertThat(foundOther).isEmpty();
        }
    }

    // ==================== findWonPrizesByConsumer ====================

    @Nested
    @DisplayName("findWonPrizesByConsumer")
    class FindWonPrizesByConsumer {

        @Test
        @DisplayName("sin filtro de status trae todos los premios ganados, con los JOIN FETCH ya inicializados")
        void withoutStatusFilterReturnsAllFetched() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            Raffle raffleA = persistRaffle("Rifa premios A");
            Raffle raffleB = persistRaffle("Rifa premios B");
            RaffleResult resultA = persistRaffleResult(raffleA);
            RaffleResult resultB = persistRaffleResult(raffleB);

            Prize prizeA = persistPrize(raffleA, 1);
            Prize prizeB = persistPrize(raffleB, 1);
            setPrizeStatus(prizeA, PrizeStatus.PENDING);
            setPrizeStatus(prizeB, PrizeStatus.DELIVERED);

            RaffleTicket ticketA = persistWinningTicket(raffleA, consumer, "WP-A");
            RaffleTicket ticketB = persistWinningTicket(raffleB, consumer, "WP-B");

            persistRaffleWinner(resultA, prizeA, consumer, ticketA);
            persistRaffleWinner(resultB, prizeB, consumer, ticketB);

            em.clear();

            Page<RaffleWinner> page = raffleWinnerRepository.findWonPrizesByConsumer(
                    consumer.getId(), null, PageRequest.of(0, 10));

            assertThat(page.getContent()).hasSize(2);
            // Los JOIN FETCH (prize, winningTicket, raffleResult) deben estar
            // inicializados: acceder a estos getters no debe lanzar
            // LazyInitializationException aunque el contexto se haya limpiado.
            for (RaffleWinner w : page.getContent()) {
                assertThat(w.getPrize().getTitle()).isNotNull();
                assertThat(w.getWinningTicket().getTicketNumber()).isNotNull();
                assertThat(w.getRaffleResult().getId()).isNotNull();
            }
        }

        @Test
        @DisplayName("con filtro de status solo trae los premios que coinciden")
        void withStatusFilterReturnsOnlyMatching() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            Raffle raffleA = persistRaffle("Rifa premios filtro A");
            Raffle raffleB = persistRaffle("Rifa premios filtro B");
            RaffleResult resultA = persistRaffleResult(raffleA);
            RaffleResult resultB = persistRaffleResult(raffleB);

            Prize prizeA = persistPrize(raffleA, 1);
            Prize prizeB = persistPrize(raffleB, 1);
            setPrizeStatus(prizeA, PrizeStatus.PENDING);
            setPrizeStatus(prizeB, PrizeStatus.DELIVERED);

            RaffleTicket ticketA = persistWinningTicket(raffleA, consumer, "WPF-A");
            RaffleTicket ticketB = persistWinningTicket(raffleB, consumer, "WPF-B");

            persistRaffleWinner(resultA, prizeA, consumer, ticketA);
            persistRaffleWinner(resultB, prizeB, consumer, ticketB);

            em.clear();

            Page<RaffleWinner> delivered = raffleWinnerRepository.findWonPrizesByConsumer(
                    consumer.getId(), PrizeStatus.DELIVERED, PageRequest.of(0, 10));

            assertThat(delivered.getContent()).hasSize(1);
            assertThat(delivered.getContent().get(0).getPrize().getPrizeStatus()).isEqualTo(PrizeStatus.DELIVERED);
        }
    }

    // ==================== countByRaffleResultId ====================

    @Nested
    @DisplayName("countByRaffleResultId")
    class CountByRaffleResultId {

        @Test
        @DisplayName("cuenta los ganadores asociados a ese resultado")
        void countsWinnersForResult() {
            ConsumerDetails winner1 = TestEntities.persistConsumer(em);
            ConsumerDetails winner2 = TestEntities.persistConsumer(em);
            Raffle raffle = persistRaffle("Rifa conteo ganadores");
            RaffleResult result = persistRaffleResult(raffle);

            Prize prize1 = persistPrize(raffle, 1);
            Prize prize2 = persistPrize(raffle, 2);
            RaffleTicket ticket1 = persistWinningTicket(raffle, winner1, "CT-1");
            RaffleTicket ticket2 = persistWinningTicket(raffle, winner2, "CT-2");

            persistRaffleWinner(result, prize1, winner1, ticket1);
            persistRaffleWinner(result, prize2, winner2, ticket2);

            assertThat(raffleWinnerRepository.countByRaffleResultId(result.getId())).isEqualTo(2);
            assertThat(raffleWinnerRepository.countByRaffleResultId(999999L)).isEqualTo(0);
        }
    }

    // ==================== findLastWinners ====================

    @Nested
    @DisplayName("findLastWinners")
    class FindLastWinners {

        @Test
        @DisplayName("ordena por createdAt descendente")
        void ordersByCreatedAtDescending() {
            ConsumerDetails winner1 = TestEntities.persistConsumer(em);
            ConsumerDetails winner2 = TestEntities.persistConsumer(em);
            ConsumerDetails winner3 = TestEntities.persistConsumer(em);
            Raffle raffle = persistRaffle("Rifa últimos ganadores");
            RaffleResult result = persistRaffleResult(raffle);

            Prize prize1 = persistPrize(raffle, 1);
            Prize prize2 = persistPrize(raffle, 2);
            Prize prize3 = persistPrize(raffle, 3);

            RaffleTicket ticket1 = persistWinningTicket(raffle, winner1, "LW-1");
            RaffleTicket ticket2 = persistWinningTicket(raffle, winner2, "LW-2");
            RaffleTicket ticket3 = persistWinningTicket(raffle, winner3, "LW-3");

            RaffleWinner w1 = persistRaffleWinner(result, prize1, winner1, ticket1);
            RaffleWinner w2 = persistRaffleWinner(result, prize2, winner2, ticket2);
            RaffleWinner w3 = persistRaffleWinner(result, prize3, winner3, ticket3);

            // Fuerza un orden determinístico de createdAt (el @PrePersist usa
            // ZonedDateTime.now(), que puede coincidir entre inserts consecutivos).
            setCreatedAt(w1, now().minusHours(3));
            setCreatedAt(w2, now().minusHours(1));
            setCreatedAt(w3, now().minusHours(2));

            List<RaffleWinner> lastWinners = raffleWinnerRepository.findLastWinners();

            assertThat(lastWinners).extracting(RaffleWinner::getId)
                    .containsExactly(w2.getId(), w3.getId(), w1.getId());
        }
    }

    // ==================== findByPrizeId ====================

    @Nested
    @DisplayName("findByPrizeId")
    class FindByPrizeId {

        @Test
        @DisplayName("encuentra el ganador de un premio específico")
        void findsWinnerForPrize() {
            ConsumerDetails winner = TestEntities.persistConsumer(em);
            Raffle raffle = persistRaffle("Rifa por premio");
            RaffleResult result = persistRaffleResult(raffle);
            Prize prize = persistPrize(raffle, 1);
            RaffleTicket ticket = persistWinningTicket(raffle, winner, "BP-1");
            RaffleWinner raffleWinner = persistRaffleWinner(result, prize, winner, ticket);

            Optional<RaffleWinner> found = raffleWinnerRepository.findByPrizeId(prize.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(raffleWinner.getId());
        }

        @Test
        @DisplayName("retorna Optional.empty si el premio no tiene ganador registrado")
        void returnsEmptyWhenNoWinner() {
            Optional<RaffleWinner> found = raffleWinnerRepository.findByPrizeId(999999L);
            assertThat(found).isEmpty();
        }
    }
}
