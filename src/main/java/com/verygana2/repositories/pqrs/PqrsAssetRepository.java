package com.verygana2.repositories.pqrs;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.pqrs.PqrsAsset;

@Repository
public interface PqrsAssetRepository extends JpaRepository<PqrsAsset, Long> {

    List<PqrsAsset> findAllByIdIn(List<Long> ids);

    /**
     * Candidatos a limpieza: PENDING/ORPHANED vencidos (el cliente nunca subió
     * o nunca confirmó el archivo), y también VALIDATED-sin-reclamar vencidos
     * (el usuario subió evidencia pero nunca llegó a radicar el PQRS) — este
     * último caso es propio del flujo "subir antes de que exista el padre" y
     * no lo cubre el patrón de limpieza más simple de otros assets del proyecto.
     */
    @Query("""
            SELECT a FROM PqrsAsset a
            WHERE a.createdAt < :threshold
            AND (a.status = com.verygana2.models.enums.pqrs.PqrsAssetStatus.ORPHANED
                 OR a.status = com.verygana2.models.enums.pqrs.PqrsAssetStatus.PENDING
                 OR (a.status = com.verygana2.models.enums.pqrs.PqrsAssetStatus.VALIDATED AND a.pqrs IS NULL))
            """)
    List<PqrsAsset> findDeletableAssets(@Param("threshold") ZonedDateTime threshold);
}
