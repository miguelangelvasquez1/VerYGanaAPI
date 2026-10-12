package com.verygana2.services.interfaces.finance;

import java.time.ZonedDateTime;

import com.verygana2.dtos.finance.responses.KeyExpiryRunResponseDTO;

public interface KeyExpiryService {

    /**
     * Extingue lo que queda de cada lote de llaves vencido, billetera por billetera:
     * - Debita purchaseKeys y/o connectivityKeys del KeyWallet de cada usuario.
     * - Registra un asiento EXPIRED por billetera, con el lote de origen.
     * - Mueve el respaldo en COP de KEYS_RESERVE → FORTIFICATION en la tesorería.
     */
    KeyExpiryRunResponseDTO processExpiredKeys();

    /**
     * Igual que {@link #processExpiredKeys()} pero evaluando los vencimientos como si
     * fuera {@code asOf}. Solo para pruebas: se rechaza si
     * {@code financial.allow-simulated-expiry} está apagado (producción).
     */
    KeyExpiryRunResponseDTO processExpiredKeysAsOf(ZonedDateTime asOf);
}
