package com.verygana2.dtos.raffle.websocket;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DrawingStartedPayloadDTO {
    int totalWinners;
    long totalTickets;
    int maxTickets;
}
