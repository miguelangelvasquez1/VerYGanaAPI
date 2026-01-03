package com.verygana2.services.interfaces;

import com.verygana2.dtos.wompi.WompiDepositRequest;
import com.verygana2.dtos.wompi.WompiDepositResponse;

public interface WompiPaymentService {
    WompiDepositResponse initiateDeposit (Long userId, WompiDepositRequest request, String ipAddress);
    void confirmDeposit(String wompiTransactionId);
    void declineDeposit(String wompiTransactionId, String reason);
}
