package com.verygana2.services.notifications;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.verygana2.dtos.notification.responses.NotificationResponseDTO;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class NotificationEmitterRegistry {

    static final int MAX_EMITTERS_PER_USER = 5;
    private static final long EMITTER_TIMEOUT_MS = 5 * 60 * 1000L;

    // Varios emitters por usuario (uno por pestaña), del más antiguo al más nuevo.
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(Long userId) {
        SseEmitter emitter = newEmitter();

        // Cada callback retira SOLO su propio emitter. Antes hacían remove(userId) a secas:
        // al completarse el emitter viejo se llevaba al nuevo, que quedaba abierto 5 minutos
        // fuera del registro, sin recibir nada y ocupando una conexión del navegador.
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> {
            remove(userId, emitter);
            emitter.complete();
        });
        emitter.onError(e -> remove(userId, emitter));

        // Tope por usuario: un cliente que abre conexiones sin cerrarlas no puede acumularlas.
        List<SseEmitter> evicted = new ArrayList<>();
        emitters.compute(userId, (id, current) -> {
            List<SseEmitter> list = current != null ? current : new CopyOnWriteArrayList<>();
            list.add(emitter);
            while (list.size() > MAX_EMITTERS_PER_USER) {
                evicted.add(list.remove(0));
            }
            return list;
        });
        evicted.forEach(this::completeQuietly);

        // Primer byte inmediato: el cliente confirma la conexión sin esperar a la primera notificación.
        sendOrRemove(userId, emitter, SseEmitter.event().comment("connected"));

        log.info("SSE registered for userId={} (open={})", userId, connectionCount(userId));
        return emitter;
    }

    public void send(Long userId, NotificationResponseDTO payload) {
        List<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null) return;

        for (SseEmitter emitter : userEmitters) {
            sendOrRemove(userId, emitter, SseEmitter.event().name("notification").data(payload));
        }
    }

    /**
     * Mantiene vivas las conexiones detrás de proxies con timeout por inactividad y
     * detecta las que el cliente cerró sin avisar, que si no solo se descubren al
     * intentar mandarles la siguiente notificación.
     */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        emitters.forEach((userId, userEmitters) -> {
            for (SseEmitter emitter : userEmitters) {
                sendOrRemove(userId, emitter, SseEmitter.event().comment("ping"));
            }
        });
    }

    public boolean isConnected(Long userId) {
        return emitters.containsKey(userId);
    }

    int connectionCount(Long userId) {
        List<SseEmitter> userEmitters = emitters.get(userId);
        return userEmitters == null ? 0 : userEmitters.size();
    }

    // Punto de extensión para los tests, que necesitan un emitter cuyos callbacks puedan disparar.
    protected SseEmitter newEmitter() {
        return new SseEmitter(EMITTER_TIMEOUT_MS);
    }

    private void sendOrRemove(Long userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            // IOException: el cliente se fue. IllegalStateException: el emitter ya estaba completado.
            remove(userId, emitter);
            log.debug("SSE client gone for userId={}, emitter removed", userId);
        }
    }

    private void remove(Long userId, SseEmitter emitter) {
        emitters.computeIfPresent(userId, (id, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }

    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (RuntimeException e) {
            log.debug("SSE emitter already closed: {}", e.getMessage());
        }
    }
}
