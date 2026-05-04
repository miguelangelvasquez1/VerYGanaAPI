package com.verygana2.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.finance.Wallet;

import jakarta.persistence.LockModeType;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByCommercialId(Long commercialId);

    // Lock pesimista para operaciones de consumo concurrente (BudgetService)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.commercial.id = :commercialId")
    Optional<Wallet> findByCommercialIdForUpdate(@Param("commercialId") Long commercialId);

    boolean existsByCommercialId (Long commercialId);
}
