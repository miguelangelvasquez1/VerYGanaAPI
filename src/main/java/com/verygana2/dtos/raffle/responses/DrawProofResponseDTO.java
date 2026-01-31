package com.verygana2.dtos.raffle.responses;

import java.time.ZonedDateTime;
import java.util.List;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DrawProofResponseDTO {
    private Long raffleId;
    private String raffleTitle;
    private String drawMethod;
    private ZonedDateTime drawDate;
    private ZonedDateTime executedAt;
    private Long totalParticipants;
    private Long totalTickets;
    private Integer numberOfWinners;
    private List<WinnerProofResponseDTO> winners;
}
