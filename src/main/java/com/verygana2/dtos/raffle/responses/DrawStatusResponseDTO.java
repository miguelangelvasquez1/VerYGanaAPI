package com.verygana2.dtos.raffle.responses;

import java.time.ZonedDateTime;
import java.util.List;

import com.verygana2.dtos.raffle.websocket.WinnerRevealPayloadDTO;
import com.verygana2.models.enums.raffles.DrawEventType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DrawStatusResponseDTO {

    // Fase actual del sorteo
    private DrawEventType currentPhase;

    // Segundos hasta el sorteo (solo si currentPhase = WAITING_ROOM_UPDATE)
    private Long secondsUntilDraw;

    // Espectadores conectados en este momento
    private int viewerCount;

    // Ganadores ya revelados hasta el momento
    // Vacío si el sorteo no ha empezado, parcial si está en curso
    private List<WinnerRevealPayloadDTO> revealedWinners;

    // Total de ganadores que habrá (para que el frontend sepa "2 de 3")
    private int totalWinners;

    // Solo presente si currentPhase = DRAW_COMPLETED
    private String drawProofUrl;

    private ZonedDateTime asOf; // Timestamp de cuándo se consultó este estado
}