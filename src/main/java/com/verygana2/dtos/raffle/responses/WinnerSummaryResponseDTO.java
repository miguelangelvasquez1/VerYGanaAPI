package com.verygana2.dtos.raffle.responses;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WinnerSummaryResponseDTO {
    private Long winnerId;
    private Long consumerId;
    private String consumerName;
    private String ticketNumber;
    private String prizeTitle;
    private Integer position;
}
