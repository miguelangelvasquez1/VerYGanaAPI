package com.verygana2.dtos.game.campaign;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateCampaignRequestDTO {
    
    @NotNull(message = "Game ID is required")
    @Positive(message = "Game ID must be positive")
    private Long gameId;
    
    @NotNull(message = "Budget is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Budget must be greater than 0")
    private BigDecimal budget;
    
    @NotNull(message = "Coin value is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Coin value must be greater than 0")
    private BigDecimal coinValue;
    
    @NotNull(message = "Completion coins is required")
    @Min(value = 1, message = "Completion coins must be at least 1")
    private Integer completionCoins;
    
    @NotNull(message = "Budget coins is required")
    @Min(value = 0, message = "Budget coins cannot be negative")
    private Integer budgetCoins;
    
    @NotNull(message = "Max coins per session is required")
    @Min(value = 1, message = "Max coins per session must be at least 1")
    private Integer maxCoinsPerSession;
    
    @NotNull(message = "Max sessions per user per day is required")
    @Min(value = 1, message = "Max sessions per user per day must be at least 1")
    private Integer maxSessionsPerUserPerDay;
    
    @Pattern(regexp = "^https?://.*", message = "Target URL must be a valid HTTP/HTTPS URL")
    private String targetUrl;
    
    @NotEmpty(message = "At least one category is required")
    private List<@Positive Long> categoryIds;
    
    @NotNull(message = "Target audience is required")
    @Valid
    private TargetAudienceDTO targetAudience;
    
    @NotNull(message = "Game configuration is required")
    private Map<String, Object> configData;
    
    private ZonedDateTime startDate;
    
    private ZonedDateTime endDate;
}