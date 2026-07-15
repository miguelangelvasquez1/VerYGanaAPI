package com.verygana2.games;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.dtos.game.EndSessionDTO;
import com.verygana2.dtos.game.GameEventDTO;
import com.verygana2.event.XpAwardRequestedEvent;
import com.verygana2.exceptions.BusinessException;
import com.verygana2.models.enums.ActivityType;
import com.verygana2.models.games.GameSession;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.branding.BrandingRequestRepository;
import com.verygana2.repositories.games.CampaignRepository;
import com.verygana2.repositories.games.GameMetricDefinitionRepository;
import com.verygana2.repositories.games.GameRepository;
import com.verygana2.repositories.games.GameSessionMetricRepository;
import com.verygana2.repositories.games.GameSessionRepository;
import com.verygana2.repositories.marketplace.ProductRepository;
import com.verygana2.services.GameServiceImpl;
import com.verygana2.utils.validators.MetricValidator;

@ExtendWith(MockitoExtension.class)
@DisplayName("GameServiceImpl.completeSession")
class GameServiceCompleteSessionTest {

    @Mock ObjectMapper objectMapper;
    @Mock GameRepository gameRepository;
    @Mock CampaignRepository campaignRepository;
    @Mock BrandingRequestRepository brandingRequestRepository;
    @Mock GameSessionRepository gameSessionRepository;
    @Mock GameMetricDefinitionRepository metricDefinitionRepository;
    @Mock MetricValidator metricValidator;
    @Mock GameSessionMetricRepository gameSessionMetricRepository;
    @Mock ProductRepository productRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks GameServiceImpl service;

    private GameSession session;
    private GameEventDTO<EndSessionDTO> event;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "sessionExpirationTime", 30);

        ConsumerDetails consumer = new ConsumerDetails();
        consumer.setId(42L);

        session = new GameSession();
        session.setSessionToken("token-1");
        session.setUserHash("hash-1");
        session.setConsumer(consumer);
        session.setStartTime(ZonedDateTime.now().minusMinutes(2));
        session.setCompleted(false);

        EndSessionDTO payload = new EndSessionDTO();
        payload.setFinalScore(1500);

        event = new GameEventDTO<>();
        event.setSessionToken("token-1");
        event.setUserHash("hash-1");
        event.setPayload(payload);
    }

    @Test
    @DisplayName("marca la sesión completada y publica XP GAME_PLAYED del consumer dueño")
    void completesSessionAndPublishesGamePlayedXp() {
        when(gameSessionRepository.findBySessionToken("token-1")).thenReturn(Optional.of(session));

        service.completeSession(event, 42L);

        assertThat(session.isCompleted()).isTrue();
        assertThat(session.getEndTime()).isNotNull();
        verify(gameSessionRepository).save(session);

        ArgumentCaptor<XpAwardRequestedEvent> captor =
                ArgumentCaptor.forClass(XpAwardRequestedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getConsumerId()).isEqualTo(42L);
        assertThat(captor.getValue().getActivityType()).isEqualTo(ActivityType.GAME_PLAYED);
    }

    @Test
    @DisplayName("sesión ya completada: rechaza el reintento y NO duplica el XP")
    void alreadyCompletedSessionRejectsRetryWithoutDuplicateXp() {
        session.setCompleted(true);
        when(gameSessionRepository.findBySessionToken("token-1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.completeSession(event, 42L))
                .isInstanceOf(BusinessException.class);

        verify(eventPublisher, never()).publishEvent(any(ApplicationEvent.class));
        verify(gameSessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("sesión expirada: no publica XP")
    void expiredSessionDoesNotPublishXp() {
        session.setStartTime(ZonedDateTime.now().minusMinutes(60));
        when(gameSessionRepository.findBySessionToken("token-1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.completeSession(event, 42L))
                .isInstanceOf(BusinessException.class);

        verify(eventPublisher, never()).publishEvent(any(ApplicationEvent.class));
    }

    @Test
    @DisplayName("hash ajeno: no publica XP")
    void foreignHashDoesNotPublishXp() {
        event.setUserHash("hash-de-otro");
        when(gameSessionRepository.findBySessionToken("token-1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.completeSession(event, 42L))
                .isInstanceOf(RuntimeException.class);

        verify(eventPublisher, never()).publishEvent(any(ApplicationEvent.class));
    }
}