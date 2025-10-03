package com.verygana2.dtos.ad.responses;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.verygana2.models.enums.AdStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdStatsDTO {
    
    // Estadísticas de un anuncio específico
    private Long adId;
    private Integer totalLikes;
    private Integer maxLikes;
    private Integer remainingLikes;
    private Double completionPercentage;
    private BigDecimal totalBudget;
    private BigDecimal spentBudget;
    private BigDecimal remainingBudget;
    private BigDecimal rewardPerLike;
    private AdStatus status;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    
    // Estadísticas generales de un anunciante
    private Integer totalAds;
    private Integer activeAds;
    private Integer completedAds;
    private Integer pendingAds;
    private BigDecimal totalSpent;
    private Long totalLikesReceived;
    private BigDecimal averageRewardPerLike;
    
    // Estadísticas de rendimiento
    private Double clickThroughRate; // CTR
    private Double conversionRate;
    private BigDecimal costPerLike;
    private Integer totalViews;
    private Integer totalClicks;
}
