package com.verygana2.repositories.branding;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.verygana2.models.branding.BrandingRequest;
import com.verygana2.models.enums.BrandingRequestStatus;

public interface BrandingRequestRepository extends JpaRepository<BrandingRequest, Long> {

    List<BrandingRequest> findByCommercial_User_Id(Long userId);

    Page<BrandingRequest> findByStatus(BrandingRequestStatus status, Pageable pageable);

    Page<BrandingRequest> findAll(Pageable pageable);

    List<BrandingRequest> findByAssignedDesigner_User_Id(Long designerUserId);

    @Query("SELECT br FROM BrandingRequest br WHERE br.commercial.user.id = :userId AND br.id = :requestId")
    Optional<BrandingRequest> findByIdAndCommercialUserId(@Param("requestId") Long requestId, @Param("userId") Long userId);

    Optional<BrandingRequest> findByIdAndAssignedDesigner_User_Id(Long id, Long designerUserId);

    long countByCommercial_User_IdAndStatusNotIn(Long userId, List<BrandingRequestStatus> excludedStatuses);

    /**
     * Presupuesto ya descontado de la wallet y todavía no convertido en llaves.
     *
     * Excluye REJECTED y CANCELLED porque devuelven el monto a la wallet
     * (BrandingRequestServiceImpl.refundBudget), y CAMPAIGN_CREATED porque a partir
     * de ahí el presupuesto lo lleva la Campaign — contarlo aquí lo duplicaría.
     */
    @Query("""
            SELECT COALESCE(SUM(r.budgetCents), 0)
            FROM BrandingRequest r
            WHERE r.budgetCents IS NOT NULL
              AND r.status NOT IN (
                    com.verygana2.models.enums.BrandingRequestStatus.REJECTED,
                    com.verygana2.models.enums.BrandingRequestStatus.CANCELLED,
                    com.verygana2.models.enums.BrandingRequestStatus.CAMPAIGN_CREATED)
            """)
    long sumCommittedBudgetCents();
}
