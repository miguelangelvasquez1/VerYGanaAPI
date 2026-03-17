package com.verygana2.dtos.raffle.responses;

import java.math.BigDecimal;
import java.util.Map;

import com.verygana2.models.enums.raffles.RaffleTicketSource;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RaffleStatsResponseDTO {
    private Long id;
    private Long maxTicketsFromPurchases;
    private Long maxTicketsFromAds;
    private Long maxTicketsFromGames;
    private Long maxTicketsFromReferrals;
    private BigDecimal totalPrizesValue;
    private Map<RaffleTicketSource, Long> ticketsBySource;
}
