package com.verygana2.dtos.ad.responses;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.verygana2.dtos.MunicipalityResponseDTO;
import com.verygana2.models.Category;
import com.verygana2.models.enums.AdStatus;
import com.verygana2.models.enums.MediaType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdResponseDTO { // Response for advertiser
    
    private Long id;
    private String title;
    private String description;
    private BigDecimal rewardPerLike;
    private Integer maxLikes;
    private Integer currentLikes;
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
    private List<Category> categories;
    private Integer minAge;
    private Integer maxAge;
    private String targetGender;
    private String rejectionReason;
    private MediaType mediaType;
    private List<MunicipalityResponseDTO> targetMunicipalities;
}
