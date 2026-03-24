package com.verygana2.dtos.raffle.websocket;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DrawCompletedPayloadDTO {
    private String raffleTitle;
    private List<WinnerRevealPayloadDTO> allWinners; // Lista completa para la pantalla final
    private String drawProofUrl;  // GET /api/raffles/{id}/draw-proof
    private int totalParticipants;
}
