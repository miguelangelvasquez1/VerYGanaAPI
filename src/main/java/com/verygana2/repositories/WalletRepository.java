package com.verygana2.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.Wallet;

import jakarta.persistence.LockModeType;


@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {
    Optional<Wallet> findByUserId(Long ownerId);
    boolean existsByUserId(Long ownerId);

    // MUY IMPORTANTE PARA QUE NO SE CREEN DOS COSAS A LA VEZ CUANDO SE VALIDA EL SALDO
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.user.id = :userId")
    Wallet findWalletForUpdate(@Param("userId") Long userId);
}
