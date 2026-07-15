package com.verygana2.repositories.finance.plans;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.finance.Wallet;
import com.verygana2.models.finance.plans.Investment;

@Repository
public interface InvestmentRepository extends JpaRepository<Investment, Long> {

    Optional<Investment> findByWompiReference(String wompiReference);

    List<Investment> findByWalletAndConfirmedTrue(Wallet wallet);

    /**
     * Historial de inversiones filtrado por rango de fechas para el panel de facturación.
     */
    @Query("SELECT i FROM Investment i WHERE i.wallet.id = :walletId " +
           "AND i.createdAt >= :from AND i.createdAt < :to " +
           "ORDER BY i.createdAt DESC")
    List<Investment> findByWalletIdAndPeriod(
            @Param("walletId") Long walletId,
            @Param("from") ZonedDateTime from,
            @Param("to") ZonedDateTime to);

    /**
     * Suma de inversiones confirmadas en un período — para el card de "gastado este mes"
     * en sentido inverso (total recargado).
     */
    @Query("SELECT COALESCE(SUM(i.depositAmountCents), 0) FROM Investment i " +
           "WHERE i.wallet.id = :walletId AND i.confirmed = true " +
           "AND i.createdAt >= :from AND i.createdAt < :to")
    BigDecimal sumConfirmedByWalletIdAndPeriod(
            @Param("walletId") Long walletId,
            @Param("from") ZonedDateTime from,
            @Param("to") ZonedDateTime to);
}