package com.verygana2.schedulers;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.verygana2.services.interfaces.pqrs.PqrsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reintenta asignar un admin a los PQRS que quedaron sin asignación porque no
 * había ningún admin activo en el momento de la creación (caso borde).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PqrsAssignmentRetryScheduler {

    private final PqrsService pqrsService;

    @Scheduled(cron = "${pqrs.assignment-retry.cron:0 */15 * * * *}")
    public void retryPendingAssignments() {
        log.debug("[SCHEDULER] Reintentando asignación de PQRS pendientes...");
        pqrsService.retryPendingAssignments();
    }
}
