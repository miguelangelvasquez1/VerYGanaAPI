package com.verygana2.repositories.raffles;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

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
 * Tests de integración H2 (modo MySQL) para PrizeRepository.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        // NON_KEYWORDS=VALUE: en H2 2.x "VALUE" es palabra reservada y rompe el
        // "create table raffle_prizes (... value numeric(10,2) ...)" generado por
        // Hibernate para Prize.value (columna real de producción, no se puede
        // renombrar aquí). Este flag le dice a H2 que la trate como identificador
        // normal, igual que hace MySQL en producción.
        "spring.datasource.url=jdbc:h2:mem:prize-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;NON_KEYWORDS=VALUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("PrizeRepository (integración H2)")
class PrizeRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private PrizeRepository prizeRepository;

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

    private RaffleResult persistRaffleResult(Raffle raffle) {
        RaffleResult result = new RaffleResult();
        result.setRaffle(raffle);
        em.persist(result);
        em.flush();
        return result;
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

    /**
     * Persiste un RaffleWinner y luego sobrescribe prizeClaimed/claimDeadline
     * (el @PrePersist siempre fuerza prizeClaimed=false y claimDeadline=+30d).
     */
    private RaffleWinner persistRaffleWinner(RaffleResult result, Prize prize, ConsumerDetails winner,
            RaffleTicket ticket, boolean claimed, ZonedDateTime claimDeadline) {
        RaffleWinner raffleWinner = new RaffleWinner();
        raffleWinner.setRaffleResult(result);
        raffleWinner.setPrize(prize);
        raffleWinner.setWinner(winner);
        raffleWinner.setWinningTicket(ticket);
        em.persist(raffleWinner);
        em.flush();

        raffleWinner.setPrizeClaimed(claimed);
        raffleWinner.setClaimDeadline(claimDeadline);
        em.flush();
        return raffleWinner;
    }

    private void setPrizeStatus(Prize prize, PrizeStatus status) {
        prize.setPrizeStatus(status);
        em.flush();
    }

    // ==================== findByRaffleIdOrderByPositionAsc ====================

    @Nested
    @DisplayName("findByRaffleIdOrderByPositionAsc")
    class FindByRaffleIdOrderByPositionAsc {

        @Test
        @DisplayName("trae los premios de una rifa ordenados ascendentemente por posición")
        void returnsPrizesOrderedByPosition() {
            Raffle raffle = persistRaffle("Rifa con premios ordenados");
            Prize third = persistPrize(raffle, 3);
            Prize first = persistPrize(raffle, 1);
            Prize second = persistPrize(raffle, 2);

            Raffle otherRaffle = persistRaffle("Otra rifa");
            persistPrize(otherRaffle, 1);

            List<Prize> found = prizeRepository.findByRaffleIdOrderByPositionAsc(raffle.getId());

            assertThat(found)
                    .extracting(Prize::getId)
                    .containsExactly(first.getId(), second.getId(), third.getId());
        }

        @Test
        @DisplayName("retorna lista vacía si la rifa no tiene premios")
        void returnsEmptyWhenNoPrizes() {
            Raffle raffle = persistRaffle("Rifa sin premios");

            List<Prize> found = prizeRepository.findByRaffleIdOrderByPositionAsc(raffle.getId());

            assertThat(found).isEmpty();
        }
    }

    // ==================== findOverdueUnclaimedPrizes ====================

    @Nested
    @DisplayName("findOverdueUnclaimedPrizes")
    class FindOverdueUnclaimedPrizes {

        @Test
        @DisplayName("premio PENDING con ganador vencido y sin reclamar aparece")
        void returnsOverdueUnclaimedPrize() {
            ConsumerDetails winner = TestEntities.persistConsumer(em);
            Raffle raffle = persistRaffle("Rifa premio vencido");
            RaffleResult result = persistRaffleResult(raffle);
            Prize prize = persistPrize(raffle, 1);
            RaffleTicket ticket = persistWinningTicket(raffle, winner, "OV-1");

            // Ganador sin reclamar, con plazo ya vencido.
            persistRaffleWinner(result, prize, winner, ticket, false, now().minusDays(1));

            List<Prize> found = prizeRepository.findOverdueUnclaimedPrizes(now());

            assertThat(found).extracting(Prize::getId).containsExactly(prize.getId());
        }

        @Test
        @DisplayName("premio ya reclamado a tiempo (DELIVERED) no aparece")
        void excludesClaimedPrize() {
            ConsumerDetails winner = TestEntities.persistConsumer(em);
            Raffle raffle = persistRaffle("Rifa premio reclamado");
            RaffleResult result = persistRaffleResult(raffle);
            Prize prize = persistPrize(raffle, 1);
            RaffleTicket ticket = persistWinningTicket(raffle, winner, "CL-1");

            // Reclamado dentro de plazo: el flujo real marca el premio DELIVERED.
            persistRaffleWinner(result, prize, winner, ticket, true, now().plusDays(5));
            setPrizeStatus(prize, PrizeStatus.DELIVERED);

            List<Prize> found = prizeRepository.findOverdueUnclaimedPrizes(now());

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("premio PENDING con ganador sin reclamar pero dentro de plazo no aparece")
        void excludesPrizeWithinDeadline() {
            ConsumerDetails winner = TestEntities.persistConsumer(em);
            Raffle raffle = persistRaffle("Rifa premio dentro de plazo");
            RaffleResult result = persistRaffleResult(raffle);
            Prize prize = persistPrize(raffle, 1);
            RaffleTicket ticket = persistWinningTicket(raffle, winner, "WD-1");

            persistRaffleWinner(result, prize, winner, ticket, false, now().plusDays(5));

            List<Prize> found = prizeRepository.findOverdueUnclaimedPrizes(now());

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("premio PENDING sin ningún ganador registrado no aparece")
        void excludesPrizeWithoutWinner() {
            Raffle raffle = persistRaffle("Rifa premio sin ganador");
            persistPrize(raffle, 1);

            List<Prize> found = prizeRepository.findOverdueUnclaimedPrizes(now());

            assertThat(found).isEmpty();
        }
    }
}
