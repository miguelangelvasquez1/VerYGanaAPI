package com.verygana2.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.verygana2.dtos.game.AssetDTO;
import com.verygana2.dtos.game.InitGameResponseDTO;
import com.verygana2.models.games.Asset;
import com.verygana2.models.games.Campaign;
import com.verygana2.models.games.Game;
import com.verygana2.models.games.GameSession;

@Mapper(componentModel = "spring")
public interface GameMapper {

    @Mapping(target = "sessionId", source = "session.id")
    @Mapping(target = "gameId", source = "game.id")
    @Mapping(target = "gameCode", source = "game.code")
    @Mapping(target = "gameName", source = "game.name")
    @Mapping(target = "minDurationSeconds", source = "game.minDurationSeconds")
    @Mapping(target = "maxDurationSeconds", source = "game.maxDurationSeconds")
    @Mapping(target = "jsonConfig", ignore = true) // Assuming jsonConfig is set elsewhere
    @Mapping(target = "campaignId", source = "campaign.id")
    @Mapping(target = "advertiserName", source = "campaign.advertiser.companyName")
    @Mapping(target = "assets", source = "campaign.assets")
    InitGameResponseDTO toInitResponse(
        GameSession session,
        Game game,
        Campaign campaign
    );

    AssetDTO toAssetDto(Asset asset);
}
