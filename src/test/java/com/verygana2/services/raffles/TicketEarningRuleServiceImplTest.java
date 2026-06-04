package com.verygana2.services.raffles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.hibernate.ObjectNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.dtos.generic.EntityUpdatedResponseDTO;
import com.verygana2.dtos.raffle.requests.CreateTicketEarningRuleRequestDTO;
import com.verygana2.dtos.raffle.requests.UpdateTicketEarningRuleRequestDTO;
import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.exceptions.ProductStock.DuplicateResourceException;
import com.verygana2.exceptions.mappers.raffles.TicketEarningRuleMapper;
import com.verygana2.models.enums.raffles.TicketEarningRuleType;
import com.verygana2.models.raffles.RaffleRule;
import com.verygana2.models.raffles.TicketEarningRule;
import com.verygana2.repositories.raffles.TicketEarningRuleRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("TicketEarningRuleServiceImpl")
class TicketEarningRuleServiceImplTest {

    @Mock TicketEarningRuleRepository ruleRepository;
    @Mock TicketEarningRuleMapper ruleMapper;

    @InjectMocks TicketEarningRuleServiceImpl service;

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private CreateTicketEarningRuleRequestDTO purchaseCreateRequest(String name, BigDecimal minAmount) {
        return CreateTicketEarningRuleRequestDTO.builder()
                .ruleName(name)
                .ruleType(TicketEarningRuleType.PURCHASE)
                .ticketsToAward(3)
                .priority(1)
                .minPurchaseAmount(minAmount)
                .build();
    }

    private UpdateTicketEarningRuleRequestDTO purchaseUpdateRequest(String name, BigDecimal minAmount) {
        return UpdateTicketEarningRuleRequestDTO.builder()
                .ruleName(name)
                .ruleType(TicketEarningRuleType.PURCHASE)
                .ticketsToAward(3)
                .priority(1)
                .minPurchaseAmount(minAmount)
                .build();
    }

    // ─── getTicketEarningRuleById ─────────────────────────────────────────────

    @Nested
    @DisplayName("getTicketEarningRuleById")
    class GetById {

