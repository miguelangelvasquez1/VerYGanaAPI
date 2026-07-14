package com.verygana2.services.surveys;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import com.verygana2.event.XpAwardRequestedEvent;
import com.verygana2.models.enums.ActivityType;
import org.springframework.context.ApplicationEventPublisher;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.survey.submission.RewardInfo;
import com.verygana2.dtos.survey.submission.UserRewardsSummary;
import com.verygana2.models.finance.KeyTransaction;
import com.verygana2.models.finance.KeyWallet;
import com.verygana2.models.surveys.SurveyReward;
import com.verygana2.models.surveys.SurveySession;
import com.verygana2.repositories.finance.KeyTransactionRepository;
import com.verygana2.repositories.finance.KeyWalletRepository;
import com.verygana2.repositories.surveys.SurveyRewardRepository;
import com.verygana2.repositories.surveys.SurveySessionRepository;
import com.verygana2.services.finance.KeyWalletServiceImpl.RewardSplit;
import com.verygana2.services.interfaces.finance.KeyWalletService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RewardService {

    private final SurveyRewardRepository rewardRepository;
    private final SurveySessionRepository sessionRepository;
    private final KeyWalletRepository keyWalletRepository;
    private final KeyWalletService keyWalletService;
    private final KeyTransactionRepository keyTransactionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${financial.key-value-cents:1000}")
    private long keyValueCents;

    @Transactional
    public SurveyReward grantReward(SurveySession session) {
        long questionCount = session.getSurvey().getQuestions().size();
        long rewardAmount  = questionCount * session.getSurvey().getRewardAmountPerQuestionCents();

        SurveyReward reward = SurveyReward.builder()
                .session(session)
                .amountCents(rewardAmount)
                .status(SurveyReward.RewardStatus.PENDING)
                .build();
        reward = rewardRepository.save(reward);

        try {
            creditPoints(session, rewardAmount);
            reward.setStatus(SurveyReward.RewardStatus.PROCESSED);
            reward.setProcessedAt(ZonedDateTime.now());
            log.info("Reward granted to consumer {} for survey {}: {} ¢",
                    session.getConsumer().getId(), session.getSurvey().getId(), rewardAmount);
            eventPublisher.publishEvent(
                    new XpAwardRequestedEvent(this, session.getConsumer().getId(), ActivityType.SURVEY_COMPLETED));
        } catch (Exception e) {
            reward.setStatus(SurveyReward.RewardStatus.FAILED);
            log.error("Failed to process reward for consumer {} survey {}: {}",
                    session.getConsumer().getId(), session.getSurvey().getId(), e.getMessage(), e);
        }

        return rewardRepository.save(reward);
    }

    public UserRewardsSummary getUserRewardsSummary(Long consumerId) {
        long completedSurveys = sessionRepository.countCompletedByConsumer(consumerId);
        BigDecimal totalEarnedCents = rewardRepository.getTotalRewardsByConsumer(consumerId);
        long totalKeysEarned = totalEarnedCents.longValue() / keyValueCents;

        List<RewardInfo> recent = rewardRepository
                .findBySessionConsumerId(consumerId, Pageable.ofSize(10))
                .stream()
                .map(r -> RewardInfo.builder()
                        .rewardId(r.getId())
                        .amountKeys(r.getAmountCents() / keyValueCents)
                        .status(r.getStatus())
                        .grantedAt(r.getGrantedAt())
                        .build())
                .toList();

        return UserRewardsSummary.builder()
                .completedSurveys(completedSurveys)
                .totalKeysEarned(totalKeysEarned)
                .recentRewards(recent)
                .build();
    }

    private void creditPoints(SurveySession session, long amountCents) {
        Long consumerId = session.getConsumer().getId();
        KeyWallet keyWallet = keyWalletService.getByConsumerId(consumerId);
        RewardSplit split = keyWalletService.calculate(amountCents);

        UUID referenceId = UUID.nameUUIDFromBytes(
                ("survey-session-" + session.getId()).getBytes());
        String reason = "Encuesta completada #" + session.getSurvey().getId();

        ZonedDateTime purchaseExpiry     = keyWalletService.calculatePurchaseExpiry();
        ZonedDateTime connectivityExpiry = keyWalletService.calculateConnectivityExpiry();

        keyTransactionRepository.saveAll(List.of(
                KeyTransaction.forInteractionPurchaseKeys(
                        keyWallet, split.purchaseKeysReward(), reason, referenceId, purchaseExpiry),
                KeyTransaction.forInteractionConnectivityKeys(
                        keyWallet, split.connectivityKeysReward(), reason, referenceId, connectivityExpiry)));

        keyWallet.creditKeysCents(split.purchaseKeysReward(), split.connectivityKeysReward());
        keyWalletRepository.save(keyWallet);
    }
}