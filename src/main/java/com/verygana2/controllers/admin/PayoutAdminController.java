package com.verygana2.controllers.admin;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.payout.PayoutResponseDTO;
import com.verygana2.services.interfaces.finance.PayoutService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin/payouts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PayoutAdminController {

    private static final ZoneId COLOMBIA_TZ = ZoneId.of("America/Bogota");

    private final PayoutService payoutService;

    /**
     * Lista todos los payouts de una fecha específica.
     * Si no se pasa fecha, usa el día de hoy (UTC).
     *
     * GET /api/admin/payouts
     * GET /api/admin/payouts?date=2025-01-15
     */
    @GetMapping
    public ResponseEntity<List<PayoutResponseDTO>> getPayoutsForDate(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date) {

        LocalDate target = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(payoutService.getPayoutsForDate(target));
    }

    /**
     * Dispara el ciclo diario de payouts manualmente, sin esperar al cron de las
     * 11 PM. Mismo efecto que PayoutScheduler.runDailyPayouts() — agrupa los
     * PurchaseItem CLAIMED sin payout y ejecuta las transferencias a Wompi.
     * Solo para pruebas/soporte; en producción normalmente lo dispara el cron.
     *
     * POST /api/admin/payouts/run-now
     */
    @PostMapping("/run-now")
    public ResponseEntity<Void> runNow() {
        ZonedDateTime periodEnd = ZonedDateTime.now(COLOMBIA_TZ).toLocalDate().atStartOfDay(COLOMBIA_TZ);
        ZonedDateTime periodStart = periodEnd.minusDays(1);

        payoutService.scheduleDailyPayouts(periodStart, periodEnd);
        payoutService.processScheduledPayouts();

        return ResponseEntity.ok().build();
    }

    /**
     * Reintenta manualmente todos los payouts actualmente FAILED, sin esperar
     * al cron de las 11:30 PM. Útil para probar el flujo de reintento sin
     * esperar al ciclo real.
     *
     * POST /api/admin/payouts/retry-now
     */
    @PostMapping("/retry-now")
    public ResponseEntity<Void> retryNow() {
        payoutService.retryFailedPayouts();
        return ResponseEntity.ok().build();
    }

    /**
     * Consulta directo en Wompi el estado real de un Payout ya enviado
     * (GET /payouts/{id} de Wompi), sin depender del webhook — para
     * diagnosticar cuando la confirmación no llega o no correlaciona.
     *
     * GET /api/admin/payouts/{id}/wompi-status
     */
    @GetMapping("/{id}/wompi-status")
    public ResponseEntity<Map<String, Object>> getWompiStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(payoutService.getWompiStatus(id));
    }
}
