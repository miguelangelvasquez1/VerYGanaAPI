package com.verygana2.services.raffles;

import java.time.ZoneOffset;
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
import org.springframework.jmx.access.InvalidInvocationException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.verygana2.dtos.raffle.responses.DrawResultResponseDTO;
import com.verygana2.exceptions.rafflesExceptions.InvalidOperationException;
import com.verygana2.exceptions.rafflesExceptions.InvalidRaffleStatusException;
import com.verygana2.models.Avatar;
import com.verygana2.models.enums.raffles.DrawMethod;
import com.verygana2.models.enums.raffles.PrizeType;
import com.verygana2.models.enums.raffles.RaffleStatus;
import com.verygana2.models.enums.raffles.RaffleTicketStatus;
import com.verygana2.models.raffles.Prize;
import com.verygana2.models.raffles.Raffle;
import com.verygana2.models.raffles.RaffleResult;
import com.verygana2.models.raffles.RaffleTicket;
import com.verygana2.models.raffles.RaffleWinner;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.NotificationRepository;
import com.verygana2.repositories.raffles.PrizeRepository;
import com.verygana2.repositories.raffles.RaffleRepository;
import com.verygana2.repositories.raffles.RaffleResultRepository;
import com.verygana2.repositories.raffles.RaffleTicketRepository;
import com.verygana2.repositories.raffles.RaffleWinnerRepository;
import com.verygana2.services.interfaces.raffles.RaffleEventPublisherService;
import com.verygana2.services.interfaces.raffles.RaffleResultService;
import com.verygana2.services.interfaces.raffles.RaffleService;
import com.verygana2.services.interfaces.raffles.RandomOrgService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link DrawingServiceImpl}: el núcleo del sistema de rifas — el
 * sorteo en sí (interno y su fallback), la generación de la prueba
 * criptográfica del sorteo y la verificación de integridad posterior.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DrawingServiceImpl")
class DrawingServiceImplTest {

    @Mock private RaffleRepository raffleRepository;
    @Mock private RaffleResultRepository raffleResultRepository;
    @Mock private RaffleTicketRepository raffleTicketRepository;
    @Mock private RaffleService raffleService;
    @Mock private RaffleEventPublisherService raffleEventPublisherService;
    @Mock private RaffleResultService raffleResultService;
    @Mock private RandomOrgService randomOrgService;
    @Mock private PrizeRepository prizeRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private RaffleWinnerRepository raffleWinnerRepository;

