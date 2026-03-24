package com.verygana2.services.surveys;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.survey.submission.RewardInfo;
import com.verygana2.dtos.survey.submission.UserRewardsSummary;
import com.verygana2.models.surveys.Survey;
import com.verygana2.models.surveys.SurveyResponse;
import com.verygana2.models.surveys.SurveyReward;
import com.verygana2.repositories.surveys.SurveyResponseRepository;
import com.verygana2.repositories.surveys.SurveyRewardRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RewardService {
 
    private final SurveyRewardRepository rewardRepository;
    private final SurveyResponseRepository responseRepository;
    // Inject your wallet/points service here:
    // private final UserWalletService walletService;
 
    /**
     * Grants the configured reward to the user who completed the survey.
     * This is called after the response is validated and persisted.
     */
    @Transactional
    public SurveyReward grantReward(SurveyResponse surveyResponse) {
        Survey survey = surveyResponse.getSurvey();
        Long userId = surveyResponse.getUserId();
 
        SurveyReward reward = SurveyReward.builder()
            .userId(userId)
            .survey(survey)
            .amount(survey.getRewardAmount())
            .status(SurveyReward.RewardStatus.PENDING)
            .build();
 
        reward = rewardRepository.save(reward);
 
        // Dispatch reward depending on type
        try {
            creditPoints(userId, survey.getRewardAmount());
            reward.setStatus(SurveyReward.RewardStatus.PROCESSED);
            reward.setProcessedAt(LocalDateTime.now());
            log.info("Reward granted to user {} for survey {}: {}",userId, survey.getId(), reward.getAmount());
        } catch (Exception e) {
            reward.setStatus(SurveyReward.RewardStatus.FAILED);
            log.error("Failed to process reward for user {} survey {}: {}",
                userId, survey.getId(), e.getMessage(), e);
        }
 
        // Link reward to response
        surveyResponse.setReward(reward);
        surveyResponse.setStatus(SurveyResponse.ResponseStatus.REWARDED);
 
        return rewardRepository.save(reward);
    }
 
    public UserRewardsSummary getUserRewardsSummary(Long userId) {
        long completedSurveys = responseRepository.countCompletedByUser(userId);
        var totalEarned = rewardRepository.getTotalRewardsByUser(userId);
 
        List<RewardInfo> recent = rewardRepository
            .findByUserId(userId, Pageable.ofSize(10))
            .stream()
            .map(r -> RewardInfo.builder()
                .rewardId(r.getId())
                .amount(r.getAmount())
                .status(r.getStatus())
                .grantedAt(r.getGrantedAt())
                .build())
            .toList();
 
        return UserRewardsSummary.builder()
            .completedSurveys(completedSurveys)
            .totalRewardsEarned(totalEarned)
            .recentRewards(recent)
            .build();
    }
 
    // ─── Private dispatch methods ─────────────────────────────────────────────
 
    private void creditPoints(Long userId, java.math.BigDecimal amount) {
        // TODO: integrate with your UserWalletService / PointsService
        log.debug("Crediting {} points to user {}", amount, userId);
        // walletService.addPoints(userId, amount);
    }
}