package com.verygana2.repositories.finance;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.finance.KeyWallet;

@Repository
public interface KeyWalletRepository extends JpaRepository<KeyWallet, UUID> {

    Optional<KeyWallet> findByConsumerId(Long consumerId);

    boolean existsByConsumerId (Long consumerId);
}
