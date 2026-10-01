package com.verygana2.repositories;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.Category;
import com.verygana2.models.Municipality;
import com.verygana2.models.ads.Ad;
import com.verygana2.models.enums.AdStatus;
import com.verygana2.models.enums.AdWatchSessionStatus;

import jakarta.persistence.LockModeType;

@Repository
public interface AdRepository extends JpaRepository<Ad, Long>, JpaSpecificationExecutor<Ad> {

       // Consultas para el anunciante
       Page<Ad> findByCommercialId(Long commercialId, Pageable pageable);

       // Todos los anuncios de un comercial (acotado por MAX_ADS del plan) — para reportes.
       @Query("SELECT a FROM Ad a WHERE a.commercial.id = :commercialId")
       List<Ad> findAllByCommercialId(@Param("commercialId") Long commercialId);

       Optional<Ad> findByIdAndCommercialId(Long id, Long commercialId);

       /**
        * Igual que {@link #findByIdAndCommercialId} pero con {@code SELECT … FOR UPDATE}. Lo usa el
        * aumento de presupuesto, que es un read-modify-write sobre {@code maxLikes}/{@code status}:
        * el lock serializa contra otro aumento y hace que los UPDATE atómicos de
        * {@link #incrementLikeIfAvailable} esperen al commit en vez de pisarse con el cambio.
        */
       @Lock(LockModeType.PESSIMISTIC_WRITE)
       @Query("SELECT a FROM Ad a WHERE a.id = :id AND a.commercial.id = :commercialId")
       Optional<Ad> findByIdAndCommercialIdForUpdate(@Param("id") Long id, @Param("commercialId") Long commercialId);

       List<Ad> findByStatus(AdStatus status);

       Page<Ad> findByStatus(AdStatus status, Pageable pageable);

       @Query("SELECT a FROM Ad a WHERE (:status IS NULL OR a.status = :status)")
       Page<Ad> findAllByStatus(@Param("status") AdStatus status, Pageable pageable);

       // Anuncios disponibles por categoría
       @Query("SELECT DISTINCT a FROM Ad a JOIN a.targetAudience ta JOIN ta.categories c WHERE " +
                     "a.status = 'APPROVED' " +
                     "AND a.currentLikes < a.maxLikes " +
                     "AND c IN :categories " +
                     "AND (a.endDate IS NULL OR a.endDate > :now) ")
       Page<Ad> findAvailableAdsByCategories(
                     @Param("categories") List<Category> categories,
                     @Param("now") ZonedDateTime now,
                     Pageable pageable);

       // Anuncios que aún ocupan un cupo del plan: se le pasan los estados terminales
       // (REJECTED/COMPLETED) para excluirlos. Ver PlanFeatureGuard.
       @Query("SELECT COUNT(a) FROM Ad a WHERE a.commercial.id = :commercialId AND a.status NOT IN :statuses")
       long countByCommercialIdAndStatusNotIn(
                     @Param("commercialId") Long commercialId,
                     @Param("statuses") List<AdStatus> statuses);

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

       /**
        * Incremento atómico y condicionado del contador de likes.
        *
        * <p>El {@code WHERE a.currentLikes < a.maxLikes} cierra la ventana de
        * lost-update entre dos likes concurrentes sobre el mismo anuncio: la BD
        * serializa los UPDATE sobre la misma fila, así que exactamente una
        * solicitud gana el último cupo y el resto afecta 0 filas. El llamador
        * traduce ese 0 a un error de dominio 4xx, en vez de corromper el
        * contador o depender de que un fallo de {@code @Version} se reintente y
        * se traduzca (cuando no se traducía, el perdedor recibía un 500).
        *
        * <p>Cuando el incremento alcanza el tope cierra el anuncio en la misma
        * sentencia ({@code status = COMPLETED}, {@code endDate = :now}) para no
        * dejar una segunda escritura read-modify-write con su propia carrera.
        * {@code UPDATE VERSIONED} incrementa la columna {@code @Version} para
        * que cualquier escritura de entidad concurrente sobre el mismo anuncio
        * (aprobación, pausa, agotamiento de presupuesto) siga detectando el
        * conflicto.
        *
        * <p><b>El orden del {@code SET} importa.</b> MySQL/MariaDB evalúan las
        * asignaciones de izquierda a derecha usando los valores YA actualizados
        * (PostgreSQL y H2 usan los originales). Si {@code currentLikes = currentLikes + 1}
        * va primero, los {@code CASE} siguientes ven el contador ya incrementado y
        * el anuncio pasaba a COMPLETED con un like de menos (con 9 de 10 likes ya
        * quedaba cerrado y el último no se podía registrar). Por eso {@code status} y
        * {@code endDate} van ANTES del contador: así leen el valor original en ambos
        * motores. Verificado contra MariaDB 10.4; ver
        * {@code AdRepositoryIncrementLikeQueryTest}. Al agregar asignaciones a esta
        * sentencia, toda columna derivada de {@code currentLikes} debe ir antes de él.
        *
        * @return 1 si el like se registró, 0 si el anuncio ya no admitía más likes
        */
       @Modifying(flushAutomatically = true)
       @Query("""
              UPDATE VERSIONED Ad a
                 SET a.status = CASE
                            WHEN a.currentLikes + 1 >= a.maxLikes
                            THEN com.verygana2.models.enums.AdStatus.COMPLETED
                            ELSE a.status END,
                     a.endDate = CASE
                            WHEN a.currentLikes + 1 >= a.maxLikes
                            THEN :now
                            ELSE a.endDate END,
                     a.updatedAt = :now,
                     a.currentLikes = a.currentLikes + 1
               WHERE a.id = :adId
                 AND a.status = com.verygana2.models.enums.AdStatus.ACTIVE
                 AND a.currentLikes < a.maxLikes
              """)
       int incrementLikeIfAvailable(@Param("adId") Long adId, @Param("now") ZonedDateTime now);

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
                   OR a.targetAudience IS NULL
                   OR a.targetAudience.targetMunicipalities IS EMPTY
                   OR :municipality MEMBER OF a.targetAudience.targetMunicipalities)

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

    /**
     * Presupuesto de anuncios ya descontado de la wallet pero todavía no entregado
     * como llaves: rewardPerLike × (maxLikes − currentLikes).
     *
     * Excluye REJECTED porque ese es el único estado que devuelve el remanente a la
     * wallet (AdServiceImpl.refundRemainingBudget) — ahí el dinero ya se contó en
     * sumBalanceCents. BLOCKED sí cuenta: no se reembolsa, porque un admin puede
     * reactivar el anuncio.
     */
    @Query("""
            SELECT COALESCE(SUM(a.rewardPerLike * (a.maxLikes - a.currentLikes)), 0)
            FROM Ad a
            WHERE a.status <> com.verygana2.models.enums.AdStatus.REJECTED
              AND a.rewardPerLike IS NOT NULL
              AND a.maxLikes IS NOT NULL
              AND a.currentLikes IS NOT NULL
              AND a.maxLikes > a.currentLikes
            """)
    long sumCommittedUnspentBudgetCents();
}
