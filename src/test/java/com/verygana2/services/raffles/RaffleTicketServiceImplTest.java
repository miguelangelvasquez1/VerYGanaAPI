package com.verygana2.services.raffles;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.dtos.raffle.responses.RaffleTicketResponseDTO;
import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.exceptions.rafflesExceptions.LimitReachedException;
import com.verygana2.mappers.raffles.RaffleTicketMapper;
import com.verygana2.mappers.raffles.TicketAuditLogMapper;
import com.verygana2.models.enums.raffles.RaffleStatus;
import com.verygana2.models.enums.raffles.RaffleTicketSource;
import com.verygana2.models.enums.raffles.RaffleTicketStatus;
import com.verygana2.models.enums.raffles.RaffleType;
import com.verygana2.models.raffles.Raffle;
import com.verygana2.models.raffles.RaffleRule;
import com.verygana2.models.raffles.RaffleTicket;
import com.verygana2.models.raffles.TicketEarningRule;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.raffles.RaffleParticipationRepository;
import com.verygana2.repositories.raffles.RaffleRepository;
import com.verygana2.repositories.raffles.RaffleRuleRespository;
import com.verygana2.repositories.raffles.RaffleTicketRepository;
import com.verygana2.repositories.raffles.TicketAuditLogRepository;
import com.verygana2.services.interfaces.details.ConsumerDetailsService;
import com.verygana2.services.interfaces.raffles.RaffleRuleService;
import com.verygana2.services.interfaces.raffles.RaffleService;
import com.verygana2.utils.audit.AuditContextService;
import com.verygana2.utils.validators.TargetAudienceAssembler;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link RaffleTicketServiceImpl}: la emisión de tickets con sus
 * validaciones en cascada (rifa activa y vigente, elegibilidad por tipo de
 * rifa, límites total/por fuente/por usuario) y las consultas de balance.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RaffleTicketServiceImpl")
class RaffleTicketServiceImplTest {

    @Mock private RaffleTicketRepository raffleTicketRepository;
    @Mock private RaffleRepository raffleRepository;
    @Mock private RaffleRuleRespository raffleRuleRespository;
    @Mock private RaffleRuleService raffleRuleService;
    @Mock private RaffleService raffleService;
    @Mock private RaffleTicketMapper raffleTicketMapper;
    @Mock private ConsumerDetailsService consumerDetailsService;
    @Mock private RaffleParticipationRepository participationRepository;
    @Mock private TicketAuditLogRepository auditLogRepository;
    @Mock private AuditContextService auditContextService;
    @Mock private TicketAuditLogMapper ticketAuditLogMapper;
    @Mock private TargetAudienceAssembler targetAudienceAssembler;

