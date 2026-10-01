package com.verygana2.repositories.finance;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.finance.PayoutMethodCertificateAsset;

@Repository
public interface PayoutMethodCertificateAssetRepository extends JpaRepository<PayoutMethodCertificateAsset, Long> {
    Optional<PayoutMethodCertificateAsset> findByPayoutMethodId(Long payoutMethodId);

    /**
     * Certificaciones en el estado dado (ORPHANED), más viejas que el umbral y ya
     * desvinculadas de su método de pago. La condición {@code payoutMethod IS NULL} evita que
     * el barrido borre una certificación que un método de pago sigue usando.
     */
    @Query("""
        SELECT a FROM PayoutMethodCertificateAsset a
        WHERE a.status = :status
        AND a.payoutMethod IS NULL
        AND a.uploadedAt < :threshold
    """)
    List<PayoutMethodCertificateAsset> findDeletableAssets(
        @Param("status") AssetStatus status,
        @Param("threshold") ZonedDateTime threshold
    );

    /** Certificaciones subidas y nunca vinculadas a un método de pago (subida abandonada). */
    @Query("""
        SELECT a FROM PayoutMethodCertificateAsset a
        WHERE a.payoutMethod IS NULL
        AND a.status IN :statuses
        AND a.uploadedAt < :threshold
    """)
    List<PayoutMethodCertificateAsset> findStaleUnattachedAssets(
        @Param("statuses") Collection<AssetStatus> statuses,
        @Param("threshold") ZonedDateTime threshold
    );
}
