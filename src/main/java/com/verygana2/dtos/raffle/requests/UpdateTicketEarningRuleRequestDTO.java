package com.verygana2.dtos.raffle.requests;

import java.math.BigDecimal;
import com.verygana2.models.enums.raffles.TicketEarningRuleType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateTicketEarningRuleRequestDTO {
    
    @NotBlank(message = "Rule name is required")
    @Size(max = 100, message = "Rule name cannot exceed 100 characters")
    private String ruleName;
    
    @Size(max = 255, message = "Description cannot exceed 255 characters")
    @NotBlank(message = "description is required")
    private String description;
    
    @NotNull(message = "Rule type is required")
    private TicketEarningRuleType ruleType;
    
    @NotNull(message = "Priority is required")
    @Min(value = 1, message = "Priority must be at least 1")
    @Max(value = 5, message = "Priority cannot exceed 5")
    private Integer priority;
    
    // ========== RECOMPENSA ==========
    
    @NotNull(message = "Tickets to award is required")
    @Min(value = 1, message = "Tickets to award must be at least 1")
    private Integer ticketsToAward;
    
    // ========== CONDICIONES PARA PURCHASE ==========
    
    @PositiveOrZero(message = "Min purchase amount must be positive")
    private BigDecimal minPurchaseAmount;

    // ========== CONDICIONES ADS_WATCHED ==========
    @PositiveOrZero(message = "Min ads watched must be positive")
    private Integer minAdsWatched;
    
    // ========== CONDICIONES PARA GAME_ACHIEVEMENT ==========
    
    @Size(max = 100, message = "Achievement type cannot exceed 100 characters")
    private String achievementType;
    
    // ========== CONDICIONES PARA REFERRAL ==========
    
    @PositiveOrZero(message = "referral added quantity must be positive")
    private Integer referralAddedQuantity;

}
