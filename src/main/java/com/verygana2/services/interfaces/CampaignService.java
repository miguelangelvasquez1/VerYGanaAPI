package com.verygana2.services.interfaces;

import java.util.List;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.FileUploadRequestDTO;
import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.game.GameDTO;
import com.verygana2.dtos.game.campaign.AssetConfirmRequest;
import com.verygana2.dtos.game.campaign.AssetUploadPermissionDTO;
import com.verygana2.dtos.game.campaign.CampaignDTO;
import com.verygana2.dtos.game.campaign.CreateCampaignRequestDTO;
import com.verygana2.dtos.game.campaign.GameAssetDefinitionDTO;
import com.verygana2.dtos.game.campaign.UpdateCampaignRequestDTO;
import com.verygana2.models.enums.CampaignStatus;

public interface CampaignService {

    AssetUploadPermissionDTO generateUploadUrl(FileUploadRequestDTO request, Long userId);

    void confirmUpload(AssetConfirmRequest request);

    void createCampaign(CreateCampaignRequestDTO request, Long userId);

    List<CampaignDTO> getAdvertiserCampaigns(Long advertiserId);

    void updateCampaignStatus(Long campaignId, Long userId, CampaignStatus newStatus);

    void updateCampaign(Long campaignId, Long userId, UpdateCampaignRequestDTO request);
    
    PagedResponse<GameDTO> getAvailableGames(Long advertiserId, Pageable pageable);

    List<GameAssetDefinitionDTO> getAssetsByGame(Long gameId);
}
