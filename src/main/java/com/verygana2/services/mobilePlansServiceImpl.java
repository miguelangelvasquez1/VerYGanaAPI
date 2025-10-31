package com.verygana2.services;

import com.verygana2.dtos.wallet.requests.RechargeDataRequest;
import com.verygana2.dtos.wallet.responses.TransactionResponse;
import com.verygana2.services.interfaces.MobilePlansService;

public class mobilePlansServiceImpl implements MobilePlansService{

    // we does not know how we can make this method yet due to we need more
    // information
    @Override
    public TransactionResponse rechargeData(RechargeDataRequest rechargeDataRequest) {
        throw new UnsupportedOperationException("Unimplemented method 'rechargeData'");
    }
    
}
