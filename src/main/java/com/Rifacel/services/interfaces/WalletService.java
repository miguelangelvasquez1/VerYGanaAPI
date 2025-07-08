package com.Rifacel.services.interfaces;

import java.util.Optional;

import com.Rifacel.models.Wallet;

public interface WalletService {
    Optional<Wallet> getWalletByUserId(String userId);
}
