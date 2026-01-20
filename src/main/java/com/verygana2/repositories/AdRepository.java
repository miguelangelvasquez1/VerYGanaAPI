package com.verygana2.repositories;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.Category;
import com.verygana2.models.ads.Ad;
import com.verygana2.models.enums.AdStatus;
import com.verygana2.models.enums.AdWatchSessionStatus;

@Repository
public interface AdRepository extends JpaRepository<Ad, Long>, JpaSpecificationExecutor<Ad> {

       // Consultas para el anunciante
       Page<Ad> findByAdvertiserId(Long advertiserId, Pageable pageable);

       Optional<Ad> findByIdAndAdvertiserId(Long id, Long advertiserId);

       List<Ad> findByStatus(AdStatus status);

       Page<Ad> findByStatus(AdStatus status, Pageable pageable);

       @Query("SELECT a FROM Ad a WHERE (:status IS NULL OR a.status = :status)")
       Page<Ad> findAllByStatus(@Param("status") AdStatus status, Pageable pageable);

       // Anuncios disponibles por categoría
       @Query("SELECT DISTINCT a FROM Ad a JOIN a.categories c WHERE " +
                     "a.status = 'APPROVED' " +
                     "AND a.currentLikes < a.maxLikes " +
                     "AND c IN :categories " +
                     "AND (a.endDate IS NULL OR a.endDate > :now) ")
       Page<Ad> findAvailableAdsByCategories(
                     @Param("categories") List<Category> categories,
                     @Param("now") ZonedDateTime now,
                     Pageable pageable);

       // Consultas de estadísticas
       @Query("SELECT COUNT(a) FROM Ad a WHERE a.advertiser.id = :advertiserId")
       Long countByAdvertiserId(@Param("advertiserId") Long advertiserId);

       @Query("SELECT COUNT(a) FROM Ad a WHERE a.advertiser.id = :advertiserId AND a.status = :status")
       Long countByAdvertiserIdAndStatus(
                     @Param("advertiserId") Long advertiserId,
                     @Param("status") AdStatus status);

       // @Query("SELECT SUM(a.spentBudget) FROM Ad a WHERE a.advertiser.id = :advertiserId")
       // BigDecimal sumSpentBudgetByAdvertiserId(@Param("advertiserId") Long advertiserId);

       @Query("SELECT SUM(a.currentLikes) FROM Ad a WHERE a.advertiser.id = :advertiserId")
       Long sumLikesByAdvertiserId(@Param("advertiserId") Long advertiserId);

       // Anuncios que necesitan ser desactivados automáticamente
       @Query("SELECT a FROM Ad a WHERE " +
                     "(a.currentLikes >= a.maxLikes " +
                     "OR (a.endDate IS NOT NULL AND a.endDate < :now))")
       List<Ad> findAdsToAutoDeactivate(@Param("now") ZonedDateTime now);

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
                     Pageable pageable);

       // Actualización masiva de estado
       @Modifying
       @Query("UPDATE Ad a SET a.status = 'COMPLETED', a.updatedAt = :now " +
                     "WHERE a.id IN :ids")
       int deactivateAds(@Param("ids") List<Long> ids, @Param("now") ZonedDateTime now);

       // Top anuncios por engagement
       @Query("SELECT a FROM Ad a WHERE a.status = 'APPROVED' " +
                     "ORDER BY a.currentLikes DESC")
       Page<Ad> findTopAdsByLikes(Pageable pageable);

       // Anuncios por rango de fechas
       @Query("SELECT a FROM Ad a WHERE a.advertiser.id = :advertiserId " +
                     "AND a.createdAt BETWEEN :startDate AND :endDate")
       List<Ad> findByAdvertiserIdAndDateRange(
                     @Param("advertiserId") Long advertiserId,
                     @Param("startDate") ZonedDateTime startDate,
                     @Param("endDate") ZonedDateTime endDate);

       // Verificar si existe un anuncio activo con el mismo título para un advertiser
       boolean existsByAdvertiserIdAndTitle(Long advertiserId, String title);

       // ------------------------ Consultas para usuarios consumer --------------------------

       @Query("""
              SELECT a FROM Ad a
              WHERE a.status = :status
              AND a.currentLikes < a.maxLikes
              AND (a.startDate IS NULL OR a.startDate <= :now)
              AND (a.endDate IS NULL OR a.endDate > :now)

              AND NOT EXISTS (
                     SELECT 1 FROM AdLike al
                     WHERE al.ad.id = a.id
                     AND al.user.id = :userId
              )

              AND EXISTS (
                     SELECT 1
                     FROM ConsumerDetails cd
                     JOIN cd.categories c
                     WHERE cd.user.id = :userId
                     AND c MEMBER OF a.categories
              )

              AND NOT EXISTS (
                     SELECT 1 FROM AdWatchSession s
                     WHERE s.ad.id = a.id
                     AND s.user.id = :userId
                     AND s.status IN :blockedStatuses
              )

              ORDER BY a.createdAt DESC
       """)
       List<Ad> findFirstAvailableAdForUser(
              @Param("userId") Long userId,
              @Param("status") AdStatus status,
              @Param("blockedStatuses") Collection<AdWatchSessionStatus> blockedStatuses,
              @Param("now") ZonedDateTime now,
              Pageable pageable
       );
       
       @Query("""
              SELECT a FROM Ad a
              WHERE a.status = :status
              AND a.currentLikes < a.maxLikes
              AND (a.startDate IS NULL OR a.startDate <= :now)
              AND (a.endDate IS NULL OR a.endDate > :now)

              AND NOT EXISTS (
                     SELECT 1 FROM AdLike al
                     WHERE al.ad.id = a.id
                     AND al.user.id = :userId
              )

              AND NOT EXISTS (
                     SELECT 1 FROM AdWatchSession s
                     WHERE s.ad.id = a.id
                     AND s.user.id = :userId
                     AND s.status IN :blockedStatuses
              )

              ORDER BY a.createdAt DESC
       """)
       List<Ad> findNextAdWithoutCategoryMatch(
              @Param("userId") Long userId,
              @Param("status") AdStatus status,
              @Param("blockedStatuses") Collection<AdWatchSessionStatus> blockedStatuses,
              @Param("now") ZonedDateTime now,
              Pageable pageable
       );

       /**
        * Cuenta los anuncios disponibles para un usuario
        */
       @Query("""
              SELECT COUNT(DISTINCT a) FROM Ad a
              WHERE a.status = :status
              AND a.currentLikes < a.maxLikes
              AND (a.startDate IS NULL OR a.startDate <= :now)
              AND (a.endDate IS NULL OR a.endDate > :now)
              AND NOT EXISTS (
              SELECT 1 FROM AdLike al 
              WHERE al.ad.id = a.id 
              AND al.user.id = :userId
              )
       """)
       long countAvailableAdsForUser(
              @Param("userId") Long userId,
              @Param("status") AdStatus status,
              @Param("now") ZonedDateTime now
       );
}
