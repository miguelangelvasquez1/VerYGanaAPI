package com.verygana2.services.interfaces.finance;

public interface KeyExpiryService {

    /**
     * Procesa todos los créditos de llaves cuyo expires_at < NOW():
     * - Debita purchaseKeys y/o connectivityKeys del KeyWallet de cada usuario.
     * - Crea un DEBIT_EXPIRY por usuario.
     * - Mueve el valor en COP de KEYS_RESERVE → FORTIFICATION en la tesorería.
     * - Loguea alerta si KEYS_RESERVE cae por debajo de los umbrales configurados.
     */
    void processExpiredKeys();
}
