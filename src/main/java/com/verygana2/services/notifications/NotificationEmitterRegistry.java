package com.verygana2.services.notifications;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.verygana2.dtos.notification.responses.NotificationResponseDTO;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class NotificationEmitterRegistry {

    // Un emitter por usuario (puede escalar a lista si el usuario tiene múltiples tabs)
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(Long userId) {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);

        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> {
            emitters.remove(userId);
            emitter.complete();
        });
        emitter.onError(e -> emitters.remove(userId));

        SseEmitter previous = emitters.put(userId, emitter);
        if (previous != null) {
            previous.complete();
        }
        log.info("SSE registered for userId={}", userId);
        return emitter;
    }

    public void send(Long userId, NotificationResponseDTO payload) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) return;

        try {
            emitter.send(
                SseEmitter.event()
                    .name("notification")
                    .data(payload)
            );
        } catch (IOException e) {
            emitters.remove(userId);
            log.warn("SSE send failed for userId={}, emitter removed", userId);
        }
    }

    public boolean isConnected(Long userId) {
        return emitters.containsKey(userId);
    }
}
