package com.verygana2.mappers;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.verygana2.dtos.game.campaign.GameAssetDefinitionDTO;
import com.verygana2.dtos.game.AssetDTO;
import com.verygana2.models.branding.Asset;
import com.verygana2.models.games.GameAssetDefinition;

@Mapper(componentModel = "spring")
public interface GameMapper {



    @Mapping(target = "assetType", ignore = true)
    AssetDTO toAssetDto(Asset asset);

    // Para obtener las definiciones
    GameAssetDefinitionDTO toDto(GameAssetDefinition entity);

    List<GameAssetDefinitionDTO> toDtoList(List<GameAssetDefinition> entities);
}
