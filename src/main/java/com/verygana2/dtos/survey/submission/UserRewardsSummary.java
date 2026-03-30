package com.verygana2.dtos.survey.submission;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor 
@AllArgsConstructor 
@Builder
@Data
public class UserRewardsSummary {
        private long completedSurveys;
        private BigDecimal totalRewardsEarned;
        private List<RewardInfo> recentRewards;
    }