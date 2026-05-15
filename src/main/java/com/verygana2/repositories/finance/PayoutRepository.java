package com.verygana2.repositories.finance;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.finance.Payout;

@Repository
public interface PayoutRepository extends JpaRepository<Payout, UUID> {

    @Query("""
            SELECT SUM(p.netAmountCents)
            FROM Payout p
            WHERE p.commercial.id = :commercialId
            AND p.paidAt >= :startDate
            AND p.paidAt < :endDate
            """)
    BigDecimal sumTotalByCommercialIdAndPeriod(@Param("commercialId") Long commercialId,
            @Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);
}
