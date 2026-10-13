package com.verygana2.services.finance;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.verygana2.dtos.finance.responses.KeyExpiryRunResponseDTO;
import com.verygana2.exceptions.BusinessException;
import com.verygana2.repositories.finance.KeyLotRepository;
import com.verygana2.services.interfaces.finance.KeyExpiryService;
import com.verygana2.services.interfaces.finance.KeyLedgerService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeyExpiryServiceImpl implements KeyExpiryService {

    private final KeyLotRepository keyLotRepository;
    private final KeyLedgerService keyLedgerService;
    private final Clock clock;

    @Value("${financial.allow-simulated-expiry:false}")
    private boolean allowSimulatedExpiry;

    @Override
    public KeyExpiryRunResponseDTO processExpiredKeys() {
        return run(ZonedDateTime.now(clock), false);
    }

    @Override
    public KeyExpiryRunResponseDTO processExpiredKeysAsOf(ZonedDateTime asOf) {
        if (!allowSimulatedExpiry) {
            throw new BusinessException("El vencimiento con fecha simulada no está habilitado en este ambiente");
        }
        log.warn("[KEY-EXPIRY] Corrida con fecha simulada: {}", asOf);
        return run(asOf, true);
    }

    /**
     * Sin transacción propia a propósito: cada billetera vence en la suya
     * ({@link KeyLedgerService#expireDueLots}). Así el fallo de una no revierte a las
     * demás, y la que falla conserva sus lotes con saldo para el siguiente ciclo.
     */
    private KeyExpiryRunResponseDTO run(ZonedDateTime now, boolean simulated) {
        List<UUID> walletIds = keyLotRepository.findWalletIdsWithDueLots(now);
        if (walletIds.isEmpty()) {
            log.info("[KEY-EXPIRY] Sin llaves vencidas. Nada que procesar.");
            return new KeyExpiryRunResponseDTO(now, simulated, 0, 0, 0L);
        }

        log.info("[KEY-EXPIRY] {} billeteras con lotes vencidos.", walletIds.size());

        long totalExpiredCents = 0;
        int failed = 0;
        for (UUID walletId : walletIds) {
            try {
                totalExpiredCents += keyLedgerService.expireDueLots(walletId, now);
            } catch (Exception e) {
                failed++;
                log.error("[KEY-EXPIRY] Error expirando wallet {}: {}", walletId, e.getMessage(), e);
            }
        }

        log.info("[KEY-EXPIRY] Completado: {} billeteras procesadas, {} con error, {} centavos de llaves vencidas.",
                walletIds.size() - failed, failed, totalExpiredCents);
        return new KeyExpiryRunResponseDTO(now, simulated, walletIds.size(), failed, totalExpiredCents);
    }
}
