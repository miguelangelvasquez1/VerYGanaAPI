package com.verygana2.repositories.recognitions;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.enums.recognitions.RecognitionStatus;
import com.verygana2.models.recognitions.Recognition;

import jakarta.persistence.LockModeType;

@Repository
public interface RecognitionRepository extends JpaRepository<Recognition, Long> {

    Optional<Recognition> findByPublicId(UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Recognition r WHERE r.publicId = :publicId")
    Optional<Recognition> findByPublicIdForUpdate(@Param("publicId") UUID publicId);

    Optional<Recognition> findByPurchaseId(Long purchaseId);

    @Query("SELECT r FROM Recognition r WHERE r.purchase.originKey = :originKey")
    Optional<Recognition> findByOriginKey(@Param("originKey") String originKey);

    @Query("""
            SELECT r FROM Recognition r
            JOIN r.purchase p
            WHERE (:status IS NULL OR r.status = :status)
            AND (:beneficiaryId IS NULL OR p.beneficiary.id = :beneficiaryId)
            AND (:from IS NULL OR r.createdAt >= :from)
            AND (:to IS NULL OR r.createdAt < :to)
            AND (:commercialId IS NULL OR EXISTS (
                SELECT 1 FROM PurchaseItem pi WHERE pi.purchase = p AND pi.commercialId = :commercialId))
            """)
    Page<Recognition> search(@Param("status") RecognitionStatus status,
            @Param("beneficiaryId") Long beneficiaryId,
            @Param("commercialId") Long commercialId,
            @Param("from") ZonedDateTime from,
            @Param("to") ZonedDateTime to,
            Pageable pageable);

    @Query("SELECT r FROM Recognition r WHERE r.purchase.beneficiary.id = :beneficiaryId")
    Page<Recognition> findByBeneficiaryId(@Param("beneficiaryId") Long beneficiaryId, Pageable pageable);

    /**
     * Reconocimientos vigentes (no cancelados ni vencidos) por beneficiario, con la fecha del
     * último. Cada fila: {@code [beneficiaryId (Long), cantidad (Long), último (ZonedDateTime)]}.
     */
    @Query("""
            SELECT r.purchase.beneficiary.id, COUNT(r), MAX(r.createdAt)
            FROM Recognition r
            WHERE r.purchase.beneficiary.id IN :beneficiaryIds
            AND r.status IN (com.verygana2.models.enums.recognitions.RecognitionStatus.PURCHASED,
                             com.verygana2.models.enums.recognitions.RecognitionStatus.DELIVERED)
            GROUP BY r.purchase.beneficiary.id
            """)
    List<Object[]> countActiveByBeneficiary(@Param("beneficiaryIds") Collection<Long> beneficiaryIds);

    /** Total pagado por el fondo en reconocimientos vigentes. */
    @Query("""
            SELECT COALESCE(SUM(r.purchase.totalCents), 0) FROM Recognition r
            WHERE r.status IN (com.verygana2.models.enums.recognitions.RecognitionStatus.PURCHASED,
                               com.verygana2.models.enums.recognitions.RecognitionStatus.DELIVERED)
            """)
    long sumActiveSpentCents();

    long countByStatus(RecognitionStatus status);
}
