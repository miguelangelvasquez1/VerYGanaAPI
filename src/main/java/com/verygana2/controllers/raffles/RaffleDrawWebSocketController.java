package com.verygana2.controllers.raffles;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.services.interfaces.raffles.WaitingRoomService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequiredArgsConstructor
@Slf4j
public class RaffleDrawWebSocketController {
    
    private final WaitingRoomService waitingRoomService;

     /**
     * El cliente envía este mensaje al conectarse a la sala de espera.
     * Destino del cliente: /app/raffle/{raffleId}/join
     */
    @MessageMapping("/raffle/{raffleId}/join")
    public void joinWaitingRoom(
            @DestinationVariable Long raffleId,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        waitingRoomService.addViewer(raffleId, sessionId);

        log.info("[WS] Usuario se unió a sala de espera. Rifa: {}, Session: {}", raffleId, sessionId);
    }

    /**
     * El cliente envía este mensaje al salir de la página.
     * Destino del cliente: /app/raffle/{raffleId}/leave
     */
    @MessageMapping("/raffle/{raffleId}/leave")
    public void leaveWaitingRoom(
            @DestinationVariable Long raffleId,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        waitingRoomService.removeViewer(raffleId, sessionId);

        log.info("[WS] Usuario salió de sala de espera. Rifa: {}, Session: {}", raffleId, sessionId);
    }
}
