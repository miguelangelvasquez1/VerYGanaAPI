package com.verygana2.repositories.details;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.enums.UserState;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.userDetails.CommercialDetails;

@Repository
public interface CommercialDetailsRepository extends JpaRepository<CommercialDetails, Long>{
    Optional<CommercialDetails> findByCompanyName(String companyName);
    Optional<CommercialDetails> findByUser_Id(Long userId);
    boolean existsByUser_Id(Long userId);
    boolean existsByNit(String nit);
    boolean existsByMercantileRegistration(String mercantileRegistration);
    @Query("""
            SELECT c FROM CommercialDetails c
            WHERE (:userState IS NULL OR c.user.userState = :userState)
            AND (:currentPlan IS NULL OR c.currentPlan.code = :currentPlan)
            AND (:search IS NULL OR :search = ''
            OR LOWER(c.user.email) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(c.user.phoneNumber) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(c.companyName) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(c.nit) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(c.mercantileRegistration) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(c.legalRepDocNumber) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(c.departmentName) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(c.municipalityName) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<CommercialDetails> findCommercials (@Param("search") String search, @Param("userState") UserState userState,
    @Param("currentPlan") PlanCode currentPlan, Pageable pageable);

    @Query("""
                     SELECT c FROM CommercialDetails c
                     WHERE c.user.publicId = :publicId
                            """)
    Optional<CommercialDetails> findByPublicId (UUID publicId);
}
