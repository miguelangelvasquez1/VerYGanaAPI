package com.verygana2.services.interfaces;

import java.util.List;

import com.verygana2.dtos.game.campaign.CampaignDTO;
import com.verygana2.dtos.game.campaign.CampaignSummaryDTO;
import com.verygana2.dtos.game.campaign.UpdateCampaignRequestDTO;
import com.verygana2.models.enums.CampaignStatus;

public interface CampaignService {

    List<CampaignSummaryDTO> getCommercialCampaigns(Long commercialId);

    CampaignDTO getCampaignDetail(Long campaignId, Long userId);

    void updateCampaignStatus(Long campaignId, Long userId, CampaignStatus newStatus);

    void updateCampaign(Long campaignId, Long userId, UpdateCampaignRequestDTO request);
}
