package com.VerYGana.dtos.ad.responses;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.VerYGana.models.enums.AdStatus;
import com.VerYGana.models.enums.Preference;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdResponseDTO {
    
    private Long id;
    private String title;
    private String description;
    private BigDecimal rewardPerLike;
    private Integer maxLikes;
    private Integer currentLikes;
    private Boolean isActive;
    private AdStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private BigDecimal totalBudget;
    private BigDecimal spentBudget;
    private BigDecimal remainingBudget;
    private Integer remainingLikes;
    private Double completionPercentage;
    private String contentUrl;
    private String targetUrl;
    private Preference category;
    private String rejectionReason;
    
    // Información del anunciante
    private Long advertiserId;
    private String advertiserName;
    private String advertiserEmail;
    
    // Información adicional para usuarios
    private Boolean hasUserLiked; // Se setea si hay contexto de usuario
}
