package com.verygana2.services.finance;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.models.finance.KeyTransaction;
import com.verygana2.models.finance.KeyWallet;
import com.verygana2.repositories.finance.KeyTransactionRepository;
import com.verygana2.repositories.finance.KeyWalletRepository;
import com.verygana2.services.interfaces.finance.KeyExpiryService;
import com.verygana2.services.interfaces.finance.TreasuryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeyExpiryServiceImpl implements KeyExpiryService {

    private final KeyTransactionRepository keyTransactionRepository;
    private final KeyWalletRepository keyWalletRepository;
    private final TreasuryService treasuryService;

    /** Centavos de COP que vale cada llave. Configurable en treasury.values.key-value */
    @Value("${treasury.values.key-value:1000}")
    private long keyValueCents;

    private static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z");

    @Override
    @Transactional
    public void processExpiredKeys() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        UUID batchId = UUID.randomUUID();
        String periodLabel = now.withZoneSameInstant(COLOMBIA).format(PERIOD_FMT);

        log.info("[KEY-EXPIRY] Iniciando batch={} a las {}", batchId, periodLabel);

        List<KeyTransaction> expired = keyTransactionRepository.findExpiredNotProcessed(now);

        if (expired.isEmpty()) {
            log.info("[KEY-EXPIRY] Sin llaves vencidas. Nada que procesar.");
            return;
        }

        log.info("[KEY-EXPIRY] Encontrados {} créditos vencidos no procesados.", expired.size());

        // Agrupar por wallet — LinkedHashMap mantiene orden de inserción para logs reproducibles
        Map<KeyWallet, WalletExpiry> groups = new LinkedHashMap<>();
        for (KeyTransaction kt : expired) {
            KeyWallet wallet = kt.getKeyWallet();
            WalletExpiry expiry = groups.computeIfAbsent(wallet, k -> new WalletExpiry());

            if (kt.getPurchaseKeysDelta() != null && kt.getPurchaseKeysDelta() > 0) {
                expiry.purchaseSum += kt.getPurchaseKeysDelta();
            }
            if (kt.getConnectivityKeysDelta() != null && kt.getConnectivityKeysDelta() > 0) {
                expiry.connectivitySum += kt.getConnectivityKeysDelta();
            }
            expiry.transactionIds.add(kt.getId());
        }

        long totalExpiredKeys = 0;

        for (Map.Entry<KeyWallet, WalletExpiry> entry : groups.entrySet()) {
            KeyWallet wallet = entry.getKey();
            WalletExpiry expiry = entry.getValue();

            // Nunca expirar más de lo que hay disponible (las bloqueadas se respetan)
            long actualPurchaseExpiry = Math.min(wallet.getPurchaseKeys(), expiry.purchaseSum);
            long actualConnectivityExpiry = Math.min(wallet.getConnectivityKeys(), expiry.connectivitySum);

            if (actualPurchaseExpiry == 0 && actualConnectivityExpiry == 0) {
                log.debug("[KEY-EXPIRY] Wallet {} sin llaves disponibles para expirar (ya gastadas).",
                        wallet.getId());
                keyTransactionRepository.markAllAsProcessed(expiry.transactionIds);
                continue;
            }

            try {
                if (actualPurchaseExpiry > 0) {
                    wallet.expirePurchaseKeys(actualPurchaseExpiry);
                }
                if (actualConnectivityExpiry > 0) {
                    wallet.expireConnectivityKeys(actualConnectivityExpiry);
                }

                keyWalletRepository.save(wallet);

                KeyTransaction expiryTx = KeyTransaction.forExpiry(
                        wallet, actualPurchaseExpiry, actualConnectivityExpiry,
                        batchId, periodLabel);
                keyTransactionRepository.save(Objects.requireNonNull(expiryTx));

                keyTransactionRepository.markAllAsProcessed(expiry.transactionIds);

                totalExpiredKeys += actualPurchaseExpiry + actualConnectivityExpiry;

                log.debug("[KEY-EXPIRY] Wallet {}: purchase={}, connectivity={} llaves expiradas.",
                        wallet.getId(), actualPurchaseExpiry, actualConnectivityExpiry);

            } catch (Exception e) {
                log.error("[KEY-EXPIRY] Error expirando wallet {}: {}", wallet.getId(), e.getMessage(), e);
                // No relanzamos: un error en un usuario no debe bloquear a los demás.
                // La transacción de este bloque específico se verá afectada si la excepción
                // es de BD; en ese caso el job volverá a procesar al usuario en el siguiente ciclo
                // porque expiryProcessed quedará en false.
            }
        }

        if (totalExpiredKeys > 0) {
            long totalCents = totalExpiredKeys * keyValueCents;
            log.info("[KEY-EXPIRY] Total vencido: {} llaves = {} centavos COP. Moviendo a FORTIFICATION.",
                    totalExpiredKeys, totalCents);
            treasuryService.moveExpiredKeysToFortification(totalCents, batchId);
        }

        log.info("[KEY-EXPIRY] Batch={} completado. {} usuarios procesados, {} llaves vencidas.",
                batchId, groups.size(), totalExpiredKeys);
    }

    // ─── Auxiliar de agrupación ───────────────────────────────────────────────

    private static class WalletExpiry {
        long purchaseSum = 0;
        long connectivitySum = 0;
        final List<UUID> transactionIds = new ArrayList<>();
    }
}
