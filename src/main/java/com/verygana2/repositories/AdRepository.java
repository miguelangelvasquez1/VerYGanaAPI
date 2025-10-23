package com.verygana2.repositories;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.Category;
import com.verygana2.models.ads.Ad;
import com.verygana2.models.enums.AdStatus;

@Repository
public interface AdRepository extends JpaRepository<Ad, Long> {

       // Consultas para el anunciante
       Page<Ad> findByAdvertiserId(Long advertiserId, Pageable pageable);

       Optional<Ad> findByIdAndAdvertiserId(Long id, Long advertiserId);

       List<Ad> findByStatus(AdStatus status);

       Page<Ad> findByStatus(AdStatus status, Pageable pageable);

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

       // Consultas para usuarios consumer

       /**
        * Encuentra anuncios disponibles para un usuario específico.
        * 
        * Un anuncio es elegible si:
        * - Está en estado ACTIVE
        * - No ha alcanzado el máximo de likes
        * - No ha expirado (o no tiene fecha de expiración)
        * - Tiene presupuesto restante
        * - El usuario no lo ha visto antes (no existe un AdLike)
        * - Las categorías del anuncio coinciden con las preferencias del usuario
        * 
        * @param userId ID del usuario
        * @param now Fecha y hora actual
        * @param pageable Parámetros de paginación
        * @return Página de anuncios disponibles
        */
       @Query("""
              SELECT DISTINCT a FROM Ad a
              LEFT JOIN FETCH a.categories
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
                     SELECT 1 FROM ConsumerDetails cd
                     JOIN cd.categories pref
                     JOIN cd.user u
                     WHERE u.id = :userId
                     AND pref IN (SELECT cat FROM a.categories cat)
              )
              ORDER BY a.createdAt DESC
       """)
       Page<Ad> findAvailableAdsForUser(
              @Param("userId") Long userId,
              @Param("status") AdStatus status,
              @Param("now") ZonedDateTime now,
              Pageable pageable
       );

       /**
        * Versión sin filtro de categorías para casos donde el usuario no tiene preferencias
        */
       @Query("""
              SELECT DISTINCT a FROM Ad a
              LEFT JOIN FETCH a.categories
              WHERE a.status = :status
              AND a.currentLikes < a.maxLikes
              AND (a.startDate IS NULL OR a.startDate <= :now)
              AND (a.endDate IS NULL OR a.endDate > :now)
              AND NOT EXISTS (
              SELECT 1 FROM AdLike al 
              WHERE al.ad.id = a.id 
              AND al.user.id = :userId
              )
              ORDER BY a.createdAt DESC
       """)
       Page<Ad> findAvailableAdsForUserWithoutCategoryFilter(
              @Param("userId") Long userId,
              @Param("status") AdStatus status,
              @Param("now") ZonedDateTime now,
              Pageable pageable
       );

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