    private DrawingServiceImpl service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new DrawingServiceImpl(raffleRepository, raffleResultRepository, raffleTicketRepository,
                raffleService, raffleEventPublisherService, raffleResultService, randomOrgService, prizeRepository,
                notificationRepository, raffleWinnerRepository, objectMapper);
    }

    private RaffleTicket ticket(Long id, ConsumerDetails owner) {
        RaffleTicket ticket = new RaffleTicket();
        ticket.setId(id);
        ticket.setTicketNumber(String.format("%06d", id));
        ticket.setTicketOwner(owner);
        ticket.setStatus(RaffleTicketStatus.ACTIVE);
        return ticket;
    }

    private ConsumerDetails consumer(Long id, String name) {
        ConsumerDetails consumer = new ConsumerDetails();
        consumer.setId(id);
        consumer.setUserName(name);
        Avatar avatar = new Avatar();
        avatar.setImageUrl("avatar.jpg");
        consumer.setAvatar(avatar);
        return consumer;
    }

    // ─── randomInternalDraw ─────────────────────────────────────────────────

    @Nested
    @DisplayName("randomInternalDraw")
    class RandomInternalDraw {

        @Test
        @DisplayName("selecciona exactamente numberOfWinners tickets distintos y los marca como ganadores")
        void selectsExactWinnersAndMarksThem() {
            ConsumerDetails owner = consumer(1L, "Ana");
            List<RaffleTicket> tickets = List.of(ticket(1L, owner), ticket(2L, owner), ticket(3L, owner));

            List<RaffleTicket> winners = service.randomInternalDraw(tickets, 2);

            assertThat(winners).hasSize(2);
            assertThat(winners).allMatch(RaffleTicket::getIsWinner);
            assertThat(winners).doesNotHaveDuplicates();
            verify(raffleTicketRepository).saveAll(winners);
        }

        @Test
        @DisplayName("lista de tickets vacía: lanza InvalidOperationException")
        void emptyTickets_throwsInvalidOperationException() {
            assertThatThrownBy(() -> service.randomInternalDraw(List.of(), 1))
                    .isInstanceOf(InvalidOperationException.class);
        }

        @Test
        @DisplayName("numberOfWinners no positivo: lanza InvalidOperationException")
        void nonPositiveWinners_throwsInvalidOperationException() {
            List<RaffleTicket> tickets = List.of(ticket(1L, consumer(1L, "Ana")));
            assertThatThrownBy(() -> service.randomInternalDraw(tickets, 0))
                    .isInstanceOf(InvalidOperationException.class);
        }

        @Test
        @DisplayName("pide más ganadores que tickets disponibles: lanza InvalidOperationException")
        void moreWinnersThanTickets_throwsInvalidOperationException() {
            List<RaffleTicket> tickets = List.of(ticket(1L, consumer(1L, "Ana")));
            assertThatThrownBy(() -> service.randomInternalDraw(tickets, 5))
                    .isInstanceOf(InvalidOperationException.class);
        }
    }

    // ─── verifyDrawIntegrity ────────────────────────────────────────────────

    @Nested
    @DisplayName("verifyDrawIntegrity")
    class VerifyDrawIntegrity {

        private RaffleResult resultWith(String drawProof, RaffleStatus raffleStatus) {
            Raffle raffle = new Raffle();
            raffle.setRaffleStatus(raffleStatus);
            RaffleResult result = new RaffleResult();
            result.setId(1L);
            result.setDrawProof(drawProof);
            result.setRaffle(raffle);
            return result;
        }

        @Test
        @DisplayName("sin draw proof: retorna false")
        void noProof_returnsFalse() {
            when(raffleResultService.getByRaffleId(1L)).thenReturn(resultWith(null, RaffleStatus.COMPLETED));
            assertThat(service.verifyDrawIntegrity(1L)).isFalse();
        }

        @Test
        @DisplayName("rifa no está COMPLETED: retorna false")
        void notCompleted_returnsFalse() {
            when(raffleResultService.getByRaffleId(1L)).thenReturn(resultWith("{}", RaffleStatus.LIVE));
            assertThat(service.verifyDrawIntegrity(1L)).isFalse();
        }

        @Test
        @DisplayName("sin ganadores registrados: retorna false")
        void noWinners_returnsFalse() {
            when(raffleResultService.getByRaffleId(1L)).thenReturn(resultWith("{}", RaffleStatus.COMPLETED));
            when(raffleWinnerRepository.countByRaffleResultId(1L)).thenReturn(0L);

            assertThat(service.verifyDrawIntegrity(1L)).isFalse();
        }

        @Test
        @DisplayName("draw proof con JSON inválido: retorna false")
        void invalidJson_returnsFalse() {
            when(raffleResultService.getByRaffleId(1L)).thenReturn(resultWith("{not valid json", RaffleStatus.COMPLETED));
            when(raffleWinnerRepository.countByRaffleResultId(1L)).thenReturn(1L);

            assertThat(service.verifyDrawIntegrity(1L)).isFalse();
        }

        @Test
        @DisplayName("proof presente, COMPLETED, con ganadores y JSON válido: retorna true")
        void allValid_returnsTrue() {
            when(raffleResultService.getByRaffleId(1L))
                    .thenReturn(resultWith("{\"raffleId\":1}", RaffleStatus.COMPLETED));
            when(raffleWinnerRepository.countByRaffleResultId(1L)).thenReturn(3L);

            assertThat(service.verifyDrawIntegrity(1L)).isTrue();
        }
    }

    // ─── conductDraw: validaciones ──────────────────────────────────────────

    @Nested
    @DisplayName("conductDraw — validaciones")
    class ConductDrawValidations {

        @Test
        @DisplayName("raffleId inválido: lanza IllegalArgumentException")
        void invalidId_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> service.conductDraw(0L)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rifa que no está LIVE: lanza InvalidRaffleStatusException")
        void notLive_throwsInvalidRaffleStatusException() {
            Raffle raffle = new Raffle();
            raffle.setRaffleStatus(RaffleStatus.ACTIVE);
            when(raffleService.getRaffleById(1L)).thenReturn(raffle);

            assertThatThrownBy(() -> service.conductDraw(1L)).isInstanceOf(InvalidRaffleStatusException.class);
        }

        @Test
        @DisplayName("aún no llega la fecha de sorteo: lanza InvalidOperationException")
        void beforeDrawDate_throwsInvalidOperationException() {
            Raffle raffle = new Raffle();
            raffle.setRaffleStatus(RaffleStatus.LIVE);
            raffle.setDrawMethod(DrawMethod.SYSTEM_RANDOM);
            raffle.setDrawDate(ZonedDateTime.now(ZoneOffset.UTC).plusDays(1));
            when(raffleService.getRaffleById(1L)).thenReturn(raffle);

            assertThatThrownBy(() -> service.conductDraw(1L)).isInstanceOf(InvalidOperationException.class);
        }

        @Test
        @DisplayName("sin método de sorteo configurado: lanza InvalidOperationException")
        void noDrawMethod_throwsInvalidOperationException() {
            Raffle raffle = new Raffle();
            raffle.setRaffleStatus(RaffleStatus.LIVE);
            raffle.setDrawMethod(null);
            raffle.setDrawDate(ZonedDateTime.now(ZoneOffset.UTC).minusMinutes(1));
            when(raffleService.getRaffleById(1L)).thenReturn(raffle);

            assertThatThrownBy(() -> service.conductDraw(1L)).isInstanceOf(InvalidOperationException.class);
        }

        @Test
        @DisplayName("rifa sin premios: lanza InvalidOperationException")
        void noPrizes_throwsInvalidOperationException() {
            Raffle raffle = validRaffleReadyToDraw(1L);
            when(raffleService.getRaffleById(1L)).thenReturn(raffle);
            when(prizeRepository.findByRaffleIdOrderByPositionAsc(1L)).thenReturn(List.of());

            assertThatThrownBy(() -> service.conductDraw(1L)).isInstanceOf(InvalidOperationException.class);
        }

        @Test
        @DisplayName("rifa sin tickets activos: lanza InvalidInvocationException")
        void noActiveTickets_throwsInvalidInvocationException() {
            Raffle raffle = validRaffleReadyToDraw(1L);
            when(raffleService.getRaffleById(1L)).thenReturn(raffle);
            when(prizeRepository.findByRaffleIdOrderByPositionAsc(1L)).thenReturn(List.of(new Prize()));
            when(raffleTicketRepository.findByRaffleIdAndStatus(1L, RaffleTicketStatus.ACTIVE)).thenReturn(List.of());

            assertThatThrownBy(() -> service.conductDraw(1L)).isInstanceOf(InvalidInvocationException.class);
        }
    }

    // ─── conductDraw — camino feliz (SYSTEM_RANDOM) ────────────────────────

    @Test
    @DisplayName("conductDraw feliz con SYSTEM_RANDOM: sortea, crea ganadores, genera proof y completa la rifa")
    void conductDraw_happyPath_systemRandom() {
        Raffle raffle = validRaffleReadyToDraw(1L);
        raffle.setTitle("Rifa de prueba");
        raffle.setTotalParticipants(5);
        raffle.setTotalTicketsIssued(3);
        when(raffleService.getRaffleById(1L)).thenReturn(raffle);

        Prize prize = new Prize();
        prize.setId(10L);
        prize.setTitle("iPhone");
        prize.setPosition(1);
        prize.setQuantity(1);
        prize.setValue(java.math.BigDecimal.valueOf(5_000_000));
        prize.setPrizeType(PrizeType.PHYSICAL);
        when(prizeRepository.findByRaffleIdOrderByPositionAsc(1L)).thenReturn(List.of(prize));

        ConsumerDetails owner = consumer(9L, "Carlos");
        List<RaffleTicket> activeTickets = List.of(ticket(1L, owner), ticket(2L, owner), ticket(3L, owner));
        when(raffleTicketRepository.findByRaffleIdAndStatus(1L, RaffleTicketStatus.ACTIVE)).thenReturn(activeTickets);

        when(raffleRepository.save(any(Raffle.class))).thenAnswer(inv -> inv.getArgument(0));
        when(raffleResultRepository.save(any(RaffleResult.class))).thenAnswer(inv -> {
            RaffleResult r = inv.getArgument(0);
            r.setId(100L);
            return r;
        });
        when(raffleWinnerRepository.saveAll(org.mockito.ArgumentMatchers.<List<RaffleWinner>>any()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(raffleWinnerRepository.findByRaffleResultId(100L)).thenReturn(List.of());

        DrawResultResponseDTO response = service.conductDraw(1L);

        assertThat(response.getNumberOfWinners()).isEqualTo(1);
        assertThat(raffle.getRaffleStatus()).isEqualTo(RaffleStatus.COMPLETED);
        verify(raffleEventPublisherService).publishDrawingStarted(1L, 1, 3, raffle.getMaxTotalTickets());
        verify(raffleEventPublisherService).publishWinnersWithDelay(org.mockito.ArgumentMatchers.eq(1L), any(), org.mockito.ArgumentMatchers.eq("Rifa de prueba"));
        verify(raffleTicketRepository).expireTicketsByRaffle(org.mockito.ArgumentMatchers.eq(1L), any());
    }

    private Raffle validRaffleReadyToDraw(Long id) {
        Raffle raffle = new Raffle();
        raffle.setId(id);
        raffle.setRaffleStatus(RaffleStatus.LIVE);
        raffle.setDrawMethod(DrawMethod.SYSTEM_RANDOM);
        raffle.setDrawDate(ZonedDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        raffle.setMaxTotalTickets(1000);
        return raffle;
    }
}
