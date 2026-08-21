package com.verygana2.repositories.finance;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.enums.finance.CashRefundStatus;
import com.verygana2.models.finance.PurchaseItemCashRefund;

@Repository
public interface PurchaseItemCashRefundRepository extends JpaRepository<PurchaseItemCashRefund, UUID> {

    Optional<PurchaseItemCashRefund> findByPurchaseItemId(Long purchaseItemId);

    @Query("""
            SELECT p FROM PurchaseItemCashRefund p
            WHERE (:status IS NULL OR p.status = :status) 
            AND (:startDate IS NULL OR p.createdAt >= :startDate)
            AND (:endDate IS NULL OR p.createdAt < :endDate)
            ORDER BY p.createdAt DESC
            """)
    Page<PurchaseItemCashRefund> findByStatusAndRangeDates(@Param("status") CashRefundStatus status, @Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate, Pageable pageable);
}
