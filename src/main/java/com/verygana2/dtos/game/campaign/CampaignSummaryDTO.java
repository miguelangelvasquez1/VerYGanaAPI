package com.verygana2.dtos.game.campaign;

import com.verygana2.models.enums.CampaignStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CampaignSummaryDTO {

    private Long id;
    private String gameTitle;
    private Long budgetCents;
    private Long spentCents;
    private CampaignStatus status;
    private Long completedSessions;
    private Long uniquePlayersCount;
}
