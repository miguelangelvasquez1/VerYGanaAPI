package com.verygana2.services.interfaces.finance;

import java.time.ZonedDateTime;

import com.verygana2.models.finance.KeyWallet;
import com.verygana2.services.finance.KeyWalletServiceImpl.RewardSplit;

public interface KeyWalletService {
    void createFor (Long consumerId);
    KeyWallet getByConsumerId (Long consumerId);
    RewardSplit calculate(long totalRewardKeysCents);
    ZonedDateTime calculatePurchaseExpiry();
    ZonedDateTime calculateConnectivityExpiry();
}