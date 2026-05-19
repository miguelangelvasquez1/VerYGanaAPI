package com.verygana2.repositories.finance;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.enums.finance.MovementConcept;
import com.verygana2.models.finance.TreasuryMovement;

@Repository
public interface TreasuryMovementRepository extends JpaRepository<TreasuryMovement, UUID> {

    List<TreasuryMovement> findByReferenceIdAndReferenceType(UUID referenceId, String referenceType);

    List<TreasuryMovement> findByConcept(MovementConcept concept);
}