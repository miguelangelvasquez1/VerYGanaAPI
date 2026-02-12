package com.verygana2.dtos.raffle.responses;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.raffles.RaffleTicketSource;
import com.verygana2.models.enums.raffles.RaffleTicketStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RaffleTicketResponseDTO {
    private Long id;
    private RaffleTicketStatus status;
    private String ticketNumber;
    private RaffleTicketSource source;
    private Long sourceId;
    private Long raffleId;
    private ZonedDateTime issuedAt;
    private ZonedDateTime usedAt;
}
