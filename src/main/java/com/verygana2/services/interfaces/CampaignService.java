package com.verygana2.services.interfaces;

import java.util.List;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.game.GameDTO;
import com.verygana2.dtos.game.campaign.AssetUploadPermissionDTO;
import com.verygana2.dtos.game.campaign.CreateAssetRequestDTO;
import com.verygana2.dtos.game.campaign.GameAssetDefinitionDTO;
import com.verygana2.models.games.Campaign;

public interface CampaignService {
    
    List<AssetUploadPermissionDTO> prepareAssetUploads(
            Long gameId,
            Long advertiserId,
            List<CreateAssetRequestDTO> assetRequests);

    Campaign createCampaignWithAssets(
            Long gameId,
            Long advertiserId,
            List<Long> assetIds);

    PagedResponse<GameDTO> getAvailableGames(Long advertiserId, Pageable pageable);

    List<GameAssetDefinitionDTO> getAssetsByGame(Long gameId);
}
