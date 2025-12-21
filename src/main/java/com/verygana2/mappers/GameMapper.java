package com.verygana2.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.verygana2.dtos.game.InitGameResponseDTO;
import com.verygana2.dtos.game.AssetDTO;
import com.verygana2.models.games.Asset;
import com.verygana2.models.games.Campaign;
import com.verygana2.models.games.Game;
import com.verygana2.models.games.GameSession;

@Mapper(componentModel = "spring")
public interface GameMapper {

    @Mapping(target = "sessionToken", source = "session.sessionToken")
    @Mapping(target = "userHash", source = "session.userHash")
    @Mapping(target = "gameId", source = "game.id")
    @Mapping(target = "campaignId", source = "campaign.id")
    @Mapping(target = "assets", source = "campaign.assets")
    @Mapping(target = "jsonConfig", ignore = true) // Assuming jsonConfig is set elsewhere
    InitGameResponseDTO toInitResponse(
        GameSession session,
        Game game,
        Campaign campaign
    );

    AssetDTO toAssetDto(Asset asset);
}
