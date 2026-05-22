package com.verygana2.services.raffles;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("RaffleEventPublisherServiceImpl")
class RaffleEventPublisherServiceImplTest {

    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock RaffleDrawStateCache drawStateCache;

    @InjectMocks RaffleEventPublisherServiceImpl service;

    // ─── publishDrawingStarted ────────────────────────────────────────────────

    @Nested
    @DisplayName("publishDrawingStarted")
    class PublishDrawingStarted {

        @Test
        @DisplayName("broadcasts DRAWING_STARTED event to raffle topic")
        void broadcastsToTopic() {
            service.publishDrawingStarted(1L, 100L, 3);

            verify(messagingTemplate).convertAndSend(contains("/topic/raffle/1"), any(Object.class));
        }

        @Test
        @DisplayName("updates draw state cache on drawing started")
        void updatesDrawStateCache() {
            service.publishDrawingStarted(1L, 100L, 3);

            verify(drawStateCache).onDrawingStarted(1L, 3);
        }
    }

    // ─── publishDrawCompleted ─────────────────────────────────────────────────

    @Nested
    @DisplayName("publishDrawCompleted")
    class PublishDrawCompleted {

        @Test
        @DisplayName("broadcasts DRAW_COMPLETED event to raffle topic")
        void broadcastsToTopic() {
            service.publishDrawCompleted(2L, java.util.List.of(), "Test Raffle", 50);

            verify(messagingTemplate).convertAndSend(contains("/topic/raffle/2"), any(Object.class));
        }

        @Test
        @DisplayName("marks draw completed in state cache")
        void updatesDrawStateCache() {
            service.publishDrawCompleted(2L, java.util.List.of(), "Test Raffle", 50);

            verify(drawStateCache).onDrawCompleted(2L);
        }
    }

    // ─── publishWaitingRoomUpdate ─────────────────────────────────────────────

    @Nested
    @DisplayName("publishWaitingRoomUpdate")
    class PublishWaitingRoomUpdate {

        @Test
        @DisplayName("builds payload without broadcasting (current implementation has no send call)")
        void doesNotThrow() {
            // The current implementation builds the event but does not call
            // messagingTemplate.convertAndSend — this test simply verifies no exception
            service.publishWaitingRoomUpdate(3L, 10, 120L, 500L);
        }
    }
}
