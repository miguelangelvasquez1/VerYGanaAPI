package com.verygana2.schedulers;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.models.enums.finance.WalletBudgetAlertStage;
import com.verygana2.models.enums.finance.WalletStatus;
import com.verygana2.models.finance.Wallet;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.WalletRepository;
import com.verygana2.services.interfaces.EmailService;
import com.verygana2.services.interfaces.NotificationService;
import com.verygana2.services.plans.EffectivePlanResolver;
import com.verygana2.services.plans.EffectivePlanResolver.BudgetThresholds;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Barrido periódico de saldo bajo — avisa al comercial en etapas (WARNING → CRITICAL
 * → EXHAUSTED) sin repetir el mismo aviso. La suspensión en sí (bloqueo de creación
 * de activos nuevos) no vive aquí — es derivada en tiempo real por
 * {@link com.verygana2.services.plans.PlanFeatureGuard#assertBudgetAvailable}; este
 * scheduler solo se encarga de la notificación proactiva.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BudgetAlertScheduler {

    private final WalletRepository walletRepository;
    private final EffectivePlanResolver planResolver;
    private final EmailService emailService;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void checkBudgetAlerts() {
        List<Wallet> atRisk = walletRepository.findByStatusIn(
                List.of(WalletStatus.LOW_BALANCE, WalletStatus.EXHAUSTED));

        if (atRisk.isEmpty()) {
            return;
        }

        log.info("[BUDGET ALERT JOB] Revisando {} wallets con saldo bajo/agotado.", atRisk.size());

        for (Wallet wallet : atRisk) {
            try {
                checkWallet(wallet);
            } catch (Exception e) {
                log.error("[BUDGET ALERT JOB] Error revisando wallet {}: {}", wallet.getId(), e.getMessage(), e);
            }
        }
    }

    private void checkWallet(Wallet wallet) {
        WalletBudgetAlertStage targetStage = resolveStage(wallet);

        if (!isMoreSevere(targetStage, wallet.getLastBudgetAlertStage())) {
            return;
        }

        CommercialDetails commercial = wallet.getCommercial();
        String email = commercial.getUser().getEmail();
        String name = commercial.getCompanyName();

        switch (targetStage) {
            case EXHAUSTED -> emailService.sendBudgetExhaustedEmail(email, name);
            case CRITICAL -> emailService.sendBudgetLowWarningEmail(email, name, true);
            case WARNING -> emailService.sendBudgetLowWarningEmail(email, name, false);
            case NONE -> { return; }
        }

        notificationService.createInternalNotification(commercial.getUser().getId(),
                "Saldo publicitario " + stageLabel(targetStage),
                "Revisa tu billetera para evitar interrupciones en la creación de nuevos activos.",
                Instant.now());

        wallet.setLastBudgetAlertStage(targetStage);
        wallet.setLastBudgetAlertAt(ZonedDateTime.now(ZoneOffset.UTC));
        walletRepository.save(wallet);

        log.info("[BUDGET ALERT JOB] Aviso {} enviado — commercialId={}", targetStage, commercial.getId());
    }

    private WalletBudgetAlertStage resolveStage(Wallet wallet) {
        if (wallet.isExhausted()) {
            return WalletBudgetAlertStage.EXHAUSTED;
        }
        BudgetThresholds thresholds = planResolver.resolveBudgetThresholds(wallet);
        long balance = wallet.getBalanceCents();
        if (thresholds.criticalCents() > 0 && balance < thresholds.criticalCents()) {
            return WalletBudgetAlertStage.CRITICAL;
        }
        if (thresholds.warningCents() > 0 && balance < thresholds.warningCents()) {
            return WalletBudgetAlertStage.WARNING;
        }
        return WalletBudgetAlertStage.NONE;
    }

    private boolean isMoreSevere(WalletBudgetAlertStage target, WalletBudgetAlertStage current) {
        return severityRank(target) > severityRank(current == null ? WalletBudgetAlertStage.NONE : current);
    }

    private int severityRank(WalletBudgetAlertStage stage) {
        return switch (stage) {
            case NONE -> 0;
            case WARNING -> 1;
            case CRITICAL -> 2;
            case EXHAUSTED -> 3;
        };
    }

    private String stageLabel(WalletBudgetAlertStage stage) {
        return switch (stage) {
            case WARNING -> "bajo";
            case CRITICAL -> "crítico";
            case EXHAUSTED -> "agotado";
            case NONE -> "";
        };
    }
}
