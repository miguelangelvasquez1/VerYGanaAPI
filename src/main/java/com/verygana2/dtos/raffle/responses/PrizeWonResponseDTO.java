package com.verygana2.dtos.raffle.responses;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import com.verygana2.models.enums.raffles.PrizeType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PrizeWonResponseDTO {
    private Long prizeId;
    private Long winnerId;
    private String title;
    private String description;
    private String brand;
    private BigDecimal value;
    private String imageUrl;
    private PrizeType prizeType;
    private Integer position;
    private Integer quantity;
    private String ticketWinnerNumber;
    private ZonedDateTime drawnAt;
    private boolean isClaimed;
    private ZonedDateTime claimedAt;
}