        @Test
        @DisplayName("throws IllegalArgumentException for null ID")
        void throwsOnNullId() {
            assertThatThrownBy(() -> service.getTicketEarningRuleById(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for non-positive ID")
        void throwsOnNonPositiveId() {
            assertThatThrownBy(() -> service.getTicketEarningRuleById(0L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("returns rule when found")
        void returnsRule() {
            TicketEarningRule rule = new TicketEarningRule();
            when(ruleRepository.findById(1L)).thenReturn(Optional.of(rule));

            TicketEarningRule result = service.getTicketEarningRuleById(1L);

            assertThat(result).isSameAs(rule);
        }

        @Test
        @DisplayName("throws ObjectNotFoundException when not found")
        void throwsWhenNotFound() {
            when(ruleRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getTicketEarningRuleById(99L))
                    .isInstanceOf(ObjectNotFoundException.class);
        }
    }

    // ─── createTicketEarningRule ──────────────────────────────────────────────

    @Nested
    @DisplayName("createTicketEarningRule")
    class CreateRule {

        @Test
        @DisplayName("throws DuplicateResourceException when rule name already exists")
        void throwsOnDuplicateName() {
            when(ruleRepository.existsByRuleName("Existing Rule")).thenReturn(true);

            assertThatThrownBy(() -> service.createTicketEarningRule(1L, purchaseCreateRequest("Existing Rule", BigDecimal.TEN)))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("throws InvalidRequestException when PURCHASE rule has no minPurchaseAmount")
        void throwsWhenPurchaseMissingAmount() {
            when(ruleRepository.existsByRuleName("New Rule")).thenReturn(false);

            assertThatThrownBy(() -> service.createTicketEarningRule(1L, purchaseCreateRequest("New Rule", null)))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Min purchase amount");
        }

        @Test
        @DisplayName("saves rule and returns EntityCreatedResponseDTO")
        void createsRuleSuccessfully() {
            TicketEarningRule saved = new TicketEarningRule();
            saved.setId(42L);

            when(ruleRepository.existsByRuleName("New Rule")).thenReturn(false);
            when(ruleRepository.save(any())).thenReturn(saved);

            EntityCreatedResponseDTO result = service.createTicketEarningRule(1L,
                    purchaseCreateRequest("New Rule", new BigDecimal("50000")));

            assertThat(result.getId()).isEqualTo(42L);
            verify(ruleRepository).save(any());
        }
    }

    // ─── updateTicketEarningRule ──────────────────────────────────────────────

    @Nested
    @DisplayName("updateTicketEarningRule")
    class UpdateRule {

        @Test
        @DisplayName("throws DuplicateResourceException when new name conflicts with another rule")
        void throwsOnDuplicateName() {
            TicketEarningRule existing = new TicketEarningRule();
            existing.setId(1L);
            existing.setRuleName("Old Name");

            when(ruleRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(ruleRepository.existsByRuleName("Taken Name")).thenReturn(true);

            assertThatThrownBy(() -> service.updateTicketEarningRule(1L, 1L,
                    purchaseUpdateRequest("Taken Name", BigDecimal.TEN)))
                    .isInstanceOf(DuplicateResourceException.class);
        }

        @Test
        @DisplayName("allows keeping same name without triggering duplicate check")
        void allowsSameName() {
            TicketEarningRule existing = new TicketEarningRule();
            existing.setId(1L);
            existing.setRuleName("Same Name");

            when(ruleRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(ruleRepository.save(any())).thenReturn(existing);

            EntityUpdatedResponseDTO result = service.updateTicketEarningRule(1L, 1L,
                    purchaseUpdateRequest("Same Name", new BigDecimal("10000")));

            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("throws InvalidRequestException when PURCHASE rule has no minPurchaseAmount on update")
        void throwsWhenPurchaseMissingAmountOnUpdate() {
            TicketEarningRule existing = new TicketEarningRule();
            existing.setId(1L);
            existing.setRuleName("Old Name");

            when(ruleRepository.findById(1L)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> service.updateTicketEarningRule(1L, 1L,
                    purchaseUpdateRequest("New Name", null)))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Min purchase amount");
        }
    }

    // ─── deleteTicketEarningRule ──────────────────────────────────────────────

    @Nested
    @DisplayName("deleteTicketEarningRule")
    class DeleteRule {

        @Test
        @DisplayName("throws InvalidRequestException when rule is associated with raffles")
        void throwsWhenRuleInUse() {
            TicketEarningRule rule = new TicketEarningRule();
            rule.setId(1L);
            rule.setRaffleRules(List.of(new RaffleRule()));

            when(ruleRepository.findById(1L)).thenReturn(Optional.of(rule));

            assertThatThrownBy(() -> service.deleteTicketEarningRule(1L))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Deactivate");
        }

        @Test
        @DisplayName("deletes rule when not in use")
        void deletesSuccessfully() {
            TicketEarningRule rule = new TicketEarningRule();
            rule.setId(1L);
            rule.setRuleName("Orphan Rule");
            rule.setRaffleRules(List.of());

            when(ruleRepository.findById(1L)).thenReturn(Optional.of(rule));

            service.deleteTicketEarningRule(1L);

            verify(ruleRepository).delete(rule);
        }
    }

    // ─── activateTicketEarningRule ────────────────────────────────────────────

    @Nested
    @DisplayName("activateTicketEarningRule")
    class ActivateRule {

        @Test
        @DisplayName("sets isActive to true and saves")
        void activatesRule() {
            TicketEarningRule rule = new TicketEarningRule();
            rule.setId(1L);
            rule.setActive(false);

            when(ruleRepository.findById(1L)).thenReturn(Optional.of(rule));
            when(ruleRepository.save(rule)).thenReturn(rule);

            service.activateTicketEarningRule(1L);

            assertThat(rule.isActive()).isTrue();
            verify(ruleRepository).save(rule);
        }
    }

    // ─── deactivateTicketEarningRule ──────────────────────────────────────────

    @Nested
    @DisplayName("deactivateTicketEarningRule")
    class DeactivateRule {

        @Test
        @DisplayName("sets isActive to false and saves")
        void deactivatesRule() {
            TicketEarningRule rule = new TicketEarningRule();
            rule.setId(1L);
            rule.setActive(true);

            when(ruleRepository.findById(1L)).thenReturn(Optional.of(rule));
            when(ruleRepository.save(rule)).thenReturn(rule);

            service.deactivateTicketEarningRule(1L);

            assertThat(rule.isActive()).isFalse();
            verify(ruleRepository).save(rule);
        }
    }
}
