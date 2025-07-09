package com.Rifacel.services;

import org.hibernate.ObjectNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.Rifacel.models.Wallet;
import com.Rifacel.repositories.WalletRepository;
import com.Rifacel.services.interfaces.WalletService;


@Service
public class WalletServiceImpl implements WalletService{

    @Autowired
    private WalletRepository walletRepository;

    // devuelve la billetera del usuario por su ID
    @Override
    public Wallet getWalletByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        return walletRepository.findByUserId(userId).orElseThrow(() -> new ObjectNotFoundException("Wallet", Wallet.class));
    }
    
}
