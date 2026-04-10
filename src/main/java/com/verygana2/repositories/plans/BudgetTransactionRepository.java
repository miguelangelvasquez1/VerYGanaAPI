package com.verygana2.repositories.plans;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.plans.BudgetTransaction;
import com.verygana2.models.plans.BudgetTransaction.TransactionType;

@Repository
public interface BudgetTransactionRepository extends JpaRepository<BudgetTransaction, Long> {

    List<BudgetTransaction> findByBudgetIdOrderByCreatedAtDesc(Long budgetId);

    List<BudgetTransaction> findByBudgetIdAndType(Long budgetId, TransactionType type);
}