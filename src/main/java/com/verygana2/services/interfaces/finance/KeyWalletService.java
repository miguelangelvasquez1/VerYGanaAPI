package com.verygana2.services.interfaces.finance;

import com.verygana2.dtos.keys.KeyBalanceResponseDTO;
import com.verygana2.dtos.keys.SpendKeysRequestDTO;
import com.verygana2.dtos.keys.SpendKeysResponseDTO;
import com.verygana2.models.finance.KeyWallet;

public interface KeyWalletService {
    void createFor (Long consumerId);
    KeyWallet getByConsumerId (Long consumerId);

    KeyBalanceResponseDTO getBalance(Long consumerId);
    SpendKeysResponseDTO spendKeysForPetGame(Long consumerId, SpendKeysRequestDTO request);
}