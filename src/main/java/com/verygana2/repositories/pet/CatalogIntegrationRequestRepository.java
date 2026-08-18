package com.verygana2.repositories.pet;

import com.verygana2.models.enums.CatalogRequestStatus;
import com.verygana2.models.pets.CatalogIntegrationRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CatalogIntegrationRequestRepository extends JpaRepository<CatalogIntegrationRequest, Long> {
    List<CatalogIntegrationRequest> findByCommercial_IdOrderByCreatedAtDesc(Long commercialId);
    List<CatalogIntegrationRequest> findByStatusOrderByCreatedAtAsc(CatalogRequestStatus status);
    List<CatalogIntegrationRequest> findAllByOrderByCreatedAtDesc();

    // Bandeja del diseñador: solo lo que el admin le asignó (igual que BrandingRequest).
    List<CatalogIntegrationRequest> findByAssignedDesigner_User_IdOrderByCreatedAtDesc(Long designerUserId);
    Optional<CatalogIntegrationRequest> findByIdAndAssignedDesigner_User_Id(Long id, Long designerUserId);
}