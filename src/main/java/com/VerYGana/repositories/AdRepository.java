package com.VerYGana.repositories;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.VerYGana.models.ads.Ad;
import com.VerYGana.models.enums.AdStatus;
import com.VerYGana.models.enums.Preference;

@Repository
public interface AdRepository extends JpaRepository<Ad, Long> {
    
    // Consultas básicas
    List<Ad> findByAdvertiserId(Long advertiserId);
    
    Page<Ad> findByAdvertiserId(Long advertiserId, Pageable pageable);
    
    Optional<Ad> findByIdAndAdvertiserId(Long id, Long advertiserId);
    
    // Consultas por estado
    List<Ad> findByStatus(AdStatus status);
    
    Page<Ad> findByStatus(AdStatus status, Pageable pageable);
    
    List<Ad> findByIsActiveTrue();
    
    // Anuncios disponibles para mostrar a usuarios
    @Query("SELECT a FROM Ad a WHERE a.isActive = true " +
           "AND a.status = 'APPROVED' " +
           "AND a.currentLikes < a.maxLikes " +
           "AND (a.endDate IS NULL OR a.endDate > :now) " +
           "AND a.spentBudget + a.rewardPerLike <= a.totalBudget")
    List<Ad> findAvailableAds(@Param("now") LocalDateTime now);
    
    @Query("SELECT a FROM Ad a WHERE a.isActive = true " +
           "AND a.status = 'APPROVED' " +
           "AND a.currentLikes < a.maxLikes " +
           "AND (a.endDate IS NULL OR a.endDate > :now) " +
           "AND a.spentBudget + a.rewardPerLike <= a.totalBudget")
    Page<Ad> findAvailableAds(@Param("now") LocalDateTime now, Pageable pageable);
    
    // Anuncios disponibles por categoría
    @Query("SELECT a FROM Ad a WHERE a.isActive = true " +
           "AND a.status = 'APPROVED' " +
           "AND a.currentLikes < a.maxLikes " +
           "AND a.category = :category " +
           "AND (a.endDate IS NULL OR a.endDate > :now) " +
           "AND a.spentBudget + a.rewardPerLike <= a.totalBudget")
    Page<Ad> findAvailableAdsByCategory(
        @Param("category") Preference category,
        @Param("now") LocalDateTime now,
        Pageable pageable
    );
    
    // Anuncios que el usuario NO ha visto
    @Query("SELECT a FROM Ad a WHERE a.isActive = true " +
           "AND a.status = 'APPROVED' " +
           "AND a.currentLikes < a.maxLikes " +
           "AND (a.endDate IS NULL OR a.endDate > :now) " +
           "AND a.spentBudget + a.rewardPerLike <= a.totalBudget " +
           "AND NOT EXISTS (SELECT 1 FROM AdLike al WHERE al.ad.id = a.id AND al.user.id = :userId)")
    Page<Ad> findAvailableAdsNotSeenByUser(
        @Param("userId") Long userId,
        @Param("now") LocalDateTime now,
        Pageable pageable
    );
    
    // Consultas de estadísticas
    @Query("SELECT COUNT(a) FROM Ad a WHERE a.advertiser.id = :advertiserId")
    Long countByAdvertiserId(@Param("advertiserId") Long advertiserId);
    
    @Query("SELECT COUNT(a) FROM Ad a WHERE a.advertiser.id = :advertiserId AND a.status = :status")
    Long countByAdvertiserIdAndStatus(
        @Param("advertiserId") Long advertiserId,
        @Param("status") AdStatus status
    );
    
    @Query("SELECT SUM(a.spentBudget) FROM Ad a WHERE a.advertiser.id = :advertiserId")
    BigDecimal sumSpentBudgetByAdvertiserId(@Param("advertiserId") Long advertiserId);
    
    @Query("SELECT SUM(a.currentLikes) FROM Ad a WHERE a.advertiser.id = :advertiserId")
    Long sumLikesByAdvertiserId(@Param("advertiserId") Long advertiserId);
    
    // Anuncios que necesitan ser desactivados automáticamente
    @Query("SELECT a FROM Ad a WHERE a.isActive = true " +
           "AND (a.currentLikes >= a.maxLikes " +
           "OR a.spentBudget + a.rewardPerLike > a.totalBudget " +
           "OR (a.endDate IS NOT NULL AND a.endDate < :now))")
    List<Ad> findAdsToAutoDeactivate(@Param("now") LocalDateTime now);
    
    // Anuncios pendientes de aprobación
    @Query("SELECT a FROM Ad a WHERE a.status = 'PENDING' ORDER BY a.createdAt ASC")
    Page<Ad> findPendingApproval(Pageable pageable);
    
    // Búsqueda por texto
    @Query("SELECT a FROM Ad a WHERE a.advertiser.id = :advertiserId " +
           "AND (LOWER(a.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(a.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<Ad> searchByAdvertiser(
        @Param("advertiserId") Long advertiserId,
        @Param("searchTerm") String searchTerm,
        Pageable pageable
    );
    
    // Actualización masiva de estado
    @Modifying
    @Query("UPDATE Ad a SET a.isActive = false, a.status = 'COMPLETED', a.updatedAt = :now " +
           "WHERE a.id IN :ids")
    int deactivateAds(@Param("ids") List<Long> ids, @Param("now") LocalDateTime now);
    
    // Verificar si un usuario ya dio like a un anuncio
    @Query("SELECT CASE WHEN COUNT(al) > 0 THEN true ELSE false END " +
           "FROM AdLike al WHERE al.ad.id = :adId AND al.user.id = :userId")
    boolean hasUserLikedAd(@Param("adId") Long adId, @Param("userId") Long userId);
    
    // Top anuncios por engagement
    @Query("SELECT a FROM Ad a WHERE a.status = 'APPROVED' " +
           "ORDER BY a.currentLikes DESC")
    Page<Ad> findTopAdsByLikes(Pageable pageable);
    
    // Anuncios por rango de fechas
    @Query("SELECT a FROM Ad a WHERE a.advertiser.id = :advertiserId " +
           "AND a.createdAt BETWEEN :startDate AND :endDate")
    List<Ad> findByAdvertiserIdAndDateRange(
        @Param("advertiserId") Long advertiserId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    // Verificar si existe un anuncio activo con el mismo título para un advertiser
    boolean existsByAdvertiserIdAndTitleAndIsActiveTrue(Long advertiserId, String title);

}
