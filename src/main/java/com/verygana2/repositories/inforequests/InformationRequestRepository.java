package com.verygana2.repositories.inforequests;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.verygana2.models.enums.inforequests.InformationRequestStatus;
import com.verygana2.models.inforequests.InformationRequest;

public interface InformationRequestRepository extends JpaRepository<InformationRequest, Long> {

    Optional<InformationRequest> findByPublicId(UUID publicId);

    Optional<InformationRequest> findByPublicIdAndCommercial_Id(UUID publicId, Long commercialId);

    Optional<InformationRequest> findByOpenDedupKey(String openDedupKey);

    boolean existsByDedupKey(String dedupKey);

    List<InformationRequest> findByCommercial_IdOrderByIssuedAtDesc(Long commercialId);

    @Query("SELECT r FROM InformationRequest r WHERE "
            + "(:status IS NULL OR r.status = :status) AND (:commercialId IS NULL OR r.commercial.id = :commercialId)")
    Page<InformationRequest> search(@Param("status") InformationRequestStatus status,
            @Param("commercialId") Long commercialId, Pageable pageable);
}
