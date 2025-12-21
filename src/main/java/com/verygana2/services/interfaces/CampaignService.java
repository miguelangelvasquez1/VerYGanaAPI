package com.verygana2.services.interfaces;

import java.util.List;
import java.util.Map;

import com.verygana2.dtos.game.campaign.AssetUploadPermissionDTO;
import com.verygana2.dtos.game.campaign.CreateAssetRequestDTO;
import com.verygana2.dtos.game.campaign.GameAssetDefinitionDTO;
import com.verygana2.models.games.Campaign;

public interface CampaignService {
    
    Map<Long, AssetUploadPermissionDTO> prepareAssetUploads(
            Long gameId,
            List<CreateAssetRequestDTO> assetRequests);

    Campaign createCampaignWithAssets(
            Long gameId,
            Long advertiserId,
            Map<Long, String> uploadedAssets);

    List<GameAssetDefinitionDTO> getAssetsByGame(Long gameId);
}
