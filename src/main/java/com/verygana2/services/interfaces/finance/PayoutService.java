package com.verygana2.services.interfaces.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import com.verygana2.dtos.kushki.KushkiWebhookEvent;
import com.verygana2.dtos.payout.PayoutResponseDTO;

public interface PayoutService {

    BigDecimal getCommercialEarningsForPeriod(Long commercialId, Integer year, Integer month);

    /** Agrupa copayments COMPLETED del período y crea un Payout por empresario. */
    void scheduleDailyPayouts(ZonedDateTime periodStart, ZonedDateTime periodEnd);

    /** Ejecuta las transferencias Kushki para todos los payouts SCHEDULED. */
    void processScheduledPayouts();

    /** Reintenta los payouts FAILED del día anterior. */
    void retryFailedPayouts(ZonedDateTime previousPeriodStart, ZonedDateTime previousPeriodEnd);

    /** Procesa la confirmación de Kushki vía webhook y actualiza el Payout correspondiente. */
    void handleKushkiWebhook(KushkiWebhookEvent event, Map<String, Object> metadata);

    /** Para el endpoint de monitoreo del admin. */
    List<PayoutResponseDTO> getPayoutsForDate(LocalDate date);
}