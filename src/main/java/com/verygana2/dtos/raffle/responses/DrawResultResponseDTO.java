package com.verygana2.dtos.raffle.responses;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DrawResultResponseDTO {
    private Long raffleId;
    private Integer numberOfWinners;
    private List<WinnerSummaryResponseDTO> winners;
    private String message;
}
