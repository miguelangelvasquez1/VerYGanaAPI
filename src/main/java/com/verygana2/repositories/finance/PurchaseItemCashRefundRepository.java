package com.verygana2.repositories.finance;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.enums.finance.CashRefundStatus;
import com.verygana2.models.finance.PurchaseItemCashRefund;

@Repository
public interface PurchaseItemCashRefundRepository extends JpaRepository<PurchaseItemCashRefund, UUID> {

    Optional<PurchaseItemCashRefund> findByPurchaseItemId(Long purchaseItemId);

    Page<PurchaseItemCashRefund> findByStatus(CashRefundStatus status, Pageable pageable);
}
