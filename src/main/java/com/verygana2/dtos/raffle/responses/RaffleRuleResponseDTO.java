package com.verygana2.dtos.raffle.responses;

import java.time.ZonedDateTime;

import lombok.Data;

@Data
public class RaffleRuleResponseDTO {

    private Long id;
    private Long raffleId;
    private boolean isActive;
    private TicketEarningRuleResponseDTO ticketEarningRuleResponseDTO;
    private Long maxTicketsBySource;
    private Long currentTicketsBySource;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;

}