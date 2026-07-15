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
import org.springframework.context.ApplicationEventPublisher;

import com.verygana2.dtos.raffle.responses.RaffleTicketResponseDTO;
import com.verygana2.dtos.raffle.responses.TicketEarningResult;
import com.verygana2.event.XpAwardRequestedEvent;
import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.models.enums.UserLevel;
import com.verygana2.models.enums.raffles.RaffleTicketSource;
import com.verygana2.models.enums.raffles.RaffleType;
import com.verygana2.models.enums.raffles.TicketEarningRuleType;
import com.verygana2.models.raffles.Raffle;
import com.verygana2.models.raffles.RaffleRule;
import com.verygana2.models.raffles.TicketEarningRule;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.details.ConsumerDetailsRepository;
import com.verygana2.repositories.raffles.RaffleTicketRepository;
import com.verygana2.services.interfaces.NotificationService;
import com.verygana2.services.interfaces.levels.LevelService;
import com.verygana2.services.interfaces.raffles.RaffleService;
import com.verygana2.services.interfaces.raffles.RaffleTicketService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link TicketDeliveryServiceImpl}: el "pegamento" que otorga
 * tickets automáticamente por compra, login diario y referidos, recorriendo
 * las rifas activas hasta encontrar una con la regla correspondiente activa.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TicketDeliveryServiceImpl")
class TicketDeliveryServiceImplTest {

    @Mock private RaffleTicketService raffleTicketService;
    @Mock private RaffleTicketRepository raffleTicketRepository;
    @Mock private RaffleService raffleService;
    @Mock private NotificationService notificationService;
    @Mock private ConsumerDetailsRepository consumerDetailsRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private LevelService levelService;

