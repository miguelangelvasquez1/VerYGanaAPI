package com.verygana2.dtos.raffle.responses;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import com.verygana2.models.enums.raffles.PrizeType;

import lombok.Builder;
import lombok.Data;

//para admin o el propio ganador
@Data
@Builder
public class WinnerProofResponseDTO {
    private String userName;
    private String ticketNumber;
    private Integer position;
    private String prizeTitle;
    private PrizeType prizeType;
    private BigDecimal prizeValue;
    private boolean prizeClaimed;
    private ZonedDateTime claimDeadline;
    private ZonedDateTime prizeClaimedAt;
    private String prizeTrackingNumber;
}
