package com.verygana2.services.interfaces;

import java.math.BigDecimal;

import com.verygana2.models.treasury.PlatformTreasury;

public interface PlatformTreasuryService {
    PlatformTreasury getTreasury();
    void addProductSaleCommission(BigDecimal amount, String referenceId, String description);
    void recordRealMoneyDeposit(BigDecimal amount, String paymentReference, BigDecimal gatewayCommission);
    void reserveForWithdrawal (BigDecimal amount, String withdrawalReference);
    void completeWithdrawal (BigDecimal amount, String withdrawalReference);
    void cancelWithdrawalReservation(BigDecimal amount, String withdrawalReference);
    void addRaffleCommission(BigDecimal amount, String referenceId);
    void addAdCommission(BigDecimal amount, String referenceId);
    BigDecimal getTotalBalance();
    BigDecimal getAvailaBigDecimal();
    BigDecimal getReservedBalance();
}
