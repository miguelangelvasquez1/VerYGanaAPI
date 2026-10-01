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
import com.verygana2.models.enums.raffles.TicketEarningRuleType;
import com.verygana2.models.raffles.Raffle;
import com.verygana2.models.raffles.RaffleRule;
import com.verygana2.models.raffles.TicketEarningRule;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para RaffleRuleRespository.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:raffle-rule-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("RaffleRuleRespository (integración H2)")
class RaffleRuleRespositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private RaffleRuleRespository raffleRuleRespository;

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

    private TicketEarningRule persistEarningRule(String ruleName, TicketEarningRuleType type) {
        TicketEarningRule rule = new TicketEarningRule();
        rule.setRuleName(ruleName);
        rule.setRuleType(type);
        rule.setPriority(1);
        rule.setTicketsToAward(1);
        em.persist(rule);
        em.flush();
        return rule;
    }

    private RaffleRule persistRaffleRule(Raffle raffle, TicketEarningRule rule) {
        RaffleRule raffleRule = new RaffleRule();
        raffleRule.setRaffle(raffle);
        raffleRule.setTicketEarningRule(rule);
        em.persist(raffleRule);
        em.flush();
        return raffleRule;
    }

    // ==================== findByRaffleIdAndRuleType ====================

    @Nested
    @DisplayName("findByRaffleIdAndRuleType")
    class FindByRaffleIdAndRuleType {

        @Test
        @DisplayName("encuentra la regla de la rifa que coincide con el tipo pedido")
        void findsRuleMatchingType() {
            Raffle raffle = persistRaffle("Rifa con regla de compra");
            TicketEarningRule purchaseRule = persistEarningRule("Compra - test", TicketEarningRuleType.PURCHASE);
            RaffleRule raffleRule = persistRaffleRule(raffle, purchaseRule);

            Optional<RaffleRule> found = raffleRuleRespository.findByRaffleIdAndRuleType(raffle.getId(),
                    TicketEarningRuleType.PURCHASE);

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(raffleRule.getId());
        }

        @Test
        @DisplayName("retorna Optional.empty si la rifa no tiene una regla de ese tipo")
        void returnsEmptyWhenTypeDoesNotMatch() {
            Raffle raffle = persistRaffle("Rifa con regla de login diario");
            TicketEarningRule dailyLoginRule = persistEarningRule("Login diario - test",
                    TicketEarningRuleType.DAILY_LOGIN);
            persistRaffleRule(raffle, dailyLoginRule);

            Optional<RaffleRule> found = raffleRuleRespository.findByRaffleIdAndRuleType(raffle.getId(),
                    TicketEarningRuleType.PURCHASE);

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("retorna Optional.empty si la rifa no existe")
        void returnsEmptyWhenRaffleDoesNotExist() {
            Optional<RaffleRule> found = raffleRuleRespository.findByRaffleIdAndRuleType(999999L,
                    TicketEarningRuleType.PURCHASE);

            assertThat(found).isEmpty();
        }
    }
}
