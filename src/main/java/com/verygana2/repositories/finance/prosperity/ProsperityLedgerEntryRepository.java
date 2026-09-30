package com.verygana2.repositories.finance.prosperity;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.enums.finance.ProsperityEntryType;
import com.verygana2.models.finance.prosperity.ProsperityLedgerEntry;

@Repository
public interface ProsperityLedgerEntryRepository extends JpaRepository<ProsperityLedgerEntry, Long> {

    Optional<ProsperityLedgerEntry> findByIdempotencyKey(String idempotencyKey);

    Page<ProsperityLedgerEntry> findByAccountIdOrderBySequenceDesc(Long accountId, Pageable pageable);

    Optional<ProsperityLedgerEntry> findTopByAccountIdOrderBySequenceDesc(Long accountId);

    /** Σ créditos − Σ débitos del libro de una cuenta (conciliación). */
    @Query("SELECT COALESCE(SUM(CASE WHEN e.type IN :creditTypes THEN e.amountCents ELSE -e.amountCents END), 0) "
            + "FROM ProsperityLedgerEntry e WHERE e.account.id = :accountId")
    long sumSignedByAccountId(@Param("accountId") Long accountId,
            @Param("creditTypes") java.util.Collection<ProsperityEntryType> creditTypes);

    @Query("SELECT COALESCE(SUM(e.amountCents), 0) FROM ProsperityLedgerEntry e "
            + "WHERE e.account.id = :accountId AND e.type = :type")
    long sumAmountByAccountIdAndType(@Param("accountId") Long accountId,
            @Param("type") ProsperityEntryType type);
}
