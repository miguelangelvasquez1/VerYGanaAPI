package com.Rifacel.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.Rifacel.models.Wallet;

public interface WalletRepository extends JpaRepository<Wallet, String> {
    // Additional query methods can be defined here if needed  
}
