package com.verygana2.mappers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.verygana2.dtos.MunicipalityResponseDTO;
import com.verygana2.dtos.game.campaign.CampaignDTO;
import com.verygana2.dtos.game.campaign.CreateCampaignRequestDTO;
import com.verygana2.models.Municipality;
import com.verygana2.models.enums.CampaignStatus;
import com.verygana2.models.games.Campaign;
import com.verygana2.models.games.Game;
import com.verygana2.models.userDetails.AdvertiserDetails;

@Mapper(
    componentModel = "spring",
    imports = { CampaignStatus.class, ArrayList.class, BigDecimal.class }
)
public interface CampaignMapper {

    // De entidad a DTO
    @Mapping(target = "gameId", source = "game.id")
    @Mapping(target = "gameTitle", source = "game.title")
    @Mapping(target = "categories", source = "categories")
    @Mapping(target = "targetGender", source = "targetGender")
    @Mapping(target = "minAge", source = "minAge")
    @Mapping(target = "maxAge", source = "maxAge")
    @Mapping(target = "targetMunicipalities", source = "targetMunicipalities")
    CampaignDTO toDto(Campaign entity);

    // ---- Sub-mappers ----

    @Mapping(target = "departmentName", source = "department.name")
    MunicipalityResponseDTO municipalityToDto(Municipality municipality);

    List<MunicipalityResponseDTO> municipalitiesToDto(List<Municipality> municipalities);

    // De DTO a entidad
    @Mapping(target = "id", ignore = true)
    // Relaciones principales
    @Mapping(target = "game", source = "game")
    @Mapping(target = "advertiser", source = "advertiser")
    // Assets y sesiones (se asocian después)
    @Mapping(target = "assets", expression = "java(new ArrayList<>())")
    @Mapping(target = "gameSessions", ignore = true)
    // Métricas persistidas (backend-controlled)
    @Mapping(target = "sessionsPlayed", constant = "0L")
    @Mapping(target = "completedSessions", constant = "0L")
    @Mapping(target = "totalPlayTimeSeconds", constant = "0L")
    @Mapping(target = "spent", expression = "java(BigDecimal.ZERO)")
    // Fechas (modelo activate/pause)
    @Mapping(target = "startDate", ignore = true)
    @Mapping(target = "endDate", ignore = true)
    // Estado inicial
    @Mapping(target = "status", expression = "java(CampaignStatus.DRAFT)")
    // Datos editables del request
    @Mapping(target = "budget", source = "request.budget")
    @Mapping(target = "targetUrl", source = "request.targetUrl")
    @Mapping(target = "minAge", source = "request.minAge")
    @Mapping(target = "maxAge", source = "request.maxAge")
    @Mapping(target = "targetGender", source = "request.targetGender")
    // Targeting relacional (se setea luego en el service)
    @Mapping(target = "categories", ignore = true)
    @Mapping(target = "targetMunicipalities", ignore = true)
    // Auditoría
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Campaign toEntity(CreateCampaignRequestDTO request, Game game, AdvertiserDetails advertiser);
}