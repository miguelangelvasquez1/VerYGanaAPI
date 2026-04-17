package com.verygana2.repositories.plans;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.plans.Investment;
import com.verygana2.models.plans.Investment.InvestmentStatus;

@Repository
public interface InvestmentRepository extends JpaRepository<Investment, Long> {

    /**
     * Devuelve la inversión activa vigente del anunciante.
     * Un anunciante solo puede tener una inversión ACTIVE a la vez.
     */
    Optional<Investment> findByCommercialIdAndStatus(
            Long commercialId, InvestmentStatus status);

    @Query("""
            SELECT ai FROM Investment ai
            WHERE ai.commercial.id = :commercialId
              AND ai.status = 'ACTIVE'
              AND ai.remainingAmount > 0
            ORDER BY ai.createdAt DESC
            LIMIT 1
            """)
    Optional<Investment> findActiveByCommercialId(@Param("commercialId") Long commercialId);
}