package com.verygana2.dtos.raffle.requests;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateRaffleRuleRequestDTO {

    @NotNull(message = "Ticket earning rule is required")
    private Long ticketEarningRuleId;
    
    @PositiveOrZero(message = "Max tickets for this source cannot be negative")
    private Long maxTicketsBySource;
}
