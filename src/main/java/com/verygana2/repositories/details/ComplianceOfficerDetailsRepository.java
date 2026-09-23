package com.verygana2.repositories.details;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.enums.AccountStatus;
import com.verygana2.models.userDetails.ComplianceOfficerDetails;

@Repository
public interface ComplianceOfficerDetailsRepository extends JpaRepository<ComplianceOfficerDetails, Long> {

    @Query("""
            SELECT c FROM ComplianceOfficerDetails c
            WHERE (:accountStatus IS NULL OR c.user.accountStatus = :accountStatus)
            AND (:search IS NULL OR :search = ''
            OR LOWER(c.user.email) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(c.user.phoneNumber) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(c.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(c.badgeNumber) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<ComplianceOfficerDetails> findComplianceOfficers (@Param("search") String search, @Param("accountStatus") AccountStatus accountStatus, Pageable pageable);

    @Query("""
            SELECT c FROM ComplianceOfficerDetails c
            WHERE c.user.publicId = :publicId
            """)
    Optional<ComplianceOfficerDetails> findByPublicId (@Param("publicId") UUID publicId);
}