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
import com.verygana2.models.Municipality;
import com.verygana2.models.ads.Ad;
import com.verygana2.models.enums.AdStatus;
import com.verygana2.models.enums.AdWatchSessionStatus;

@Repository
public interface AdRepository extends JpaRepository<Ad, Long>, JpaSpecificationExecutor<Ad> {

       // Consultas para el anunciante
       Page<Ad> findByCommercialId(Long commercialId, Pageable pageable);

       Optional<Ad> findByIdAndCommercialId(Long id, Long commercialId);

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
       @Query("SELECT COUNT(a) FROM Ad a WHERE a.commercial.id = :commercialId")
       Long countByCommercialId(@Param("commercialId") Long commercialId);

       @Query("SELECT COUNT(a) FROM Ad a WHERE a.commercial.id = :commercialId AND a.status = :status")
       long countByCommercialIdAndStatus(
                     @Param("commercialId") Long commercialId,
                     @Param("status") AdStatus status);

       // @Query("SELECT SUM(a.spentBudget) FROM Ad a WHERE a.commercial.id = :commercialId")
       // BigDecimal sumSpentBudgetByCommercialId(@Param("commercialId") Long commercialId);

       @Query("SELECT SUM(a.currentLikes) FROM Ad a WHERE a.commercial.id = :commercialId")
       Long sumLikesByCommercialId(@Param("commercialId") Long commercialId);

       // Anuncios pendientes de aprobación
       @Query("SELECT a FROM Ad a WHERE a.status = 'PENDING' ORDER BY a.createdAt ASC")
       Page<Ad> findPendingApproval(Pageable pageable);

       // Búsqueda por texto
       @Query("SELECT a FROM Ad a WHERE a.commercial.id = :commercialId " +
                     "AND (LOWER(a.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
                     "OR LOWER(a.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
       Page<Ad> searchByCommercial(
                     @Param("commercialId") Long commercialId,
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
       @Query("SELECT a FROM Ad a WHERE a.commercial.id = :commercialId " +
                     "AND a.createdAt BETWEEN :startDate AND :endDate")
       List<Ad> findByCommercialIdAndDateRange(
                     @Param("commercialId") Long commercialId,
                     @Param("startDate") ZonedDateTime startDate,
                     @Param("endDate") ZonedDateTime endDate);

       // Verificar si existe un anuncio activo con el mismo título para un commercial
       boolean existsByCommercialIdAndTitle(Long commercialId, String title);

       // ------------------------ Consultas para usuarios consumer --------------------------

       /**
        * Retorna anuncios candidatos que pasan TODOS los hard filters.
        * La selección final del mejor candidato se realiza en {@code AdScorer} mediante scoring ponderado.
        *
        * <p>Hard filters aplicados:
        * <ul>
        *   <li>status = ACTIVE, currentLikes &lt; maxLikes, dentro del rango de fechas</li>
        *   <li>Municipio: si el anuncio tiene municipios objetivo, el consumidor debe pertenecer a uno</li>
        *   <li>El usuario no ha dado like previamente (AdLike ni sesión LIKED)</li>
        *   <li>Límite diario: si maxLikesPerUserPerDay está definido, no puede haberlo superado hoy</li>
        *   <li>Cooldown: el usuario no ha visto este anuncio dentro de la ventana de cooldown</li>
        * </ul>
        */
       @Query("""
              SELECT a FROM Ad a
              WHERE a.status = :status
              AND a.currentLikes < a.maxLikes
              AND (a.startDate IS NULL OR a.startDate <= :now)
              AND (a.endDate IS NULL OR a.endDate > :now)

              AND (:municipality IS NULL
                   OR a.targetMunicipalities IS EMPTY
                   OR :municipality MEMBER OF a.targetMunicipalities)

              AND NOT EXISTS (
                     SELECT 1 FROM AdLike al
                     WHERE al.ad.id = a.id
                     AND al.consumer.id = :consumerId
              )

              AND NOT EXISTS (
                     SELECT 1 FROM AdWatchSession s
                     WHERE s.ad.id = a.id
                     AND s.consumer.id = :consumerId
                     AND s.status IN :blockedStatuses
              )

              AND NOT EXISTS (
                     SELECT 1 FROM AdWatchSession sa
                     WHERE sa.ad.id = a.id
                     AND sa.consumer.id = :consumerId
                     AND sa.status = :activeStatus
                     AND sa.expiresAt > :now
              )

              AND (a.maxLikesPerUserPerDay IS NULL OR (
                     SELECT COUNT(sd) FROM AdWatchSession sd
                     WHERE sd.ad.id = a.id
                     AND sd.consumer.id = :consumerId
                     AND sd.status = :likedStatus
                     AND sd.startedAt >= :todayStart
              ) < a.maxLikesPerUserPerDay)

              AND NOT EXISTS (
                     SELECT 1 FROM AdWatchSession sc
                     WHERE sc.ad.id = a.id
                     AND sc.consumer.id = :consumerId
                     AND sc.startedAt >= :cooldownThreshold
              )
       """)
       List<Ad> findEligibleAdsForConsumer(
              @Param("consumerId") Long consumerId,
              @Param("status") AdStatus status,
              @Param("blockedStatuses") Collection<AdWatchSessionStatus> blockedStatuses,
              @Param("likedStatus") AdWatchSessionStatus likedStatus,
              @Param("now") ZonedDateTime now,
              @Param("municipality") Municipality municipality,
              @Param("todayStart") ZonedDateTime todayStart,
              @Param("cooldownThreshold") ZonedDateTime cooldownThreshold,
              @Param("activeStatus") AdWatchSessionStatus activeStatus,
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
              AND al.consumer.id = :consumerId
              )
       """)
       long countAvailableAdsForUser(
              @Param("consumerId") Long consumerId,
              @Param("status") AdStatus status,
              @Param("now") ZonedDateTime now
       );
}
