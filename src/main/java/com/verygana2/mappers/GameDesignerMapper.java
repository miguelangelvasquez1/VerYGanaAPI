package com.verygana2.mappers;

import java.util.List;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mapping;

import com.verygana2.dtos.branding.DesignerBrandingDetailDTO;
import com.verygana2.dtos.user.gamedesigner.GameDesignerProfileResponseDTO;
import com.verygana2.models.TargetAudience;
import com.verygana2.models.branding.BrandingRequest;
import com.verygana2.models.userDetails.GameDesignerDetails;

@Mapper(componentModel = "spring", uses = {BrandingMapper.class})
public interface GameDesignerMapper {

    // ===== GameDesignerDetails → GameDesignerProfileResponseDTO =====

    @Mapping(target = "email",        source = "user.email")
    @Mapping(target = "phoneNumber",  source = "user.phoneNumber")
    @Mapping(target = "gamesCreated", expression = "java(0)")
    GameDesignerProfileResponseDTO toProfileDTO(GameDesignerDetails details);

    // ===== BrandingRequest → DesignerBrandingDetailDTO =====
    // corporateResources y gameSchema se inyectan en el service (requieren R2 y lógica de config)

    @Mapping(target = "commercialName",       source = "commercial.companyName")
    @Mapping(target = "gameId",               source = "game.id")
    @Mapping(target = "gameName",             source = "game.title")
    @Mapping(target = "gameFrontPageUrl",     source = "game.frontPageUrl")
    @Mapping(target = "targetMunicipalities", source = "targetAudience.targetMunicipalities")
    @Mapping(target = "categories", source = "targetAudience.categories")
    @Mapping(target = "minAge", source = "targetAudience.minAge")
    @Mapping(target = "maxAge", source = "targetAudience.maxAge")
    @Mapping(target = "targetGender", source = "targetAudience.targetGender")
    @Mapping(target = "corporateResources",   ignore = true)
    @Mapping(target = "gameSchema",           ignore = true)
    DesignerBrandingDetailDTO toDesignerDetailDTO(BrandingRequest request);

    /**
     * Mismo relleno que en {@code BrandingMapper}, y por el mismo motivo: una solicitud
     * puede aprobarse sin targeting, y el panel del diseñador hace
     * {@code categories.length} apenas abre el brief.
     *
     * El {@code @MappingTarget} tiene que ser el <b>builder</b>: el DTO usa
     * {@code @Builder} de Lombok y MapStruct saltea en silencio los
     * {@code @AfterMapping} que apuntan al DTO ya construido.
     */
    @AfterMapping
    default void fillEmptyTargeting(
            @MappingTarget DesignerBrandingDetailDTO.DesignerBrandingDetailDTOBuilder dto,
            BrandingRequest request) {

        TargetAudience audience = request.getTargetAudience();
        if (audience == null || audience.getCategories() == null) {
            dto.categories(List.of());
        }
        if (audience == null || audience.getTargetMunicipalities() == null) {
            dto.targetMunicipalities(List.of());
        }
    }
}
