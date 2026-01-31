package com.verygana2.dtos.raffle.requests;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import com.verygana2.models.enums.raffles.RaffleType;
import com.verygana2.models.enums.raffles.RuleType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class UpdateRuleRequestDTO {
    
    @NotBlank(message = "Rule name is required")
    @Size(max = 100, message = "Rule name cannot exceed 100 characters")
    private String ruleName;
    
    @Size(max = 255, message = "Description cannot exceed 255 characters")
    private String description;
    
    @NotNull(message = "Rule type is required")
    private RuleType ruleType;
    
    @NotNull(message = "Priority is required")
    @Min(value = 0, message = "Priority must be at least 0")
    @Max(value = 100, message = "Priority cannot exceed 100")
    private Integer priority;
    
    private Boolean isActive; // Permitir activar/desactivar en update
    
    // ========== RECOMPENSA ==========
    
    @NotNull(message = "Tickets to award is required")
    @Min(value = 1, message = "Tickets to award must be at least 1")
    private Integer ticketsToAward;
    
    @PositiveOrZero(message = "Tickets multiplier must be positive or zero")
    private BigDecimal ticketsMultiplier;
    
    private RaffleType appliesToRaffleType;
    
    // ========== CONDICIONES PARA PURCHASE ==========
    
    @PositiveOrZero(message = "Min purchase amount must be positive")
    private BigDecimal minPurchaseAmount;

    // ========== CONDICIONES ADS_WATCHED ==========
    @PositiveOrZero(message = "Min ads watched must be positive")
    private Integer minAdsWatched;
    
    // ========== CONDICIONES PARA GAME_ACHIEVEMENT ==========
    
    @Size(max = 100, message = "Achievement type cannot exceed 100 characters")
    private String achievementType;
    
    @PositiveOrZero(message = "Min achievement value must be positive")
    private Integer minAchievementValue;
    
    // ========== CONDICIONES PARA REFERRAL ==========
    
    @PositiveOrZero(message = "referral added quantity must be positive")
    private Integer referralAddedQuantity;
    
    // ========== CONDICIONES PERSONALIZADAS ==========
    
    @Size(max = 2000, message = "Custom conditions cannot exceed 2000 characters")
    private String customConditions;
    
    // ========== LÍMITES ==========
    
    @Positive(message = "Max tickets per user per day must be positive")
    private Integer maxTicketsPerUserPerDay;
    
    @Positive(message = "Max tickets per user total must be positive")
    private Integer maxTicketsPerUserTotal;
    
    @Positive(message = "Max uses global must be positive")
    private Long maxUsesGlobal;
    
    private Boolean requiresPet; // Usar Boolean para permitir null
    
    // ========== VALIDEZ TEMPORAL ==========
    
    private ZonedDateTime validFrom;
    private ZonedDateTime validUntil;
}
