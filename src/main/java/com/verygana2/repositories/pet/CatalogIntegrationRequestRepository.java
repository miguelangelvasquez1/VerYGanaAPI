package com.verygana2.repositories.pet;

import com.verygana2.models.enums.CatalogRequestStatus;
import com.verygana2.models.pets.CatalogIntegrationRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CatalogIntegrationRequestRepository extends JpaRepository<CatalogIntegrationRequest, Long> {
    List<CatalogIntegrationRequest> findByCommercial_IdOrderByCreatedAtDesc(Long commercialId);
    List<CatalogIntegrationRequest> findByStatusOrderByCreatedAtAsc(CatalogRequestStatus status);
    List<CatalogIntegrationRequest> findAllByOrderByCreatedAtDesc();
}