package com.verygana2.mappers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.dtos.MunicipalityResponseDTO;
import com.verygana2.dtos.game.campaign.CampaignDTO;
import com.verygana2.dtos.game.campaign.CampaignSummaryDTO;
import com.verygana2.models.Municipality;
import com.verygana2.models.branding.Campaign;
import com.verygana2.models.enums.CampaignStatus;

@Mapper(
    componentModel = "spring",
    imports = { CampaignStatus.class, ArrayList.class, BigDecimal.class }
)
public interface CampaignMapper {

    // De entidad a DTO
    @Mapping(target = "gameId", source = "game.id")
    @Mapping(target = "gameTitle", source = "game.title")
    @Mapping(target = "brandingRequestId", source = "brandingRequest.id")
    @Mapping(target = "brandName", source = "brandingRequest.brandName")
    @Mapping(target = "campaignGoal", source = "brandingRequest.campaignGoal")
    @Mapping(target = "costPerSessionCents", source = "averageRewardPerSessionCents")
    @Mapping(target = "categories", source = "targetAudience.categories")
    @Mapping(target = "targetGender", source = "targetAudience.targetGender")
    @Mapping(target = "minAge", source = "targetAudience.minAge")
    @Mapping(target = "maxAge", source = "targetAudience.maxAge")
    @Mapping(target = "targetMunicipalities", source = "targetAudience.targetMunicipalities")
    CampaignDTO toDto(Campaign entity);

    @Mapping(target = "gameTitle", source = "game.title")
    CampaignSummaryDTO toSummaryDto(Campaign entity);

    // ---- Sub-mappers ----

    @Mapping(target = "departmentName", source = "department.name")
    MunicipalityResponseDTO municipalityToDto(Municipality municipality);

    List<MunicipalityResponseDTO> municipalitiesToDto(List<Municipality> municipalities);

    // Helper to convert JsonNode to Map<String,Object> for MapStruct
    default Map<String, Object> map(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        ObjectMapper mapper = new ObjectMapper();
        return mapper.convertValue(value, new TypeReference<Map<String, Object>>() {});
    }
}