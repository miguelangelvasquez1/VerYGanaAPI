package com.verygana2.dtos.raffle.websocket;

import java.util.List;

import com.verygana2.dtos.raffle.responses.PrizeResponseDTO;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WaitingRoomPayloadDTO {

    private Integer viewerCount;
    private long secondsUntilDraw; 
    private Integer totalTickets; 
    private List<PrizeResponseDTO> prizes;
    private Integer totalParticipants;
}
