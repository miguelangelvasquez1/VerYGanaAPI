package com.Rifacel.services.interfaces;


import com.Rifacel.models.Wallet;

public interface WalletService {
    Wallet getWalletByUserId(String userId);
}
