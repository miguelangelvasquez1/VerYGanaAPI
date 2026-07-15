package com.verygana2.mappers;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Value;

import com.verygana2.dtos.MunicipalityResponseDTO;
import com.verygana2.dtos.branding.BrandingGameDTO;
import com.verygana2.dtos.branding.BrandingRequestDetailDTO;
import com.verygana2.dtos.branding.BrandingRequestSummaryDTO;
import com.verygana2.dtos.branding.CorporateResourceDTO;
import com.verygana2.models.Municipality;
import com.verygana2.models.branding.BrandingRequest;
import com.verygana2.models.branding.Campaign;
import com.verygana2.models.branding.CorporateResource;
import com.verygana2.models.games.Game;

import jakarta.validation.ValidationException;

@Mapper(componentModel = "spring")
public abstract class BrandingMapper {

    @Value("${cloudflare.r2.games-domain}")
    protected String cdnUrl;

    // ===== Game → BrandingGameDTO =====

    @Mapping(target = "url", ignore = true)
    @Mapping(target = "averageRewardPerSessionCents", ignore = true)
    @Mapping(target = "averageDurationSeconds", ignore = true)
    public abstract BrandingGameDTO toBrandingGameDTO(Game game);

    @AfterMapping
    protected void setGameUrl(@MappingTarget BrandingGameDTO dto, Game game) {
        dto.setUrl(buildGameUrl(game));
        if (game.getConfigDefinitions() != null) {
            game.getConfigDefinitions().stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsLatest()))
                .findFirst()
                .ifPresent(config -> {
                    dto.setAverageRewardPerSessionCents(config.getAverageRewardPerSessionCents());
                    dto.setAverageDurationSeconds(config.getAverageDurationSeconds());
                });
        }
    }

    // ===== BrandingRequest → BrandingRequestSummaryDTO =====

    @Mapping(target = "gameName", source = "game.title")
    @Mapping(target = "commercialName", source = "commercial.companyName")
    @Mapping(target = "assignedDesignerName", expression = "java(request.getAssignedDesigner() != null ? request.getAssignedDesigner().getName() + \" \" + request.getAssignedDesigner().getLastName() : null)")
    @Mapping(target = "corporateResourceCount", ignore = true)
    public abstract BrandingRequestSummaryDTO toSummaryDTO(BrandingRequest request);

    // ===== BrandingRequest → BrandingRequestDetailDTO =====

    @Mapping(target = "gameId", source = "game.id")
    @Mapping(target = "gameName", source = "game.title")
    @Mapping(target = "gameFrontPageUrl", source = "game.frontPageUrl")
    @Mapping(target = "commercialName", source = "commercial.companyName")
    @Mapping(target = "categories", source = "targetAudience.categories")
    @Mapping(target = "targetMunicipalities", source = "targetAudience.targetMunicipalities")
    @Mapping(target = "minAge", source = "targetAudience.minAge")
    @Mapping(target = "maxAge", source = "targetAudience.maxAge")
    @Mapping(target = "targetGender", source = "targetAudience.targetGender")
    @Mapping(target = "campaignId", source = "campaign.id")
    @Mapping(target = "assignedDesignerName", ignore = true)
    @Mapping(target = "assignedDesignerCode", ignore = true)
    @Mapping(target = "reviewedByAdminName", ignore = true)
    @Mapping(target = "corporateResources", ignore = true)
    @Mapping(target = "hasCompleteTargeting", ignore = true)
    public abstract BrandingRequestDetailDTO toDetailDTO(BrandingRequest request);

    @AfterMapping
    protected void setDetailComputedFields(@MappingTarget BrandingRequestDetailDTO dto, BrandingRequest request) {
        if (request.getAssignedDesigner() != null) {
            dto.setAssignedDesignerName(
                request.getAssignedDesigner().getName() + " " + request.getAssignedDesigner().getLastName()
            );
            dto.setAssignedDesignerCode(request.getAssignedDesigner().getDesignerCode());
        }
        if (request.getReviewedByAdmin() != null) {
            dto.setReviewedByAdminName("Admin #" + request.getReviewedByAdmin().getId());
        }
    }

    // ===== BrandingRequest → Campaign (al aprobar el diseño) =====

    @Mapping(target = "configDefinition", source = "gameConfigDefinition")
    @Mapping(target = "configData", source = "gameConfig")
    @Mapping(target = "status", constant = "DRAFT")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "brandingRequest", ignore = true)
    @Mapping(target = "gameSessions", ignore = true)
    @Mapping(target = "sessionsPlayed", ignore = true)
    @Mapping(target = "completedSessions", ignore = true)
    @Mapping(target = "totalPlayTimeSeconds", ignore = true)
    @Mapping(target = "uniquePlayersCount", ignore = true)
    @Mapping(target = "spentCents", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    public abstract Campaign toCampaign(BrandingRequest request);

    // ===== CorporateResource → CorporateResourceDTO =====

    @Mapping(target = "temporalUrl", ignore = true)
    public abstract CorporateResourceDTO toCorporateResourceDTO(CorporateResource resource);

    // ===== Municipality → MunicipalityResponseDTO =====

    @Mapping(target = "departmentCode", source = "department.code")
    @Mapping(target = "departmentName", source = "department.name")
    public abstract MunicipalityResponseDTO toMunicipalityDTO(Municipality municipality);

    // ===== Helper =====

    private String buildGameUrl(Game game) {
        if (game.getDeliveryType() == Game.DeliveryType.PATH) {
            return String.format("https://%s/builds/build-bogota/%s/%s/", cdnUrl, "28-04-2026", game.getUrl());
        } else if (game.getDeliveryType() == Game.DeliveryType.QUERY) {
            return String.format("https://%s/builds/build-cali/?game_title=%s&", cdnUrl, game.getUrl());
        }
        throw new ValidationException("Unsupported delivery type: " + game.getDeliveryType());
    }
}
