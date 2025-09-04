package com.VerYGana.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.VerYGana.models.Wallet;
import com.VerYGana.models.Enums.WalletOwnerType;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {
    Optional<Wallet> findByOwnerIdAndOwnerType(Long ownerId, WalletOwnerType ownerType);
    boolean existsByOwnerIdAndOwnerType(Long ownerId, WalletOwnerType ownerType);
    List<Wallet> findByOwnerType(WalletOwnerType ownerType);
}
