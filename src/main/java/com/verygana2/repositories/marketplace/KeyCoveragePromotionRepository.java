package com.verygana2.repositories.marketplace;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.marketplace.promotions.KeyCoveragePromotion;

import jakarta.persistence.LockModeType;

@Repository
public interface KeyCoveragePromotionRepository extends JpaRepository<KeyCoveragePromotion, Long> {

    /** Toda ruta que cambie estado, presupuesto o garantía parte de aquí. Se toma antes que tesorería. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM KeyCoveragePromotion p WHERE p.id = :id")
    Optional<KeyCoveragePromotion> findByIdForUpdate(@Param("id") Long id);

    /** Varias a la vez y en orden de id, para que dos compras no se bloqueen cruzadas. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM KeyCoveragePromotion p WHERE p.id IN :ids ORDER BY p.id ASC")
    List<KeyCoveragePromotion> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    /** Vigencia terminada pero todavía sin cerrar. */
    @Query("""
            SELECT p.id FROM KeyCoveragePromotion p
            WHERE p.endsAt <= :now
              AND p.status IN (com.verygana2.models.enums.marketplace.KeyCoveragePromotionStatus.AUTHORIZED,
                               com.verygana2.models.enums.marketplace.KeyCoveragePromotionStatus.PAUSED)
            """)
    List<Long> findIdsPastEndNotEnded(@Param("now") ZonedDateTime now);

    /**
     * Cerradas cuyo fondo todavía no está saldado. Incluye las que tienen compras con pago
     * en curso: lo no comprometido se devuelve ya y el resto cuando esos pagos se resuelvan.
     */
    @Query("""
            SELECT p.id FROM KeyCoveragePromotion p
            WHERE p.status = com.verygana2.models.enums.marketplace.KeyCoveragePromotionStatus.ENDED
              AND p.authorizedAt IS NOT NULL
              AND p.guaranteeReturnedAt IS NULL
            """)
    List<Long> findIdsEndedWithGuaranteeToReturn();
}
