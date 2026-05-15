package com.verygana2.services.interfaces.finance;

import com.verygana2.models.finance.KeyWallet;

public interface KeyWalletService {
    void createFor (Long consumerId);
    KeyWallet getByConsumerId (Long consumerId);
}