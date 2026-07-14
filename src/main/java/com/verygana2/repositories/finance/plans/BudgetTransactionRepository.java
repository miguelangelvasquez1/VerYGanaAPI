package com.verygana2.repositories.finance.plans;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.finance.plans.BudgetTransaction;
import com.verygana2.models.finance.plans.BudgetTransaction.TransactionType;

@Repository
public interface BudgetTransactionRepository extends JpaRepository<BudgetTransaction, Long> {

    List<BudgetTransaction> findByWalletIdAndType(Long walletId, TransactionType type);

    /** Suma total de gasto publicitario en un período — card "Gastado este mes". */
    @Query("SELECT COALESCE(SUM(bt.amountCents), 0) FROM BudgetTransaction bt " +
           "WHERE bt.wallet.id = :walletId " +
           "AND bt.createdAt >= :from AND bt.createdAt < :to")
    BigDecimal sumByWalletIdAndPeriod(
            @Param("walletId") Long walletId,
            @Param("from") ZonedDateTime from,
            @Param("to") ZonedDateTime to);
}
