package com.verygana2.repositories.raffles;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
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
import com.verygana2.models.raffles.RaffleResult;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para RaffleResultRepository.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:raffle-result-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("RaffleResultRepository (integración H2)")
class RaffleResultRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private RaffleResultRepository raffleResultRepository;

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

    /**
     * Persiste un RaffleResult y sobrescribe drawnAt (el @PrePersist siempre
     * lo fuerza a "now") para poder controlar el orden de forma determinística.
     */
    private RaffleResult persistResult(String raffleTitle, ZonedDateTime drawnAt) {
        Raffle raffle = persistRaffle(raffleTitle);
        RaffleResult result = new RaffleResult();
        result.setRaffle(raffle);
        em.persist(result);
        em.flush();

        result.setDrawnAt(drawnAt);
        em.flush();
        return result;
    }

    // ==================== findLastRaffleResults ====================

    @Nested
    @DisplayName("findLastRaffleResults")
    class FindLastRaffleResults {

        @Test
        @DisplayName("ordena por drawnAt descendente")
        void ordersByDrawnAtDescending() {
            RaffleResult oldest = persistResult("Rifa resultado antiguo", now().minusDays(3));
            RaffleResult newest = persistResult("Rifa resultado reciente", now().minusHours(1));
            RaffleResult middle = persistResult("Rifa resultado intermedio", now().minusDays(1));

            List<RaffleResult> found = raffleResultRepository.findLastRaffleResults();

            assertThat(found)
                    .extracting(RaffleResult::getId)
                    .containsExactly(newest.getId(), middle.getId(), oldest.getId());
        }

        @Test
        @DisplayName("retorna como máximo 10 resultados")
        void limitsToTen() {
            List<RaffleResult> created = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                created.add(persistResult("Rifa resultado " + i, now().minusHours(i)));
            }

            List<RaffleResult> found = raffleResultRepository.findLastRaffleResults();

            assertThat(found).hasSize(10);
            // Los 10 más recientes son los primeros 10 creados (i = 0..9),
            // ya que a mayor i, más antiguo es drawnAt.
            List<Long> expectedIds = created.subList(0, 10).stream().map(RaffleResult::getId).toList();
            assertThat(found).extracting(RaffleResult::getId).containsExactlyElementsOf(expectedIds);
        }
    }

    // ==================== findByRaffleId ====================

    @Nested
    @DisplayName("findByRaffleId")
    class FindByRaffleId {

        @Test
        @DisplayName("encuentra el resultado asociado a la rifa")
        void findsResultForRaffle() {
            Raffle raffle = persistRaffle("Rifa con resultado");
            RaffleResult result = new RaffleResult();
            result.setRaffle(raffle);
            em.persist(result);
            em.flush();

            Optional<RaffleResult> found = raffleResultRepository.findByRaffleId(raffle.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(result.getId());
        }

        @Test
        @DisplayName("retorna Optional.empty si la rifa no tiene resultado")
        void returnsEmptyWhenNoResult() {
            Raffle raffle = persistRaffle("Rifa sin resultado");

            Optional<RaffleResult> found = raffleResultRepository.findByRaffleId(raffle.getId());

            assertThat(found).isEmpty();
        }
    }
}
