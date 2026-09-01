package com.verygana2.repositories.commercial;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.verygana2.models.commercial.PlanChangeRequest;
import com.verygana2.models.enums.finance.plans.PlanChangeRequestStatus;

public interface PlanChangeRequestRepository extends JpaRepository<PlanChangeRequest, Long> {
    List<PlanChangeRequest> findByCommercial_IdAndStatusNotIn(Long commercialId, List<PlanChangeRequestStatus> statuses);
    List<PlanChangeRequest> findByStatusNotIn(List<PlanChangeRequestStatus> statuses);
    Optional<PlanChangeRequest> findByContract_Id(Long contractId);

    /** El rechazo más reciente que el comercial todavía no ha dado por leído. */
    Optional<PlanChangeRequest> findFirstByCommercial_IdAndStatusAndRejectionAcknowledgedAtIsNullOrderByRequestedAtDesc(
            Long commercialId, PlanChangeRequestStatus status);
}