    private TicketDeliveryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TicketDeliveryServiceImpl(raffleTicketService, raffleTicketRepository, raffleService,
                notificationService, consumerDetailsRepository, eventPublisher, levelService);
    }

    private Raffle raffleWithRule(TicketEarningRuleType type, Long minPurchaseCents, int ticketsToAward) {
        TicketEarningRule globalRule = new TicketEarningRule();
        globalRule.setRuleType(type);
        globalRule.setMinPurchaseAmountCents(minPurchaseCents);
        globalRule.setTicketsToAward(ticketsToAward);

        RaffleRule raffleRule = new RaffleRule();
        raffleRule.setActive(true);
        raffleRule.setTicketEarningRule(globalRule);

        Raffle raffle = new Raffle();
        raffle.setId(1L);
        raffle.setTitle("Rifa activa");
        raffle.setRaffleType(RaffleType.STANDARD);
        raffle.setDrawDate(ZonedDateTime.now().plusDays(10));
        raffle.setRaffleRules(List.of(raffleRule));
        return raffle;
    }

    @Nested
    @DisplayName("processTicketEarningForPurchase")
    class ForPurchase {

        @Test
        @DisplayName("compra cumple el monto mínimo: emite los tickets de la regla y notifica")
        void meetsMinimum_issuesTicketsAndNotifies() {
            Raffle raffle = raffleWithRule(TicketEarningRuleType.PURCHASE, 50_000L, 2);
            when(raffleService.getActiveRafflesOrderedByDrawDate(any())).thenReturn(List.of(raffle));
            when(raffleTicketRepository.existsByTicketOwnerIdAndSourceAndSourceId(9L, RaffleTicketSource.PURCHASE, 500L))
                    .thenReturn(false);
            when(raffleTicketService.canUserReceiveTickets(9L, RaffleType.STANDARD)).thenReturn(true);
            when(raffleTicketService.issueTickets(9L, 1L, 2, RaffleTicketSource.PURCHASE, 500L))
                    .thenReturn(List.of(new RaffleTicketResponseDTO(), new RaffleTicketResponseDTO()));

            TicketEarningResult result = service.processTicketEarningForPurchase(9L, 500L, 100_000L);

            assertThat(result.getTotalTicketsIssued()).isEqualTo(2);
            verify(notificationService).createInternalNotification(eq(9L), any(), any(), any());
        }

        @Test
        @DisplayName("compra no alcanza el monto mínimo: no emite tickets en esa rifa")
        void belowMinimum_doesNotIssueTickets() {
            Raffle raffle = raffleWithRule(TicketEarningRuleType.PURCHASE, 500_000L, 2);
            when(raffleService.getActiveRafflesOrderedByDrawDate(any())).thenReturn(List.of(raffle));
            when(raffleTicketRepository.existsByTicketOwnerIdAndSourceAndSourceId(9L, RaffleTicketSource.PURCHASE, 500L))
                    .thenReturn(false);
            when(raffleTicketService.canUserReceiveTickets(9L, RaffleType.STANDARD)).thenReturn(true);

            TicketEarningResult result = service.processTicketEarningForPurchase(9L, 500L, 100_000L);

            assertThat(result.getTotalTicketsIssued()).isZero();
            verify(raffleTicketService, never()).issueTickets(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("ya se emitieron tickets para esta compra: es idempotente, no repite la emisión")
        void alreadyIssuedForPurchase_isIdempotent() {
            when(raffleTicketRepository.existsByTicketOwnerIdAndSourceAndSourceId(9L, RaffleTicketSource.PURCHASE, 500L))
                    .thenReturn(true);
            when(raffleService.getActiveRafflesOrderedByDrawDate(any()))
                    .thenReturn(List.of(raffleWithRule(TicketEarningRuleType.PURCHASE, 0L, 1)));

            TicketEarningResult result = service.processTicketEarningForPurchase(9L, 500L, 100_000L);

            assertThat(result.getTotalTicketsIssued()).isZero();
            verify(raffleTicketService, never()).issueTickets(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("sin rifas activas: retorna resultado vacío sin tocar el repositorio de idempotencia")
        void noActiveRaffles_returnsEmptyResult() {
            when(raffleService.getActiveRafflesOrderedByDrawDate(any())).thenReturn(List.of());

            TicketEarningResult result = service.processTicketEarningForPurchase(9L, 500L, 100_000L);

            assertThat(result.getTotalTicketsIssued()).isZero();
            verify(raffleTicketRepository, never()).existsByTicketOwnerIdAndSourceAndSourceId(any(), any(), any());
        }

        @Test
        @DisplayName("parámetros inválidos: lanza InvalidRequestException")
        void invalidInputs_throwInvalidRequestException() {
            assertThatThrownBy(() -> service.processTicketEarningForPurchase(0L, 500L, 100_000L))
                    .isInstanceOf(InvalidRequestException.class);
            assertThatThrownBy(() -> service.processTicketEarningForPurchase(9L, 0L, 100_000L))
                    .isInstanceOf(InvalidRequestException.class);
            assertThatThrownBy(() -> service.processTicketEarningForPurchase(9L, 500L, 0L))
                    .isInstanceOf(InvalidRequestException.class);
        }
    }

    @Nested
    @DisplayName("processTicketEarningForDailyLogin")
    class ForDailyLogin {

        @Test
        @DisplayName("primer login del día: otorga el ticket y actualiza la fecha de último login")
        void firstLoginToday_awardsTicketAndUpdatesDate() {
            ConsumerDetails consumer = new ConsumerDetails();
            consumer.setId(9L);
            consumer.setLastDailyLoginDate(ZonedDateTime.now().minusDays(1));
            when(consumerDetailsRepository.findById(9L)).thenReturn(Optional.of(consumer));

            Raffle raffle = raffleWithRule(TicketEarningRuleType.DAILY_LOGIN, null, 1);
            when(raffleService.getActiveRafflesOrderedByDrawDate(any())).thenReturn(List.of(raffle));
            when(raffleTicketService.issueTickets(eq(9L), eq(1L), eq(1), eq(RaffleTicketSource.DAILY_LOGIN), any()))
                    .thenReturn(List.of(new RaffleTicketResponseDTO()));

            service.processTicketEarningForDailyLogin(9L);

            verify(consumerDetailsRepository).save(consumer);
            verify(raffleTicketService).issueTickets(eq(9L), eq(1L), eq(1), eq(RaffleTicketSource.DAILY_LOGIN), any());
        }

        @Test
        @DisplayName("ya se otorgó el bono hoy: no vuelve a emitir ni a guardar")
        void alreadyAwardedToday_skips() {
            ConsumerDetails consumer = new ConsumerDetails();
            consumer.setId(9L);
            consumer.setLastDailyLoginDate(ZonedDateTime.now());
            when(consumerDetailsRepository.findById(9L)).thenReturn(Optional.of(consumer));

            service.processTicketEarningForDailyLogin(9L);

            verify(consumerDetailsRepository, never()).save(any());
            verify(raffleTicketService, never()).issueTickets(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("sin ConsumerDetails para el usuario: no falla, simplemente no otorga nada")
        void noConsumerDetails_doesNothingSilently() {
            when(consumerDetailsRepository.findById(9L)).thenReturn(Optional.empty());

            service.processTicketEarningForDailyLogin(9L);

            verify(raffleService, never()).getActiveRafflesOrderedByDrawDate(any());
        }

        @Test
        @DisplayName("consumerId inválido: lanza InvalidRequestException")
        void invalidConsumerId_throwsInvalidRequestException() {
            assertThatThrownBy(() -> service.processTicketEarningForDailyLogin(0L))
                    .isInstanceOf(InvalidRequestException.class);
        }
    }

    @Nested
    @DisplayName("processTicketEarningForReferral")
    class ForReferral {

        @Test
        @DisplayName("ya existe un ticket para este referido: es idempotente")
        void alreadyExists_isIdempotent() {
            when(raffleTicketRepository.existsByTicketOwnerIdAndSourceAndSourceId(9L, RaffleTicketSource.REFERRAL, 55L))
                    .thenReturn(true);

            service.processTicketEarningForReferral(9L, 55L);

            verify(raffleService, never()).getActiveRafflesOrderedByDrawDate(any());
        }

        @Test
        @DisplayName("sin rifas activas: otorga XP doble al referidor en vez de tickets")
        void noActiveRaffles_awardsDoubleXpInstead() {
            when(raffleTicketRepository.existsByTicketOwnerIdAndSourceAndSourceId(9L, RaffleTicketSource.REFERRAL, 55L))
                    .thenReturn(false);
            when(raffleService.getActiveRafflesOrderedByDrawDate(any())).thenReturn(List.of());

            service.processTicketEarningForReferral(9L, 55L);

            verify(eventPublisher).publishEvent(any(XpAwardRequestedEvent.class));
        }

        @Test
        @DisplayName("con rifa activa y regla REFERRAL: emite tickets según el nivel del referidor")
        void withActiveRaffleAndRule_issuesTicketsByLevel() {
            when(raffleTicketRepository.existsByTicketOwnerIdAndSourceAndSourceId(9L, RaffleTicketSource.REFERRAL, 55L))
                    .thenReturn(false);
            Raffle raffle = raffleWithRule(TicketEarningRuleType.REFERRAL, null, 0);
            when(raffleService.getActiveRafflesOrderedByDrawDate(any())).thenReturn(List.of(raffle));
            when(levelService.getUserLevel(9L)).thenReturn(UserLevel.BRONCE); // 1 ticket de referido
            when(raffleTicketService.issueTickets(eq(9L), eq(1L), eq(1), eq(RaffleTicketSource.REFERRAL), eq(55L)))
                    .thenReturn(List.of(new RaffleTicketResponseDTO()));

            service.processTicketEarningForReferral(9L, 55L);

            verify(raffleTicketService).issueTickets(eq(9L), eq(1L), eq(1), eq(RaffleTicketSource.REFERRAL), eq(55L));
        }

        @Test
        @DisplayName("nivel bajo (BRONCE) no puede acceder a rifa PREMIUM: la salta")
        void lowLevelSkipsPremiumRaffle() {
            when(raffleTicketRepository.existsByTicketOwnerIdAndSourceAndSourceId(9L, RaffleTicketSource.REFERRAL, 55L))
                    .thenReturn(false);
            Raffle premiumRaffle = raffleWithRule(TicketEarningRuleType.REFERRAL, null, 0);
            premiumRaffle.setRaffleType(RaffleType.PREMIUM);
            when(raffleService.getActiveRafflesOrderedByDrawDate(any())).thenReturn(List.of(premiumRaffle));
            when(levelService.getUserLevel(9L)).thenReturn(UserLevel.BRONCE);

            service.processTicketEarningForReferral(9L, 55L);

            verify(raffleTicketService, never()).issueTickets(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("ids inválidos: lanzan InvalidRequestException")
        void invalidIds_throwInvalidRequestException() {
            assertThatThrownBy(() -> service.processTicketEarningForReferral(0L, 55L))
                    .isInstanceOf(InvalidRequestException.class);
            assertThatThrownBy(() -> service.processTicketEarningForReferral(9L, 0L))
                    .isInstanceOf(InvalidRequestException.class);
        }
    }
}
