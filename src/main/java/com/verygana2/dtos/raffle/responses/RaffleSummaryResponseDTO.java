package com.verygana2.dtos.raffle.responses;

import java.time.ZonedDateTime;


import com.verygana2.models.enums.raffles.RaffleStatus;
import com.verygana2.models.enums.raffles.RaffleType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaffleSummaryResponseDTO {
    private Long id;
    private String title;
    private String imageUrl;
    private RaffleType raffleType;
    private RaffleStatus raffleStatus;
    private ZonedDateTime startDate;
    private ZonedDateTime endDate;
    private ZonedDateTime drawDate;
    private Long totalTicketsIssued;
    private Long totalParticipants;
    private Long prizeCount;
    private boolean requiresPet;
}

