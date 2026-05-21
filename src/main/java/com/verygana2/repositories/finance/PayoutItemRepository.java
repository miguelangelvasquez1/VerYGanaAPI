package com.verygana2.repositories.finance;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.finance.PayoutItem;

@Repository
public interface PayoutItemRepository extends JpaRepository<PayoutItem, UUID> {

    /**
     * Verifica si ya existe un PayoutItem para la combinación (copayment, commercial).
     * Usado por el PayoutScheduler para evitar duplicar pagos en reintento.
     */
    @Query("""
            SELECT COUNT(pi) > 0 FROM PayoutItem pi
            WHERE pi.copayment.id = :copaymentId
            AND pi.payout.commercial.id = :commercialId
            """)
    boolean existsByCopaymentAndCommercial(
            @Param("copaymentId") UUID copaymentId,
            @Param("commercialId") Long commercialId);
}
