package com.VerYGana.services.interfaces;

import java.math.BigDecimal;

import com.VerYGana.dtos2.wallet2.requests2.DepositRequest;
import com.VerYGana.dtos2.wallet2.requests2.RafflePrizeRequest;
import com.VerYGana.dtos2.wallet2.requests2.RechargeDataRequest;
import com.VerYGana.dtos2.wallet2.requests2.TransferRequest;
import com.VerYGana.dtos2.wallet2.requests2.WithdrawalRequest;
import com.VerYGana.dtos2.wallet2.responses2.TransactionResponse;
import com.VerYGana.dtos2.wallet2.responses2.WalletResponse;

public interface WalletService {

    // Internal wallet methods

    // Creation
    void createWallet(Long userId);

    // Get
    WalletResponse getWalletByOwnerId(Long ownerId);

    // Operations
    TransactionResponse doDeposit(Long userId, DepositRequest depositRequest);
    TransactionResponse doWithdrawal(Long userId, WithdrawalRequest withdrawalRequest);
    TransactionResponse transferToUser(Long senderId, TransferRequest transferRequest);
    
    // Balance Queries
    BigDecimal getAvailableBalance(Long ownerId);
    BigDecimal getBlockedBalance(Long ownerId);

    TransactionResponse rechargeData(RechargeDataRequest rechargeDataRequest);

    // auxiliar wallet methods for others app sections
    void addPointsForWatchingAdAndLike(Long userId, BigDecimal reward, Long advertiserId);
    void addPointsForReferral(Long userId, BigDecimal amount, Long userReferiedId);
    void addRafflePrize(RafflePrizeRequest rafflePrizeRequest);
    void participateInRaffle(Long userId, BigDecimal amount);
    void doPurchase(Long buyerId, BigDecimal amount, Long sellerId);

}
