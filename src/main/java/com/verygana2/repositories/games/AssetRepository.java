package com.verygana2.repositories.games;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.branding.Asset;
import com.verygana2.models.enums.AssetStatus;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {

    List<Asset> findByObjectKeyIn(List<String> urls);
    
    List<Asset> findAllByIdInAndStatus(List<Long> ids, AssetStatus status);

    @Query("""
        SELECT a FROM Asset a
        WHERE a.status = :status
        AND a.createdAt < :threshold
    """)
    List<Asset> findDeletableAssets(
        @Param("status") AssetStatus status,
        @Param("threshold") ZonedDateTime threshold
    );

    List<Asset> findByBrandingRequest_Id(Long brandingRequestId);

    List<Asset> findByObjectKeyIn(Set<String> urls);

    /**
     * Assets de campaña en los estados dados y más viejos que el umbral. Se usa con PENDING:
     * el diseñador pidió la URL de subida y nunca confirmó. A diferencia de otros assets no
     * hay un padre contra el cual comprobar "sin vincular" (la relación con la solicitud de
     * branding es opcional y no indica uso), pero el flujo siempre confirma justo después de
     * subir, así que un PENDING vencido es una subida abandonada.
     */
    @Query("""
        SELECT a FROM Asset a
        WHERE a.status IN :statuses
        AND a.createdAt < :threshold
    """)
    List<Asset> findStaleAssets(
        @Param("statuses") Collection<AssetStatus> statuses,
        @Param("threshold") ZonedDateTime threshold
    );
}
