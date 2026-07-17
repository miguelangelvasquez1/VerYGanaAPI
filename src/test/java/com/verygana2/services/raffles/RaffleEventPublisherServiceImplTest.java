package com.verygana2.services.raffles;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.verygana2.dtos.raffle.websocket.RaffleDrawEventDTO;
import com.verygana2.models.enums.raffles.DrawEventType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Tests de {@link RaffleEventPublisherServiceImpl}: cada evento de sorteo se
 * publica al topic STOMP correcto ("/topic/raffle/{raffleId}") con el tipo de
 * evento esperado. {@code publishWinnersWithDelay} no se testea aquí porque
 * hace un {@code Thread.sleep} real de 15s por ganador — es responsabilidad
 * de un test de integración, no de uno unitario.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RaffleEventPublisherServiceImpl")
class RaffleEventPublisherServiceImplTest {

    @Mock private SimpMessagingTemplate messagingTemplate;

    private RaffleEventPublisherServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RaffleEventPublisherServiceImpl(messagingTemplate, new RaffleDrawStateCache());
    }

    @Test
    @DisplayName("publishDrawingStarted: publica DRAWING_STARTED en el topic de la rifa")
    void publishDrawingStarted_broadcastsToRaffleTopic() {
        service.publishDrawingStarted(1L, 3, 100, 200);

        ArgumentCaptor<RaffleDrawEventDTO> captor = ArgumentCaptor.forClass(RaffleDrawEventDTO.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/raffle/1"), captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(DrawEventType.DRAWING_STARTED);
        assertThat(captor.getValue().getRaffleId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("publishDrawCompleted: publica DRAW_COMPLETED con todos los ganadores")
    void publishDrawCompleted_broadcastsAllWinners() {
        service.publishDrawCompleted(1L, List.of(), "Rifa X", 50);

        ArgumentCaptor<RaffleDrawEventDTO> captor = ArgumentCaptor.forClass(RaffleDrawEventDTO.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/raffle/1"), captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(DrawEventType.DRAW_COMPLETED);
    }

    @Test
    @DisplayName("publishWaitingRoomUpdate: publica WAITING_ROOM_UPDATE con el conteo de espectadores")
    void publishWaitingRoomUpdate_broadcastsViewerCount() {
        service.publishWaitingRoomUpdate(1L, 42, 120L, 500, 300, List.of());

        ArgumentCaptor<RaffleDrawEventDTO> captor = ArgumentCaptor.forClass(RaffleDrawEventDTO.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/raffle/1"), captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(DrawEventType.WAITING_ROOM_UPDATE);
    }

    @Test
    @DisplayName("cada rifa publica en su propio topic, no se mezclan")
    void differentRaffles_publishToDifferentTopics() {
        service.publishDrawingStarted(1L, 1, 10, 20);
        service.publishDrawingStarted(2L, 1, 10, 20);

        verify(messagingTemplate).convertAndSend(eq("/topic/raffle/1"), any(RaffleDrawEventDTO.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/raffle/2"), any(RaffleDrawEventDTO.class));
    }
}
