package com.VerYGana.mapper;

import org.springframework.stereotype.Component;

import com.VerYGana.dtos.ad.Requests.AdCreateDTO;
import com.VerYGana.dtos.ad.Requests.AdUpdateDTO;
import com.VerYGana.dtos.ad.Responses.AdResponseDTO;
import com.VerYGana.models.ads.Ad;

@Component
public class AdMapper {

    public Ad toEntity(AdCreateDTO dto) {
        if (dto == null) {
            return null;
        }
        
        return Ad.builder()
            .title(dto.getTitle())
            .description(dto.getDescription())
            .rewardPerLike(dto.getRewardPerLike())
            .maxLikes(dto.getMaxLikes())
            .totalBudget(dto.getTotalBudget())
            .startDate(dto.getStartDate())
            .endDate(dto.getEndDate())
            .contentUrl(dto.getContentUrl())
            .targetUrl(dto.getTargetUrl())
            .category(dto.getCategory())
            .build();
    }

    public AdResponseDTO toDto(Ad entity) {
        if (entity == null) {
            return null;
        }
        
        return AdResponseDTO.builder()
            .id(entity.getId())
            .title(entity.getTitle())
            .description(entity.getDescription())
            .rewardPerLike(entity.getRewardPerLike())
            .maxLikes(entity.getMaxLikes())
            .currentLikes(entity.getCurrentLikes())
            .status(entity.getStatus())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .startDate(entity.getStartDate())
            .endDate(entity.getEndDate())
            .totalBudget(entity.getTotalBudget())
            .spentBudget(entity.getSpentBudget())
            .remainingBudget(entity.getRemainingBudget())
            .remainingLikes(entity.getRemainingLikes())
            .completionPercentage(entity.getCompletionPercentage())
            .contentUrl(entity.getContentUrl())
            .targetUrl(entity.getTargetUrl())
            .category(entity.getCategory())
            .rejectionReason(entity.getRejectionReason())
            .advertiserId(entity.getAdvertiser() != null ? entity.getAdvertiser().getId() : null)
            .advertiserName(entity.getAdvertiser() != null ? entity.getAdvertiser().getCompanyName() : null)
            .advertiserEmail(entity.getAdvertiser() != null ? entity.getAdvertiser().getUser().getEmail() : null)
            .build();
    }

    public void updateEntityFromDto(AdUpdateDTO dto, Ad entity) {
        if (dto == null || entity == null) {
            return;
        }
        
        if (dto.getTitle() != null) {
            entity.setTitle(dto.getTitle());
        }
        if (dto.getDescription() != null) {
            entity.setDescription(dto.getDescription());
        }
        if (dto.getRewardPerLike() != null) {
            entity.setRewardPerLike(dto.getRewardPerLike());
        }
        if (dto.getMaxLikes() != null) {
            entity.setMaxLikes(dto.getMaxLikes());
        }
        if (dto.getTotalBudget() != null) {
            entity.setTotalBudget(dto.getTotalBudget());
        }
        if (dto.getStartDate() != null) {
            entity.setStartDate(dto.getStartDate());
        }
        if (dto.getEndDate() != null) {
            entity.setEndDate(dto.getEndDate());
        }
        if (dto.getContentUrl() != null) {
            entity.setContentUrl(dto.getContentUrl());
        }
        if (dto.getTargetUrl() != null) {
            entity.setTargetUrl(dto.getTargetUrl());
        }
        if (dto.getCategory() != null) {
            entity.setCategory(dto.getCategory());
        }
    }
}
