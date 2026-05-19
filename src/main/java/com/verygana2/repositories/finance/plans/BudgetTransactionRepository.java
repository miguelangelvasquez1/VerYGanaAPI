package com.verygana2.repositories.finance.plans;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.finance.plans.BudgetTransaction;
import com.verygana2.models.finance.plans.BudgetTransaction.TransactionType;

@Repository
public interface BudgetTransactionRepository extends JpaRepository<BudgetTransaction, Long> {

    List<BudgetTransaction> findByWalletIdOrderByCreatedAtDesc(Long walletId);

    List<BudgetTransaction> findByWalletIdAndType(Long walletId, TransactionType type);
}
