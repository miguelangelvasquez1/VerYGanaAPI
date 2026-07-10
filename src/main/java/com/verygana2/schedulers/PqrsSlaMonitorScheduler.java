package com.verygana2.schedulers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.verygana2.services.interfaces.pqrs.PqrsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Alerta diariamente a los admins con PQRS a punto de vencer (o ya vencidos)
 * dentro del plazo legal, para que no incumplan la respuesta.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PqrsSlaMonitorScheduler {

    private final PqrsService pqrsService;

    @Value("${pqrs.sla-alert.days-before-due-date:2}")
    private int daysBeforeDueDateToAlert;

    @Scheduled(cron = "${pqrs.sla-alert.cron:0 0 8 * * *}", zone = "UTC")
    public void sendSlaAlerts() {
        log.info("[SCHEDULER] Revisando PQRS próximos a vencer...");
        pqrsService.sendSlaAlerts(daysBeforeDueDateToAlert);
    }
}
