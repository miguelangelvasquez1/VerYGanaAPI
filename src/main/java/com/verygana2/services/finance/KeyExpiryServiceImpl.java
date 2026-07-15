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

            if (kt.getPurchaseKeysDeltaCents() != null && kt.getPurchaseKeysDeltaCents() > 0) {
                expiry.purchaseSumCents += kt.getPurchaseKeysDeltaCents();
            }
            if (kt.getConnectivityKeysDeltaCents() != null && kt.getConnectivityKeysDeltaCents() > 0) {
                expiry.connectivitySumCents += kt.getConnectivityKeysDeltaCents();
            }
            expiry.transactionIds.add(kt.getId());
        }

        long totalExpiredCents = 0;

        for (Map.Entry<KeyWallet, WalletExpiry> entry : groups.entrySet()) {
            KeyWallet wallet = entry.getKey();
            WalletExpiry expiry = entry.getValue();

            // Nunca expirar más de lo que hay disponible (las bloqueadas se respetan)
            long actualPurchaseExpiryCents = Math.min(wallet.getPurchaseKeysCents(), expiry.purchaseSumCents);
            long actualConnectivityExpiryCents = Math.min(wallet.getConnectivityKeysCents(), expiry.connectivitySumCents);

            if (actualPurchaseExpiryCents == 0 && actualConnectivityExpiryCents == 0) {
                log.debug("[KEY-EXPIRY] Wallet {} sin llaves disponibles para expirar (ya gastadas).",
                        wallet.getId());
                keyTransactionRepository.markAllAsProcessed(expiry.transactionIds);
                continue;
            }

            try {
                if (actualPurchaseExpiryCents > 0) {
                    wallet.expirePurchaseKeysCents(actualPurchaseExpiryCents);
                }
                if (actualConnectivityExpiryCents > 0) {
                    wallet.expireConnectivityKeysCents(actualConnectivityExpiryCents);
                }

                keyWalletRepository.save(wallet);

                KeyTransaction expiryTx = KeyTransaction.forExpiry(
                        wallet, actualPurchaseExpiryCents, actualConnectivityExpiryCents,
                        batchId, periodLabel);
                keyTransactionRepository.save(Objects.requireNonNull(expiryTx));

                keyTransactionRepository.markAllAsProcessed(expiry.transactionIds);

                totalExpiredCents += actualPurchaseExpiryCents + actualConnectivityExpiryCents;

                log.debug("[KEY-EXPIRY] Wallet {}: purchase={}, connectivity={} centavos de llaves expiradas.",
                        wallet.getId(), actualPurchaseExpiryCents, actualConnectivityExpiryCents);

            } catch (Exception e) {
                log.error("[KEY-EXPIRY] Error expirando wallet {}: {}", wallet.getId(), e.getMessage(), e);
                // No relanzamos: un error en un usuario no debe bloquear a los demás.
                // La transacción de este bloque específico se verá afectada si la excepción
                // es de BD; en ese caso el job volverá a procesar al usuario en el siguiente ciclo
                // porque expiryProcessed quedará en false.
            }
        }

        if (totalExpiredCents > 0) {
            log.info("[KEY-EXPIRY] Total vencido: {} centavos COP. Moviendo a FORTIFICATION.",
                    totalExpiredCents);
            treasuryService.moveExpiredKeysToFortification(totalExpiredCents, batchId);
        }

        log.info("[KEY-EXPIRY] Batch={} completado. {} usuarios procesados, {} centavos de llaves vencidas.",
                batchId, groups.size(), totalExpiredCents);
    }

    // ─── Auxiliar de agrupación ───────────────────────────────────────────────

    private static class WalletExpiry {
        long purchaseSumCents = 0;
        long connectivitySumCents = 0;
        final List<UUID> transactionIds = new ArrayList<>();
    }
}
