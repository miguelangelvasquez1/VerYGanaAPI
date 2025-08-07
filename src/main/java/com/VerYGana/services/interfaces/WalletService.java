package com.VerYGana.services.interfaces;


import com.VerYGana.models.Wallet;

public interface WalletService {
    Wallet getWalletByUserId(Long userId);
}
