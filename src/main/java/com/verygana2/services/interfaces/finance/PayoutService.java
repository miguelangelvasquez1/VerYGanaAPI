package com.verygana2.services.interfaces.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import com.verygana2.dtos.payout.PayoutResponseDTO;

public interface PayoutService {

    BigDecimal getCommercialEarningsForPeriod(Long commercialId, Integer year, Integer month);

    /** Agrupa copayments COMPLETED del período y crea un Payout por empresario. */
    void scheduleDailyPayouts(ZonedDateTime periodStart, ZonedDateTime periodEnd);

    /** Ejecuta las transferencias Wompi para todos los payouts SCHEDULED. */
    void processScheduledPayouts();

    /** Reintenta los payouts FAILED del día anterior. */
    void retryFailedPayouts(ZonedDateTime previousPeriodStart, ZonedDateTime previousPeriodEnd);

    /** Para el endpoint de monitoreo del admin. */
    List<PayoutResponseDTO> getPayoutsForDate(LocalDate date);
}