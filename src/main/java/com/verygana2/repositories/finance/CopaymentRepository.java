package com.verygana2.repositories.finance;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.enums.finance.CopaymentStatus;
import com.verygana2.models.finance.Copayment;

@Repository
public interface CopaymentRepository extends JpaRepository<Copayment, UUID> {

    @Query("""
            SELECT DISTINCT c FROM Copayment c
            JOIN FETCH c.purchase p
            LEFT JOIN FETCH p.items items
            LEFT JOIN FETCH items.product prod
            LEFT JOIN FETCH prod.imageAsset
            LEFT JOIN FETCH items.assignedProductStock
            JOIN FETCH c.consumer
            WHERE p.referenceId = :referenceId
            """)
    Optional<Copayment> findByPurchaseReferenceIdWithDetails(@Param("referenceId") String referenceId);

    @Query("""
            SELECT DISTINCT c FROM Copayment c
            JOIN FETCH c.purchase p
            LEFT JOIN FETCH p.items items
            LEFT JOIN FETCH items.product
            LEFT JOIN FETCH items.assignedProductStock
            JOIN FETCH c.consumer con
            JOIN FETCH con.user
            WHERE c.status = :status
            AND p.createdAt < :before
            """)
    List<Copayment> findExpiredPending(
            @Param("status") CopaymentStatus status,
            @Param("before") ZonedDateTime before);

    /**
     * Copayments COMPLETED en el período, con todos sus ítems cargados para
     * la agrupación por empresario en el PayoutScheduler.
     */
    @Query("""
            SELECT DISTINCT c FROM Copayment c
            JOIN FETCH c.purchase p
            JOIN FETCH p.items items
            JOIN FETCH items.product prod
            JOIN FETCH prod.commercial comm
            WHERE c.status = :status
            AND p.completedAt >= :start
            AND p.completedAt < :end
            """)
    List<Copayment> findCompletedInPeriod(
            @Param("status") CopaymentStatus status,
            @Param("start") ZonedDateTime start,
            @Param("end") ZonedDateTime end);
}
