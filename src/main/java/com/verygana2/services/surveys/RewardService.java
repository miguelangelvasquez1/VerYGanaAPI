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

import com.verygana2.exceptions.BusinessException;
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
import com.verygana2.services.interfaces.levels.LevelService;

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
    private final LevelService levelService;

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

        // Sin try/catch a propósito: grantReward corre dentro de la transacción de
        // submitSurvey, así que atraparlo aquí commiteaba un estado imposible —
        // sesión COMPLETED, cupo y presupuesto del anunciante consumidos, reward en
        // FAILED, y las llaves acreditadas igual porque la entidad ya estaba sucia
        // en el persistence context. Dejar propagar revierte las cuatro cosas y el
        // usuario puede reenviar la encuesta.
        long creditedCents;
        try {
            creditedCents = creditPoints(session, rewardAmount);
        } catch (RuntimeException e) {
            // BusinessException sigue siendo RuntimeException, así que la transacción
            // de submitSurvey se revierte igual — la encuesta no queda consumida.
            // Lo único que cambia es que el usuario recibe un 422 accionable en vez
            // del "Unexpected error" del catch-all de GlobalExceptionHandler.
            log.error("Failed to credit survey reward for consumer {} survey {}: {}",
                    session.getConsumer().getId(), session.getSurvey().getId(), e.getMessage(), e);
            throw new BusinessException(
                    "No pudimos acreditar tu recompensa en este momento. "
                            + "Tus respuestas no se guardaron: vuelve a enviar la encuesta en unos minutos.");
        }

        reward.setCreditedAmountCents(creditedCents);
        reward.setStatus(SurveyReward.RewardStatus.PROCESSED);
        reward.setProcessedAt(ZonedDateTime.now());
        log.info("Reward granted to consumer {} for survey {}: {} ¢",
                session.getConsumer().getId(), session.getSurvey().getId(), rewardAmount);
        eventPublisher.publishEvent(
                new XpAwardRequestedEvent(this, session.getConsumer().getId(), ActivityType.SURVEY_COMPLETED));

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
                        // Lo acreditado, no lo financiado: son cifras distintas
                        // en cuanto el multiplicador de nivel no es 1.0.
                        .amountKeys(creditedOf(r) / keyValueCents)
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

    /** Lo acreditado, con respaldo a la base para las filas previas a la columna. */
    private static long creditedOf(SurveyReward reward) {
        return reward.getCreditedAmountCents() != null
                ? reward.getCreditedAmountCents()
                : reward.getAmountCents();
    }

    /** @return lo realmente acreditado en la billetera, en centavos. */
    private long creditPoints(SurveySession session, long amountCents) {
        Long consumerId = session.getConsumer().getId();
        KeyWallet keyWallet = keyWalletService.getByConsumerId(consumerId);
        long adjustedCents = Math.round(amountCents * levelService.getMultiplier(consumerId));
        RewardSplit split = keyWalletService.calculate(adjustedCents);

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

        // El diferencial (amountCents − adjustedCents) lo liquida en tesorería
        // KeyIssuanceSettlementService por lotes, leyendo las dos cifras que esta
        // recompensa deja persistidas.

        return adjustedCents;
    }
}