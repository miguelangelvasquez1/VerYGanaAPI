package com.verygana2.services.raffles;

import java.util.List;
import java.util.Optional;

import org.hibernate.ObjectNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.dtos.raffle.requests.CreateTicketEarningRuleRequestDTO;
import com.verygana2.dtos.raffle.requests.UpdateTicketEarningRuleRequestDTO;
import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.exceptions.ProductStock.DuplicateResourceException;
import com.verygana2.mappers.raffles.TicketEarningRuleMapper;
import com.verygana2.models.enums.raffles.TicketEarningRuleType;
import com.verygana2.models.raffles.RaffleRule;
import com.verygana2.models.raffles.TicketEarningRule;
import com.verygana2.repositories.raffles.TicketEarningRuleRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link TicketEarningRuleServiceImpl}: alta/edición de reglas
 * globales de obtención de tickets, con sus validaciones de nombre único,
 * condiciones según el tipo de regla, y la protección contra borrar una
 * regla que ya está en uso por alguna rifa.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TicketEarningRuleServiceImpl")
class TicketEarningRuleServiceImplTest {

    @Mock private TicketEarningRuleRepository ruleRepository;
    @Mock private TicketEarningRuleMapper ruleMapper;

    private TicketEarningRuleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TicketEarningRuleServiceImpl(ruleRepository, ruleMapper);
    }

    @Nested
    @DisplayName("createTicketEarningRule")
    class Create {

        @Test
        @DisplayName("regla PURCHASE con monto mínimo: se crea correctamente")
        void purchaseRuleWithMinAmount_createsSuccessfully() {
            CreateTicketEarningRuleRequestDTO request = CreateTicketEarningRuleRequestDTO.builder()
                    .ruleName("Compra mínima").ruleType(TicketEarningRuleType.PURCHASE)
                    .priority(1).minPurchaseAmount(50_000L).ticketsToAward(1).build();

            when(ruleRepository.existsByRuleName("Compra mínima")).thenReturn(false);
            when(ruleRepository.save(any(TicketEarningRule.class))).thenAnswer(inv -> {
                TicketEarningRule rule = inv.getArgument(0);
                rule.setId(5L);
                return rule;
            });

            var response = service.createTicketEarningRule(1L, request);

            assertThat(response.getId()).isEqualTo(5L);
        }

        @Test
        @DisplayName("nombre de regla duplicado: lanza DuplicateResourceException")
        void duplicateName_throwsDuplicateResourceException() {
            CreateTicketEarningRuleRequestDTO request = CreateTicketEarningRuleRequestDTO.builder()
                    .ruleName("Ya existe").ruleType(TicketEarningRuleType.PURCHASE)
                    .priority(1).minPurchaseAmount(1000L).ticketsToAward(1).build();

            when(ruleRepository.existsByRuleName("Ya existe")).thenReturn(true);

            assertThatThrownBy(() -> service.createTicketEarningRule(1L, request))
                    .isInstanceOf(DuplicateResourceException.class);
            verify(ruleRepository, never()).save(any());
        }

        @Test
        @DisplayName("regla PURCHASE sin monto mínimo: lanza InvalidRequestException")
        void purchaseRuleWithoutMinAmount_throwsInvalidRequestException() {
            CreateTicketEarningRuleRequestDTO request = CreateTicketEarningRuleRequestDTO.builder()
                    .ruleName("Sin monto").ruleType(TicketEarningRuleType.PURCHASE)
                    .priority(1).minPurchaseAmount(null).ticketsToAward(1).build();

            when(ruleRepository.existsByRuleName("Sin monto")).thenReturn(false);

            assertThatThrownBy(() -> service.createTicketEarningRule(1L, request))
                    .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        @DisplayName("regla DAILY_LOGIN sin marcar dailyLogin=true: lanza InvalidRequestException")
        void dailyLoginRuleWithoutFlag_throwsInvalidRequestException() {
            CreateTicketEarningRuleRequestDTO request = CreateTicketEarningRuleRequestDTO.builder()
                    .ruleName("Login diario").ruleType(TicketEarningRuleType.DAILY_LOGIN)
                    .priority(1).dailyLogin(false).ticketsToAward(1).build();

            when(ruleRepository.existsByRuleName("Login diario")).thenReturn(false);

            assertThatThrownBy(() -> service.createTicketEarningRule(1L, request))
                    .isInstanceOf(InvalidRequestException.class);
        }
    }

    @Nested
    @DisplayName("updateTicketEarningRule")
    class Update {

        @Test
        @DisplayName("nuevo nombre ya usado por OTRA regla: lanza DuplicateResourceException")
        void newNameUsedByAnotherRule_throwsDuplicateResourceException() {
            TicketEarningRule existing = new TicketEarningRule();
            existing.setId(5L);
            existing.setRuleName("Nombre viejo");
            when(ruleRepository.findById(5L)).thenReturn(Optional.of(existing));
            when(ruleRepository.existsByRuleName("Nombre nuevo")).thenReturn(true);

            UpdateTicketEarningRuleRequestDTO request = UpdateTicketEarningRuleRequestDTO.builder()
                    .ruleName("Nombre nuevo").ruleType(TicketEarningRuleType.PURCHASE)
                    .priority(1).minPurchaseAmount(1000L).ticketsToAward(1).build();

            assertThatThrownBy(() -> service.updateTicketEarningRule(1L, 5L, request))
                    .isInstanceOf(DuplicateResourceException.class);
        }

        @Test
        @DisplayName("mantener el mismo nombre no cuenta como duplicado")
        void keepingSameName_isNotADuplicate() {
            TicketEarningRule existing = new TicketEarningRule();
            existing.setId(5L);
            existing.setRuleName("Mismo nombre");
            when(ruleRepository.findById(5L)).thenReturn(Optional.of(existing));
            when(ruleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UpdateTicketEarningRuleRequestDTO request = UpdateTicketEarningRuleRequestDTO.builder()
                    .ruleName("Mismo nombre").ruleType(TicketEarningRuleType.PURCHASE)
                    .priority(2).minPurchaseAmount(2000L).ticketsToAward(2).build();

            service.updateTicketEarningRule(1L, 5L, request);

            verify(ruleRepository, never()).existsByRuleName(any());
            assertThat(existing.getPriority()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("deleteTicketEarningRule: si está asociada a rifas, lanza InvalidRequestException")
    void delete_associatedWithRaffles_throwsInvalidRequestException() {
        TicketEarningRule rule = new TicketEarningRule();
        rule.setId(5L);
        rule.setRaffleRules(List.of(new RaffleRule()));
        when(ruleRepository.findById(5L)).thenReturn(Optional.of(rule));

        assertThatThrownBy(() -> service.deleteTicketEarningRule(5L)).isInstanceOf(InvalidRequestException.class);
        verify(ruleRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteTicketEarningRule: sin uso, se elimina sin problema")
    void delete_notInUse_deletesIt() {
        TicketEarningRule rule = new TicketEarningRule();
        rule.setId(5L);
        rule.setRaffleRules(List.of());
        when(ruleRepository.findById(5L)).thenReturn(Optional.of(rule));

        service.deleteTicketEarningRule(5L);

        verify(ruleRepository).delete(rule);
    }

    @Test
    @DisplayName("activate/deactivateTicketEarningRule: cambian el flag isActive")
    void activateAndDeactivate_toggleActiveFlag() {
        TicketEarningRule rule = new TicketEarningRule();
        rule.setId(5L);
        when(ruleRepository.findById(5L)).thenReturn(Optional.of(rule));

        service.deactivateTicketEarningRule(5L);
        assertThat(rule.isActive()).isFalse();

        service.activateTicketEarningRule(5L);
        assertThat(rule.isActive()).isTrue();
    }

    @Test
    @DisplayName("getTicketEarningRuleById: id inválido lanza IllegalArgumentException; inexistente lanza ObjectNotFoundException")
    void getById_validations() {
        assertThatThrownBy(() -> service.getTicketEarningRuleById(0L)).isInstanceOf(IllegalArgumentException.class);

        when(ruleRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getTicketEarningRuleById(99L)).isInstanceOf(ObjectNotFoundException.class);
    }
}
