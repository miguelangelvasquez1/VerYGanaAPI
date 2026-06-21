package com.verygana2.dtos.survey.submission;

import java.time.ZonedDateTime;

import com.verygana2.models.surveys.SurveyReward;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class RewardInfo {
    private Long rewardId;
    private Long amountCents;
    private SurveyReward.RewardStatus status;
    private ZonedDateTime grantedAt;
}
