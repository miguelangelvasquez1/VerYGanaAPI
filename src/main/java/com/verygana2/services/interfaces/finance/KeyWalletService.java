package com.verygana2.services.interfaces.finance;

import com.verygana2.dtos.keys.KeyBalanceResponseDTO;
import com.verygana2.dtos.keys.SpendKeysRequestDTO;
import com.verygana2.dtos.keys.SpendKeysResponseDTO;
import java.time.ZonedDateTime;

import com.verygana2.models.finance.KeyWallet;
import com.verygana2.services.finance.KeyWalletServiceImpl.RewardSplit;

public interface KeyWalletService {
    void createFor (Long consumerId);
    /** Solo lectura. Para modificar el saldo, {@link #getByConsumerIdForUpdate}. */
    KeyWallet getByConsumerId (Long consumerId);

    /**
     * La billetera bloqueada hasta el fin de la transacción. Úsese en toda ruta que
     * cambie el saldo; requiere una transacción abierta.
     */
    KeyWallet getByConsumerIdForUpdate(Long consumerId);
    RewardSplit calculate(long totalRewardKeysCents);
    ZonedDateTime calculatePurchaseExpiry();
    ZonedDateTime calculateConnectivityExpiry();

    KeyBalanceResponseDTO getBalance(Long consumerId);
    SpendKeysResponseDTO spendKeysForPetGame(Long consumerId, SpendKeysRequestDTO request);
}