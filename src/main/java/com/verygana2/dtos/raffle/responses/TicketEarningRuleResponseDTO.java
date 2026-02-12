package com.verygana2.dtos.raffle.responses;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import com.verygana2.models.enums.raffles.TicketEarningRuleType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TicketEarningRuleResponseDTO {
    private Long id; 
    private String ruleName;
    private String description; 
    private TicketEarningRuleType ruleType; 
    private boolean isActive; 
    private Integer priority;
    private Integer ticketsToAward; 

    // Para PURCHASE:
    private BigDecimal minPurchaseAmount; 

    // Para ADS:
    private Integer minAdsWatched; 

    // Para GAME_ACHIEVEMENT:
    private String achievementType; 

    // Para REFERRAL:
    private Integer referralAddedQuantity; 

    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt; 
}
