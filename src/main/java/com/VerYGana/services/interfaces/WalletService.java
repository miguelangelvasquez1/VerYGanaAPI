package com.VerYGana.services.interfaces;

import java.math.BigDecimal;

import com.VerYGana.dtos.Wallet.Requests.BlockBalanceRequest;
import com.VerYGana.dtos.Wallet.Requests.DepositRequest;

import com.VerYGana.dtos.Wallet.Requests.RafflePrizeRequest;
import com.VerYGana.dtos.Wallet.Requests.RechargeDataRequest;
import com.VerYGana.dtos.Wallet.Requests.TransferRequest;
import com.VerYGana.dtos.Wallet.Requests.UnblockBalanceRequest;
import com.VerYGana.dtos.Wallet.Requests.WalletCreateRequest;
import com.VerYGana.dtos.Wallet.Requests.WithdrawalRequest;
import com.VerYGana.dtos.Wallet.Responses.TransactionResponse;
import com.VerYGana.dtos.Wallet.Responses.WalletCreateResponse;
import com.VerYGana.dtos.Wallet.Responses.WalletResponse;

public interface WalletService {

    // Internal wallet methods

    // Creation
    WalletCreateResponse createWallet(WalletCreateRequest walletCreateRequest);

    // Get
    WalletResponse getWalletByOwnerId(Long ownerId);

    // Operations
    TransactionResponse doDeposit(Long userId, DepositRequest depositRequest);
    TransactionResponse doWithdrawal(Long userId, WithdrawalRequest withdrawalRequest);
    TransactionResponse transferToUser(Long senderId, TransferRequest transferRequest);
    
    // Balance Queries
    BigDecimal getAvailableBalance(Long ownerId);
    BigDecimal getBlockedBalance(Long ownerId);

    // Balance Management
    TransactionResponse blockBalance(BlockBalanceRequest blockBalanceRequest);
    TransactionResponse UnblockBalance(UnblockBalanceRequest unblockBalanceRequest);

    TransactionResponse rechargeData(RechargeDataRequest rechargeDataRequest);

    // auxiliar wallet methods for others app sections
    void addPointsForWatchingAdAndLike(Long userId, BigDecimal reward, Long advertiserId);
    void addPointsForReferral(Long userId, BigDecimal amount, Long userReferiedId);
    void addRafflePrize(RafflePrizeRequest rafflePrizeRequest);
    void participateInRaffle(Long userId, BigDecimal amount);
    void doPurchase(Long buyerId, BigDecimal amount, Long sellerId);

}
