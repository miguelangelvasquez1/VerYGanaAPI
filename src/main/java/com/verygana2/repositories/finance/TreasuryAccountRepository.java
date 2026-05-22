package com.verygana2.repositories.finance;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.enums.finance.TreasuryAccountCode;
import com.verygana2.models.finance.TreasuryAccount;

import jakarta.persistence.LockModeType;

@Repository
public interface TreasuryAccountRepository extends JpaRepository<TreasuryAccount, UUID> {

    Optional<TreasuryAccount> findByCode(TreasuryAccountCode code);

    boolean existsByCode(TreasuryAccountCode code);

    /**
     * Busca una cuenta con PESSIMISTIC_WRITE lock.
     * Usado por TreasuryService antes de modificar saldos para evitar
     * race conditions cuando dos depósitos llegan simultáneamente.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TreasuryAccount t WHERE t.code = :code")
    Optional<TreasuryAccount> findByCodeForUpdate(@Param("code") TreasuryAccountCode code);

    @Query("SELECT COUNT(t) FROM TreasuryAccount t WHERE t.balanceCents < 0")
    long countNegativeBalances();
}