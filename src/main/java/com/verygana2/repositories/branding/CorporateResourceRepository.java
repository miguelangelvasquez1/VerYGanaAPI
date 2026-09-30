package com.verygana2.repositories.branding;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.verygana2.models.branding.CorporateResource;
import com.verygana2.models.enums.AssetStatus;

public interface CorporateResourceRepository extends JpaRepository<CorporateResource, Long> {

    List<CorporateResource> findByBrandingRequest_IdAndStatus(Long brandingRequestId, AssetStatus status);

    Optional<CorporateResource> findByIdAndBrandingRequest_Id(Long resourceId, Long brandingRequestId);

    /**
     * Recursos en el estado dado (ORPHANED o un PENDING vencido) más viejos que el umbral.
     * Un recurso nace vinculado a su solicitud de branding, así que aquí el estado es lo que
     * distingue un archivo en uso (VALIDATED) de uno abandonado.
     */
    @Query("""
        SELECT r FROM CorporateResource r
        WHERE r.status = :status
        AND r.createdAt < :threshold
    """)
    List<CorporateResource> findDeletableAssets(
        @Param("status") AssetStatus status,
        @Param("threshold") ZonedDateTime threshold
    );
}
