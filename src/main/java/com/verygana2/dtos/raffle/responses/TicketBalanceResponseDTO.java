package com.verygana2.dtos.raffle.responses;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.raffles.RaffleStatus;
import com.verygana2.models.enums.raffles.RaffleType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TicketBalanceResponseDTO {
    private Long raffleId;
    private String raffleTitle;
    private RaffleType raffleType;
    private Long ticketsCount;
    private ZonedDateTime drawDate;
    private RaffleStatus raffleStatus;
}
