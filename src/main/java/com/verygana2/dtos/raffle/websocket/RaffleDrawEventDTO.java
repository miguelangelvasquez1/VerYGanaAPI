package com.verygana2.dtos.raffle.websocket;

import java.time.ZonedDateTime;
import com.verygana2.models.enums.raffles.DrawEventType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RaffleDrawEventDTO {
    private DrawEventType type;
    private Long raffleId;
    private ZonedDateTime timestamp;
    private Object payload; // WaitingRoomPayload | WinnerRevealPayload | DrawCompletedPayload
}
