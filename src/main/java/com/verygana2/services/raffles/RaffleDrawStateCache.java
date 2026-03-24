package com.verygana2.services.raffles;


import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.verygana2.dtos.raffle.responses.DrawStatusResponseDTO;
import com.verygana2.dtos.raffle.websocket.WinnerRevealPayloadDTO;
import com.verygana2.models.enums.raffles.DrawEventType;
import com.verygana2.models.raffles.Raffle;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class RaffleDrawStateCache {

    // Clase interna que representa el estado completo de un sorteo en curso
    @Data
    private static class DrawState {
        DrawEventType phase;
        List<WinnerRevealPayloadDTO> revealedWinners;
        int totalWinners;
        ZonedDateTime updatedAt;

        DrawState(DrawEventType phase, int totalWinners) {
            this.phase = phase;
            this.totalWinners = totalWinners;
            this.revealedWinners = new ArrayList<>();
            this.updatedAt = ZonedDateTime.now(ZoneId.from(ZoneOffset.UTC));
        }
    }

    private final Map<Long, DrawState> cache = new ConcurrentHashMap<>();

    public void onDrawingStarted(Long raffleId, int totalWinners) {
        cache.put(raffleId, new DrawState(DrawEventType.DRAWING_STARTED, totalWinners));
        log.debug("[DrawStateCache] Rifa {} -> DRAWING_STARTED", raffleId);
    }

    public void onWinnerRevealed(Long raffleId, WinnerRevealPayloadDTO winner) {
        DrawState state = cache.get(raffleId);
        if (state != null) {
            state.revealedWinners.add(winner);
            state.phase = DrawEventType.WINNER_REVEALED;
            state.updatedAt = ZonedDateTime.now(ZoneId.of("America/Bogota"));
        }
    }

    public void onDrawCompleted(Long raffleId) {
        DrawState state = cache.get(raffleId);
        if (state != null) {
            state.phase = DrawEventType.DRAW_COMPLETED;
            state.updatedAt = ZonedDateTime.now(ZoneId.of("America/Bogota"));
        }
    }

    public void onDrawError(Long raffleId) {
        DrawState state = cache.get(raffleId);
        if (state != null) {
            state.phase = DrawEventType.DRAW_ERROR;
            state.updatedAt = ZonedDateTime.now(ZoneId.of("America/Bogota"));
        }
    }

    public void evict(Long raffleId) {
        cache.remove(raffleId);
        log.debug("[DrawStateCache] Estado de rifa {} eliminado del cache", raffleId);
    }

    public boolean exists(Long raffleId) {
        return cache.containsKey(raffleId);
    }

    /**
     * Construye el DTO de respuesta para el endpoint draw-status.
     * Devuelve null si no hay estado en cache para esa rifa
     * (significa que el sorteo no ha empezado aún).
     */
    public DrawStatusResponseDTO buildStatus(Long raffleId, int viewerCount,
                                              Long secondsUntilDraw, Raffle raffle) {
        DrawState state = cache.get(raffleId);

        // Sorteo aún no empezó — está en sala de espera
        if (state == null) {
            return DrawStatusResponseDTO.builder()
                    .currentPhase(DrawEventType.WAITING_ROOM_UPDATE)
                    .secondsUntilDraw(secondsUntilDraw)
                    .viewerCount(viewerCount)
                    .revealedWinners(List.of())
                    .totalWinners(0)
                    .asOf(ZonedDateTime.now(ZoneId.of("America/Bogota")))
                    .build();
        }

        String proofUrl = state.phase == DrawEventType.DRAW_COMPLETED
                ? "/api/raffles/" + raffleId + "/draw-proof"
                : null;

        return DrawStatusResponseDTO.builder()
                .currentPhase(state.phase)
                .secondsUntilDraw(secondsUntilDraw)
                .viewerCount(viewerCount)
                .revealedWinners(new ArrayList<>(state.revealedWinners))
                .totalWinners(state.totalWinners)
                .drawProofUrl(proofUrl)
                .asOf(ZonedDateTime.now(ZoneId.of("America/Bogota")))
                .build();
    }
}
