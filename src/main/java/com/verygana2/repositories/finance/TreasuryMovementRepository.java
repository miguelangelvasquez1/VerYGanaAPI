package com.verygana2.repositories.finance;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.enums.finance.MovementConcept;
import com.verygana2.models.enums.finance.TreasuryAccountCode;
import com.verygana2.models.finance.TreasuryMovement;

@Repository
public interface TreasuryMovementRepository extends JpaRepository<TreasuryMovement, UUID> {

    List<TreasuryMovement> findByReferenceIdAndReferenceType(UUID referenceId, String referenceType);

    List<TreasuryMovement> findByConcept(MovementConcept concept);

    /** Movimientos donde la cuenta fue origen O destino. Pageable controla el orden y tamaño. */
    @Query("""
            SELECT tm FROM TreasuryMovement tm
            WHERE tm.fromAccount.code = :code OR tm.toAccount.code = :code
            """)
    Page<TreasuryMovement> findByAccountCode(
            @Param("code") TreasuryAccountCode code, Pageable pageable);
}