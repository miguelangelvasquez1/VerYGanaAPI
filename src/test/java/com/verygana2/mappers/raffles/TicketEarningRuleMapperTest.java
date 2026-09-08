package com.verygana2.mappers.raffles;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.verygana2.dtos.raffle.responses.TicketEarningRuleResponseDTO;
import com.verygana2.models.enums.raffles.TicketEarningRuleType;
import com.verygana2.models.raffles.TicketEarningRule;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TicketEarningRuleMapper")
class TicketEarningRuleMapperTest {

    private final TicketEarningRuleMapper ticketEarningRuleMapper = new TicketEarningRuleMapperImpl();

    @Nested
    @DisplayName("toRuleResponseDTO")
    class ToRuleResponseDTO {

        @Test
        @DisplayName("minPurchaseAmountCents (Long) se convierte a BigDecimal numéricamente igual")
        void minPurchaseAmountCents_convertsLongToEquivalentBigDecimal() {
            TicketEarningRule rule = new TicketEarningRule();
            rule.setRuleName("Compra");
            rule.setRuleType(TicketEarningRuleType.PURCHASE);
            rule.setPriority(1);
            rule.setTicketsToAward(1);
            rule.setMinPurchaseAmountCents(1500L);

            TicketEarningRuleResponseDTO dto = ticketEarningRuleMapper.toRuleResponseDTO(rule);

            assertThat(dto.getMinPurchaseAmountCents()).isEqualByComparingTo(BigDecimal.valueOf(1500));
        }

        @Test
        @DisplayName("minPurchaseAmountCents null: no se setea nada y queda null en el DTO")
        void nullMinPurchaseAmountCents_staysNull() {
            TicketEarningRule rule = new TicketEarningRule();
            rule.setRuleName("Referido");
            rule.setRuleType(TicketEarningRuleType.REFERRAL);
            rule.setPriority(1);
            rule.setTicketsToAward(3);
            rule.setMinPurchaseAmountCents(null);

            TicketEarningRuleResponseDTO dto = ticketEarningRuleMapper.toRuleResponseDTO(rule);

            assertThat(dto.getMinPurchaseAmountCents()).isNull();
        }

        @Test
        @DisplayName("mapea el resto de campos directamente")
        void mapsRemainingFields() {
            TicketEarningRule rule = new TicketEarningRule();
            rule.setRuleName("Login diario");
            rule.setDescription("desc");
            rule.setRuleType(TicketEarningRuleType.DAILY_LOGIN);
            rule.setActive(true);
            rule.setPriority(2);
            rule.setTicketsToAward(1);
            rule.setDailyLogin(true);

            TicketEarningRuleResponseDTO dto = ticketEarningRuleMapper.toRuleResponseDTO(rule);

            assertThat(dto.getRuleName()).isEqualTo("Login diario");
            assertThat(dto.getDescription()).isEqualTo("desc");
            assertThat(dto.getRuleType()).isEqualTo(TicketEarningRuleType.DAILY_LOGIN);
            assertThat(dto.isActive()).isTrue();
            assertThat(dto.getPriority()).isEqualTo(2);
            assertThat(dto.getTicketsToAward()).isEqualTo(1);
            assertThat(dto.isDailyLogin()).isTrue();
        }
    }
}
