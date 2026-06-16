package com.verygana2.repositories.branding;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.verygana2.models.branding.CorporateResource;
import com.verygana2.models.enums.AssetStatus;

public interface CorporateResourceRepository extends JpaRepository<CorporateResource, Long> {

    List<CorporateResource> findByBrandingRequest_IdAndStatus(Long brandingRequestId, AssetStatus status);

    Optional<CorporateResource> findByIdAndBrandingRequest_Id(Long resourceId, Long brandingRequestId);
}
