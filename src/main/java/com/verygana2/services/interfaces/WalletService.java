package com.verygana2.services.interfaces;

import java.math.BigDecimal;

import com.verygana2.dtos.wallet.requests.DepositRequest;
import com.verygana2.dtos.wallet.requests.TransferRequest;
import com.verygana2.dtos.wallet.requests.WithdrawalRequest;
import com.verygana2.dtos.wallet.responses.TransactionResponse;
import com.verygana2.dtos.wallet.responses.WalletResponse;
import com.verygana2.models.User;
import com.verygana2.models.Wallet;

public interface WalletService {

    // Internal wallet methods

    // Creation
    void createWallet(User user);

    // Get
    Wallet getByOwnerId(Long ownerId);
    WalletResponse getWalletByOwnerId(Long ownerId);

    // Operations
    TransactionResponse doDeposit(Long userId, DepositRequest depositRequest);
    TransactionResponse doWithdrawal(Long userId, WithdrawalRequest withdrawalRequest);
    TransactionResponse transferToUser(Long senderId, TransferRequest transferRequest);
    
    // Balance Queries
    BigDecimal getAvailableBalance(Long ownerId);
    BigDecimal getBlockedBalance(Long ownerId);

    // auxiliar wallet methods for others app sections
    void addPointsForWatchingAdAndLike(Long userId, BigDecimal reward, Long advertiserId);
}
