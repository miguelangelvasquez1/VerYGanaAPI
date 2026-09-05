package com.verygana2.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.ads.AdLike;
import com.verygana2.models.ads.AdLikeId;

@Repository
public interface AdLikeRepository extends JpaRepository<AdLike, AdLikeId> {

    /**
    * Verifica si un usuario ya ha visto un anuncio específico
    */
    @Query("""
         SELECT CASE WHEN COUNT(al) > 0 THEN true ELSE false END
           FROM AdLike al
           WHERE al.ad.id = :adId
           AND al.consumer.id = :consumerId
    """)
    boolean hasUserSeenAd(
           @Param("consumerId") Long consumerId,
           @Param("adId") Long adId
    );

    // Buscar likes por usuario
    List<AdLike> findByConsumerId(Long consumerId);
    
    Page<AdLike> findByConsumerId(Long consumerId, Pageable pageable);
    
    // Buscar likes por anuncio
    List<AdLike> findByAdId(Long adId);
    
    Page<AdLike> findByAdId(Long adId, Pageable pageable);
    
    @Query("""
        SELECT al FROM AdLike al
        JOIN FETCH al.consumer c
        WHERE al.ad.id = :adId
        ORDER BY al.createdAt DESC
    """)
    Page<AdLike> findByAdIdOrderByCreatedAtDesc(
        @Param("adId") Long adId,
        Pageable pageable
    );

    // Verificar si existe un like
    boolean existsByConsumerIdAndAdId(Long consumerId, Long adId);
    
    Optional<AdLike> findByConsumerIdAndAdId(Long consumerId, Long adId);
    
    // Contar likes por anuncio
    @Query("SELECT COUNT(al) FROM AdLike al WHERE al.ad.id = :adId")
    Long countByAdId(@Param("adId") Long adId);
    
    // Contar likes por usuario
    @Query("SELECT COUNT(al) FROM AdLike al WHERE al.consumer.id = :consumerId")
    Long countByConsumerId(@Param("consumerId") Long consumerId);
    
    // Obtener likes por rango de fechas
    @Query("SELECT al FROM AdLike al WHERE al.consumer.id = :consumerId " +
           "AND al.createdAt BETWEEN :startDate AND :endDate")
    List<AdLike> findByConsumerIdAndDateRange(
        @Param("consumerId") Long consumerId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    // Obtener los últimos likes de un usuario
    @Query("SELECT al FROM AdLike al WHERE al.consumer.id = :consumerId " +
           "ORDER BY al.createdAt DESC")
    Page<AdLike> findRecentLikesByUser(@Param("consumerId") Long consumerId, Pageable pageable);
    
    // Estadísticas de likes por día
    @Query("SELECT DATE(al.createdAt), COUNT(al) FROM AdLike al " +
           "WHERE al.ad.id = :adId " +
           "GROUP BY DATE(al.createdAt) " +
           "ORDER BY DATE(al.createdAt) DESC")
    List<Object[]> getLikesByDay(@Param("adId") Long adId);
    
    // Total ganado por usuario
    @Query("SELECT SUM(al.rewardAmount) FROM AdLike al WHERE al.consumer.id = :consumerId")
    java.math.BigDecimal sumRewardsByConsumerId(@Param("consumerId") Long consumerId);

    // Likes recibidos por todos los anuncios de un comercial en un rango (panel de inicio).
    @Query("""
        SELECT COUNT(al) FROM AdLike al
        WHERE al.ad.commercial.id = :commercialId
        AND al.createdAt >= :start AND al.createdAt < :end
    """)
    long countByCommercialIdAndCreatedAtRange(
        @Param("commercialId") Long commercialId,
        @Param("start") java.time.ZonedDateTime start,
        @Param("end") java.time.ZonedDateTime end
    );

    // ── Reportes de rendimiento del comercial ────────────────────────────────

    /** Suma de recompensas (centavos) pagadas por likes del comercial en el rango. */
    @Query("""
        SELECT COALESCE(SUM(al.rewardAmount), 0) FROM AdLike al
        WHERE al.ad.commercial.id = :commercialId
        AND al.createdAt >= :start AND al.createdAt < :end
    """)
    long sumRewardInRange(
        @Param("commercialId") Long commercialId,
        @Param("start") java.time.ZonedDateTime start,
        @Param("end") java.time.ZonedDateTime end
    );

    /** Consumers distintos que dieron like a algún anuncio del comercial en el rango. */
    @Query("""
        SELECT COUNT(DISTINCT al.consumer.id) FROM AdLike al
        WHERE al.ad.commercial.id = :commercialId
        AND al.createdAt >= :start AND al.createdAt < :end
    """)
    long countDistinctConsumersInRange(
        @Param("commercialId") Long commercialId,
        @Param("start") java.time.ZonedDateTime start,
        @Param("end") java.time.ZonedDateTime end
    );

    /** [Long adId, Long count] de likes por anuncio del comercial en el rango. */
    @Query("""
        SELECT al.ad.id, COUNT(al) FROM AdLike al
        WHERE al.ad.commercial.id = :commercialId
        AND al.createdAt >= :start AND al.createdAt < :end
        GROUP BY al.ad.id
    """)
    List<Object[]> countByAdInRange(
        @Param("commercialId") Long commercialId,
        @Param("start") java.time.ZonedDateTime start,
        @Param("end") java.time.ZonedDateTime end
    );

    /** [java.sql.Date day, Long count] de likes del comercial por día del rango. */
    @Query("""
        SELECT DATE(al.createdAt), COUNT(al) FROM AdLike al
        WHERE al.ad.commercial.id = :commercialId
        AND al.createdAt >= :start AND al.createdAt < :end
        GROUP BY DATE(al.createdAt)
    """)
    List<Object[]> countByDayInRange(
        @Param("commercialId") Long commercialId,
        @Param("start") java.time.ZonedDateTime start,
        @Param("end") java.time.ZonedDateTime end
    );
}