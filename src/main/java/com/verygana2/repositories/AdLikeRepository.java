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
           AND al.user.id = :userId
    """)
    boolean hasUserSeenAd(
           @Param("userId") Long userId,
           @Param("adId") Long adId
    );

    // Buscar likes por usuario
    List<AdLike> findByUserId(Long userId);
    
    Page<AdLike> findByUserId(Long userId, Pageable pageable);
    
    // Buscar likes por anuncio
    List<AdLike> findByAdId(Long adId);
    
    Page<AdLike> findByAdId(Long adId, Pageable pageable);
    
    // Verificar si existe un like
    boolean existsByUserIdAndAdId(Long userId, Long adId);
    
    Optional<AdLike> findByUserIdAndAdId(Long userId, Long adId);
    
    // Contar likes por anuncio
    @Query("SELECT COUNT(al) FROM AdLike al WHERE al.ad.id = :adId")
    Long countByAdId(@Param("adId") Long adId);
    
    // Contar likes por usuario
    @Query("SELECT COUNT(al) FROM AdLike al WHERE al.user.id = :userId")
    Long countByUserId(@Param("userId") Long userId);
    
    // Obtener likes por rango de fechas
    @Query("SELECT al FROM AdLike al WHERE al.user.id = :userId " +
           "AND al.createdAt BETWEEN :startDate AND :endDate")
    List<AdLike> findByUserIdAndDateRange(
        @Param("userId") Long userId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    // Obtener los últimos likes de un usuario
    @Query("SELECT al FROM AdLike al WHERE al.user.id = :userId " +
           "ORDER BY al.createdAt DESC")
    Page<AdLike> findRecentLikesByUser(@Param("userId") Long userId, Pageable pageable);
    
    // Estadísticas de likes por día
    @Query("SELECT DATE(al.createdAt), COUNT(al) FROM AdLike al " +
           "WHERE al.ad.id = :adId " +
           "GROUP BY DATE(al.createdAt) " +
           "ORDER BY DATE(al.createdAt) DESC")
    List<Object[]> getLikesByDay(@Param("adId") Long adId);
    
    // Total ganado por usuario
    @Query("SELECT SUM(al.rewardAmount) FROM AdLike al WHERE al.user.id = :userId")
    java.math.BigDecimal sumRewardsByUserId(@Param("userId") Long userId);
}