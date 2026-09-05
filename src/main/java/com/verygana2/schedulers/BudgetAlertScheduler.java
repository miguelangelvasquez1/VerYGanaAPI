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

    @Scheduled(cron = "${budget.alert-cron:0 0 * * * *}", zone = "UTC")
    @Transactional
    public void checkBudgetAlerts() {
        // "Saldo bajo" no es un estado del wallet: se deriva aquí contra los umbrales
        // por plan (resolveStage). Por eso se barren todos los wallets financiados
        // (ACTIVE + EXHAUSTED); resolveStage() → NONE para los sanos, que se saltan.
        // INACTIVE (sin depósito) queda fuera.
        List<Wallet> atRisk = walletRepository.findByStatusIn(
                List.of(WalletStatus.ACTIVE, WalletStatus.EXHAUSTED));

        if (atRisk.isEmpty()) {
            return;
        }

        log.debug("[BUDGET ALERT JOB] Evaluando {} wallets financiados.", atRisk.size());

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
            case DORMANT -> emailService.sendBudgetDormantEmail(email, name);
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
            // Red de seguridad: sellar exhaustedSince si una billetera llegó a 0 sin pasar
            // luego por recalculateStatus() (p. ej. datos previos a esta funcionalidad).
            if (wallet.getExhaustedSince() == null) {
                wallet.setExhaustedSince(ZonedDateTime.now(ZoneOffset.UTC));
            }
            int graceDays = planResolver.resolveGracePeriodDays(wallet.getCommercial().getCurrentPlan());
            if (graceDays > 0 && wallet.getExhaustedSince()
                    .isBefore(ZonedDateTime.now(ZoneOffset.UTC).minusDays(graceDays))) {
                return WalletBudgetAlertStage.DORMANT;
            }
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
            case DORMANT -> 4;
        };
    }

    private String stageLabel(WalletBudgetAlertStage stage) {
        return switch (stage) {
            case WARNING -> "bajo";
            case CRITICAL -> "crítico";
            case EXHAUSTED -> "agotado";
            case DORMANT -> "en pausa";
            case NONE -> "";
        };
    }
}
