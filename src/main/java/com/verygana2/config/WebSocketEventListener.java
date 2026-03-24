package com.verygana2.config;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import com.verygana2.services.interfaces.raffles.WaitingRoomService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final WaitingRoomService waitingRoomService;

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.debug("[WS] New connection established. Session: {}", accessor.getSessionId());
    }

    /**
     * Se dispara automáticamente cuando el usuario cierra el navegador,
     * pierde conexión o navega fuera de la página sin mandar /leave.
     * Es el safety net del WaitingRoomService.
     */
    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();

        // Buscar en qué sala estaba este session y removerlo
        waitingRoomService.removeViewerFromAllRooms(sessionId);

        log.info("[WS] Disconnected session: {}. Viewer removed from all rooms.", sessionId);
    }

    /**
     * Loguea cuando un cliente se suscribe a un topic.
     * Útil para debugging y para detectar suscripciones inválidas.
     */
    @EventListener
    public void handleSessionSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.debug("[WS] Suscription to: {} | Session: {}",
                accessor.getDestination(), accessor.getSessionId());
    }
}