package com.verygana2.dtos.raffle.websocket;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WaitingRoomPayloadDTO {

    private int viewerCount; // Usuarios conectados a esta sala
    private long secondsUntilDraw; // Para el countdown del frontend
    private long totalTickets; // totalTicketsIssued de la rifa
}
