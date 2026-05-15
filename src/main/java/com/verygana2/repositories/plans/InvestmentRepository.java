package com.verygana2.repositories.plans;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.finance.plans.Investment;

@Repository
public interface InvestmentRepository extends JpaRepository<Investment, Long> {

    List<Investment> findByWalletIdOrderByCreatedAtDesc(Long walletId);
}
