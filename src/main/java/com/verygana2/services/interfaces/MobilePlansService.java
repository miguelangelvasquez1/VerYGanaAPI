package com.verygana2.services.interfaces;

import com.verygana2.dtos.wallet.requests.RechargeDataRequest;
import com.verygana2.dtos.wallet.responses.TransactionResponse;

public interface MobilePlansService {
    TransactionResponse rechargeData(RechargeDataRequest rechargeDataRequest);
}
