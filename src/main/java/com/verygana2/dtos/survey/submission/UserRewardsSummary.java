package com.verygana2.dtos.survey.submission;

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
        private Long totalKeysEarned;
        private List<RewardInfo> recentRewards;
    }