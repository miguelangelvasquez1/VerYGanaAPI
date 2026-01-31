package com.verygana2.dtos.raffle.responses;

import java.time.ZonedDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WinnerProofResponseDTO {
    private Integer position;
    private String ticketNumber;
    private Long consumerId;
    private String prizeTitle;
    private ZonedDateTime drawnAt;
}
