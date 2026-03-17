package com.verygana2.dtos.raffle.responses;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.raffles.RaffleType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RaffleSummaryResultResponseDTO {
    
    private Long raffleId;
    private String raffleTitle;
    private RaffleType raffleType;
    private ZonedDateTime drawnAt;

}
