package com.verygana2.dtos.raffle.requests;

import com.verygana2.models.enums.raffles.RaffleTicketSource;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class IssueTicketRequestDTO {

    @Positive(message = "Consumer id must be positive")
    private Long consumerId;

    @Positive(message = "Raffle id must be positive")
    private Long raffleId;

    @Positive(message = "Quantity must be positive")
    private Integer quantity;

    @NotNull(message = "Source is required")
    private RaffleTicketSource source;
    private Long sourceId;
}
