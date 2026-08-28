package com.verygana2.repositories.raffles;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.verygana2.models.enums.raffles.DrawMethod;
import com.verygana2.models.enums.raffles.RaffleType;
import com.verygana2.models.raffles.Raffle;
import com.verygana2.models.raffles.RaffleParticipation;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para RaffleParticipationRepository.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:raffle-participation-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("RaffleParticipationRepository (integración H2)")
class RaffleParticipationRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private RaffleParticipationRepository raffleParticipationRepository;

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

    private RaffleParticipation persistParticipation(Raffle raffle, ConsumerDetails consumer) {
        RaffleParticipation participation = new RaffleParticipation();
        participation.setRaffle(raffle);
        participation.setConsumer(consumer);
        em.persist(participation);
        em.flush();
        return participation;
    }

    // ==================== findByConsumerIdAndRaffleId ====================

    @Nested
    @DisplayName("findByConsumerIdAndRaffleId")
    class FindByConsumerIdAndRaffleId {

        @Test
        @DisplayName("encuentra la participación existente del consumer en la rifa")
        void findsExistingParticipation() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            Raffle raffle = persistRaffle("Rifa con participación");
            RaffleParticipation participation = persistParticipation(raffle, consumer);

            Optional<RaffleParticipation> found = raffleParticipationRepository
                    .findByConsumerIdAndRaffleId(consumer.getId(), raffle.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(participation.getId());
        }

        @Test
        @DisplayName("retorna Optional.empty si el consumer no participó en esa rifa")
        void returnsEmptyWhenNoParticipation() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            Raffle raffleWithParticipation = persistRaffle("Rifa con otra participación");
            Raffle raffleWithoutParticipation = persistRaffle("Rifa sin participación de este consumer");
            persistParticipation(raffleWithParticipation, consumer);

            Optional<RaffleParticipation> found = raffleParticipationRepository
                    .findByConsumerIdAndRaffleId(consumer.getId(), raffleWithoutParticipation.getId());

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("retorna Optional.empty si el consumer no existe")
        void returnsEmptyWhenConsumerDoesNotExist() {
            Raffle raffle = persistRaffle("Rifa sin consumer");

            Optional<RaffleParticipation> found = raffleParticipationRepository
                    .findByConsumerIdAndRaffleId(999999L, raffle.getId());

            assertThat(found).isEmpty();
        }
    }
}
