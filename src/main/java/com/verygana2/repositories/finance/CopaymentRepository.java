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
            LEFT JOIN FETCH items.product
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
}
