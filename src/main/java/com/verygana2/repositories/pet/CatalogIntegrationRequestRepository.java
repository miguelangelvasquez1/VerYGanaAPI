package com.verygana2.repositories.pet;

import com.verygana2.models.enums.CatalogRequestStatus;
import com.verygana2.models.pets.CatalogIntegrationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface CatalogIntegrationRequestRepository extends JpaRepository<CatalogIntegrationRequest, Long> {
    List<CatalogIntegrationRequest> findByCommercial_IdOrderByCreatedAtDesc(Long commercialId);
    List<CatalogIntegrationRequest> findByStatusOrderByCreatedAtAsc(CatalogRequestStatus status);
    List<CatalogIntegrationRequest> findAllByOrderByCreatedAtDesc();

    // Bandeja del diseñador: solo lo que el admin le asignó (igual que BrandingRequest).
    List<CatalogIntegrationRequest> findByAssignedDesigner_User_IdOrderByCreatedAtDesc(Long designerUserId);
    Optional<CatalogIntegrationRequest> findByIdAndAssignedDesigner_User_Id(Long id, Long designerUserId);

    /**
     * La solicitud que originó un ítem del catálogo, con {@code SELECT … FOR UPDATE}: las
     * compras concurrentes del mismo ítem se serializan sobre la bolsa y no la sobregiran.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM CatalogIntegrationRequest r WHERE r.resultCatalogItemId = :catalogItemId")
    Optional<CatalogIntegrationRequest> findByResultCatalogItemIdForUpdate(@Param("catalogItemId") Long catalogItemId);
}