    private RaffleTicketServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RaffleTicketServiceImpl(raffleTicketRepository, raffleRepository, raffleRuleRespository,
                raffleRuleService, raffleService, raffleTicketMapper, consumerDetailsService, participationRepository,
                auditLogRepository, auditContextService, new ObjectMapper(), ticketAuditLogMapper,
                targetAudienceAssembler);
    }

    private Raffle activeRaffle(RaffleType type) {
        Raffle raffle = new Raffle();
        raffle.setId(1L);
        raffle.setRaffleStatus(RaffleStatus.ACTIVE);
        raffle.setRaffleType(type);
        raffle.setEndDate(ZonedDateTime.now().plusDays(5));
        raffle.setTotalTicketsIssued(0);
        raffle.setTotalParticipants(0);
        return raffle;
    }

    private ConsumerDetails consumer(boolean hasPet) {
        ConsumerDetails consumer = new ConsumerDetails();
        consumer.setId(9L);
        consumer.setHasPet(hasPet);
        return consumer;
    }

    private RaffleRule openRule() {
        RaffleRule rule = new RaffleRule();
        rule.setMaxTicketsBySource(null);
        rule.setCurrentTicketsBySource(0L);
        TicketEarningRule global = new TicketEarningRule();
        global.setActive(true);
        rule.setTicketEarningRule(global);
        rule.setActive(true);
        return rule;
    }

    @Nested
    @DisplayName("issueTickets")
    class IssueTickets {

        @Test
        @DisplayName("rifa activa, usuario elegible, sin límites: emite los tickets solicitados")
        void happyPath_issuesTickets() {
            Raffle raffle = activeRaffle(RaffleType.STANDARD);
            when(raffleService.getRaffleById(1L)).thenReturn(raffle);
            when(consumerDetailsService.getConsumerById(9L)).thenReturn(consumer(false));
            when(raffleRuleService.getByRaffleIdAndRuleType(1L, com.verygana2.models.enums.raffles.TicketEarningRuleType.PURCHASE))
                    .thenReturn(openRule());
            when(raffleTicketRepository.saveAll(org.mockito.ArgumentMatchers.<List<RaffleTicket>>any()))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(raffleTicketMapper.toRaffleTicketResponseDTO(any())).thenReturn(new RaffleTicketResponseDTO());
            when(participationRepository.findByConsumerIdAndRaffleId(9L, 1L)).thenReturn(Optional.empty());

            List<RaffleTicketResponseDTO> tickets = service.issueTickets(9L, 1L, 2, RaffleTicketSource.PURCHASE, 100L);

            assertThat(tickets).hasSize(2);
            assertThat(raffle.getTotalTicketsIssued()).isEqualTo(2);
        }

        @Test
        @DisplayName("sourceId nulo o no positivo: lanza InvalidRequestException antes de tocar la rifa")
        void invalidSourceId_throwsInvalidRequestException() {
            assertThatThrownBy(() -> service.issueTickets(9L, 1L, 1, RaffleTicketSource.PURCHASE, null))
                    .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        @DisplayName("rifa que no está ACTIVE: lanza InvalidRequestException")
        void raffleNotActive_throwsInvalidRequestException() {
            Raffle raffle = activeRaffle(RaffleType.STANDARD);
            raffle.setRaffleStatus(RaffleStatus.DRAFT);
            when(raffleService.getRaffleById(1L)).thenReturn(raffle);

            assertThatThrownBy(() -> service.issueTickets(9L, 1L, 1, RaffleTicketSource.PURCHASE, 100L))
                    .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        @DisplayName("rifa ya finalizada (endDate pasado): lanza InvalidRequestException")
        void raffleEnded_throwsInvalidRequestException() {
            Raffle raffle = activeRaffle(RaffleType.STANDARD);
            raffle.setEndDate(ZonedDateTime.now().minusDays(1));
            when(raffleService.getRaffleById(1L)).thenReturn(raffle);

            assertThatThrownBy(() -> service.issueTickets(9L, 1L, 1, RaffleTicketSource.PURCHASE, 100L))
                    .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        @DisplayName("rifa PREMIUM y usuario sin mascota registrada: lanza InvalidRequestException")
        void premiumRaffleWithoutPet_throwsInvalidRequestException() {
            Raffle raffle = activeRaffle(RaffleType.PREMIUM);
            when(raffleService.getRaffleById(1L)).thenReturn(raffle);
            when(consumerDetailsService.getConsumerById(9L)).thenReturn(consumer(false));

            assertThatThrownBy(() -> service.issueTickets(9L, 1L, 1, RaffleTicketSource.PURCHASE, 100L))
                    .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        @DisplayName("la rifa ya alcanzó el límite total de tickets: lanza LimitReachedException")
        void totalLimitReached_throwsLimitReachedException() {
            Raffle raffle = activeRaffle(RaffleType.STANDARD);
            raffle.setMaxTotalTickets(10);
            raffle.setTotalTicketsIssued(10);
            when(raffleService.getRaffleById(1L)).thenReturn(raffle);
            when(consumerDetailsService.getConsumerById(9L)).thenReturn(consumer(false));

            assertThatThrownBy(() -> service.issueTickets(9L, 1L, 1, RaffleTicketSource.PURCHASE, 100L))
                    .isInstanceOf(LimitReachedException.class);
        }

        @Test
        @DisplayName("la fuente ya alcanzó su límite específico: lanza LimitReachedException")
        void sourceLimitReached_throwsLimitReachedException() {
            Raffle raffle = activeRaffle(RaffleType.STANDARD);
            when(raffleService.getRaffleById(1L)).thenReturn(raffle);
            when(consumerDetailsService.getConsumerById(9L)).thenReturn(consumer(false));

            RaffleRule exhaustedRule = openRule();
            exhaustedRule.setMaxTicketsBySource(5L);
            exhaustedRule.setCurrentTicketsBySource(5L);
            when(raffleRuleService.getByRaffleIdAndRuleType(1L, com.verygana2.models.enums.raffles.TicketEarningRuleType.PURCHASE))
                    .thenReturn(exhaustedRule);

            assertThatThrownBy(() -> service.issueTickets(9L, 1L, 1, RaffleTicketSource.PURCHASE, 100L))
                    .isInstanceOf(LimitReachedException.class);
        }

        @Test
        @DisplayName("el usuario ya alcanzó su límite por rifa: lanza LimitReachedException")
        void perUserLimitReached_throwsLimitReachedException() {
            Raffle raffle = activeRaffle(RaffleType.STANDARD);
            raffle.setMaxTicketsPerUser(3);
            when(raffleService.getRaffleById(1L)).thenReturn(raffle);
            when(consumerDetailsService.getConsumerById(9L)).thenReturn(consumer(false));
            when(raffleRuleService.getByRaffleIdAndRuleType(1L, com.verygana2.models.enums.raffles.TicketEarningRuleType.PURCHASE))
                    .thenReturn(openRule());
            when(raffleTicketRepository.countByTicketOwnerIdAndRaffleIdAndStatus(9L, 1L, RaffleTicketStatus.ACTIVE))
                    .thenReturn(3L);

            assertThatThrownBy(() -> service.issueTickets(9L, 1L, 1, RaffleTicketSource.PURCHASE, 100L))
                    .isInstanceOf(LimitReachedException.class);
        }
    }

    @Nested
    @DisplayName("canUserReceiveTickets")
    class CanUserReceiveTickets {

        @Test
        @DisplayName("rifa STANDARD: cualquier usuario es elegible, no consulta el consumidor")
        void standardRaffle_alwaysEligible() {
            assertThat(service.canUserReceiveTickets(9L, RaffleType.STANDARD)).isTrue();
            org.mockito.Mockito.verifyNoInteractions(consumerDetailsService);
        }

        @Test
        @DisplayName("rifa PREMIUM: depende de si el consumidor tiene mascota registrada")
        void premiumRaffle_dependsOnPet() {
            when(consumerDetailsService.getConsumerById(9L)).thenReturn(consumer(true));
            assertThat(service.canUserReceiveTickets(9L, RaffleType.PREMIUM)).isTrue();
        }

        @Test
        @DisplayName("id inválido: lanza IllegalArgumentException")
        void invalidId_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> service.canUserReceiveTickets(0L, RaffleType.STANDARD))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("expireTickets: raffleId inválido lanza IllegalArgumentException")
    void expireTickets_invalidId_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.expireTickets(-1L)).isInstanceOf(IllegalArgumentException.class);
    }
}
