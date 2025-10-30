package com.verygana2.services.interfaces;

import java.math.BigDecimal;

import com.verygana2.models.treasury.PlatformTreasury;

public interface PlatformTreasuryService {
    PlatformTreasury getTreasury();
    void addProductsSaleCommission(BigDecimal amount, String referenceId, String description);
    void recordRealMoneyDeposit(BigDecimal amount, String paymentReference, String description);
    void recordProductSaleRefund(BigDecimal amount, String refundReferenceId, String reason);
    void reserveForWithdrawal (BigDecimal amount, String withdrawalReference, String description);
    void completeWithdrawal (BigDecimal amount, String withdrawalReference, String description);
    void cancelWithdrawalReservation(BigDecimal amount, String withdrawalReference, String description);
    void addRaffleCommission(BigDecimal amount, String referenceId, String description);
    void addAdCommission(BigDecimal amount, String referenceId, String description);
    BigDecimal getTotalBalance();
    BigDecimal getAvailableBalance();
    BigDecimal getReservedBalance();
}
