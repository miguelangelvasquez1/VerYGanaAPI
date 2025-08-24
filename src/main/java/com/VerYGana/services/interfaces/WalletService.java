package com.VerYGana.services.interfaces;


import java.math.BigDecimal;

import com.VerYGana.models.Advertiser;
import com.VerYGana.models.User;
import com.VerYGana.models.Wallet;

public interface WalletService {
    void createWallet(User user);
    Wallet getWalletByUserId(Long userId);

    // add Tpoints
    void doIncome(User user, BigDecimal Tpoints);
    void addPointsForWatchingAdAndLike(User user, BigDecimal Tpoints, Advertiser advertiser);
    void addPointsForReferral(User user, User referredUser, BigDecimal Tpoints);
    void addRafflePrize(User user, BigDecimal Tpoints);

    // Purchases and others
    void doWithdrawal(User user, BigDecimal Tpoints);
    void participateInRaffle (User user, BigDecimal Tpoints);
    void rechargeData(User user, BigDecimal Tpoints, String phoneNumber);
    void transferToUser(User sender,  BigDecimal Tpoints, User receiver);
    void doPurchase(User buyer, BigDecimal Tpoints, User seller);

    // Balance Queries
    BigDecimal getAvailableBalance(User user);
    BigDecimal getBlockedBalance(User user);

    // Balance Management
    void BlockBalance(User user, BigDecimal Tpoints, String reason);
    void UnBlockBalance(User user, BigDecimal Tpoints);
}
