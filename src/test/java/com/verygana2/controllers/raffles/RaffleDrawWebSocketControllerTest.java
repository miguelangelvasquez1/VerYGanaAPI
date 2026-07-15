package com.verygana2.controllers.raffles;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import com.verygana2.services.interfaces.raffles.WaitingRoomService;

import static org.mockito.Mockito.verify;

/**
 * Tests de {@link RaffleDrawWebSocketController}: los handlers STOMP que
 * conectan/desconectan a un usuario de la sala de espera de una rifa. Se usa
 * {@link SimpMessageHeaderAccessor} real (no mockeable de forma útil) para
 * simular el sessionId que manda el cliente STOMP.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RaffleDrawWebSocketController")
class RaffleDrawWebSocketControllerTest {

    @Mock private WaitingRoomService waitingRoomService;

    private RaffleDrawWebSocketController controller;

    @BeforeEach
    void setUp() {
        controller = new RaffleDrawWebSocketController(waitingRoomService);
    }

    private SimpMessageHeaderAccessor headerAccessorWithSession(String sessionId) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        accessor.setSessionId(sessionId);
        accessor.setLeaveMutable(true);
        return accessor;
    }

    @Test
    @DisplayName("joinWaitingRoom: agrega al WaitingRoomService al viewer con el sessionId del header STOMP")
    void joinWaitingRoom_addsViewer() {
        controller.joinWaitingRoom(1L, headerAccessorWithSession("session-123"));

        verify(waitingRoomService).addViewer(1L, "session-123");
    }

    @Test
    @DisplayName("leaveWaitingRoom: remueve al viewer con el sessionId del header STOMP")
    void leaveWaitingRoom_removesViewer() {
        controller.leaveWaitingRoom(1L, headerAccessorWithSession("session-123"));

        verify(waitingRoomService).removeViewer(1L, "session-123");
    }
}
