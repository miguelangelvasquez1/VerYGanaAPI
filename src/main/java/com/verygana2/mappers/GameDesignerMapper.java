package com.verygana2.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.verygana2.dtos.branding.DesignerBrandingDetailDTO;
import com.verygana2.dtos.user.gamedesigner.GameDesignerProfileResponseDTO;
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
    @Mapping(target = "targetMunicipalities", source = "targetMunicipalities")
    @Mapping(target = "corporateResources",   ignore = true)
    @Mapping(target = "gameSchema",           ignore = true)
    DesignerBrandingDetailDTO toDesignerDetailDTO(BrandingRequest request);
}
