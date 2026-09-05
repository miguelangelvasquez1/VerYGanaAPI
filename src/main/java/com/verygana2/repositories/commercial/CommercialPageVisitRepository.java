package com.verygana2.repositories.commercial;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.commercial.CommercialPageVisit;

@Repository
public interface CommercialPageVisitRepository extends JpaRepository<CommercialPageVisit, Long> {

    /** Deduplicación: ¿este consumer ya visitó la página desde este anuncio dentro de la ventana? */
    boolean existsByAdIdAndConsumerIdAndCreatedAtAfter(Long adId, Long consumerId, ZonedDateTime threshold);

    @Query("""
        SELECT COUNT(v) FROM CommercialPageVisit v
        WHERE v.commercial.id = :commercialId
          AND v.createdAt >= :from AND v.createdAt < :to
    """)
    long countInRange(@Param("commercialId") Long commercialId,
                      @Param("from") ZonedDateTime from,
                      @Param("to") ZonedDateTime to);

    @Query("""
        SELECT COUNT(DISTINCT v.consumer.id) FROM CommercialPageVisit v
        WHERE v.commercial.id = :commercialId
          AND v.createdAt >= :from AND v.createdAt < :to
    """)
    long countUniqueVisitorsInRange(@Param("commercialId") Long commercialId,
                                    @Param("from") ZonedDateTime from,
                                    @Param("to") ZonedDateTime to);

    @Query("SELECT COUNT(v) FROM CommercialPageVisit v WHERE v.commercial.id = :commercialId")
    long countLifetime(@Param("commercialId") Long commercialId);

    /**
     * Consumers distintos que visitaron la página Y además dieron like a algún anuncio del
     * comercial, ambos dentro del rango — la intersección visitantes ∩ likers. Es el numerador
     * de la tasa de conversión: al ser un subconjunto de "consumers que dieron like", el
     * resultado nunca supera a {@code AdLikeRepository.countDistinctConsumersInRange}, por lo
     * que la tasa queda acotada en [0, 100] (a diferencia de comparar visitas vs. likes en
     * bruto, que son conteos de eventos independientes y pueden divergir sin límite).
     */
    @Query("""
        SELECT COUNT(DISTINCT v.consumer.id) FROM CommercialPageVisit v
        WHERE v.commercial.id = :commercialId
          AND v.createdAt >= :from AND v.createdAt < :to
          AND v.consumer.id IN (
              SELECT al.consumer.id FROM AdLike al
              WHERE al.ad.commercial.id = :commercialId
                AND al.createdAt >= :from AND al.createdAt < :to
          )
    """)
    long countConvertedVisitorsInRange(@Param("commercialId") Long commercialId,
                                       @Param("from") ZonedDateTime from,
                                       @Param("to") ZonedDateTime to);

    /** [java.sql.Date day, Long count] por día natural del rango. */
    @Query("""
        SELECT DATE(v.createdAt), COUNT(v) FROM CommercialPageVisit v
        WHERE v.commercial.id = :commercialId
          AND v.createdAt >= :from AND v.createdAt < :to
        GROUP BY DATE(v.createdAt)
    """)
    List<Object[]> visitsByDay(@Param("commercialId") Long commercialId,
                               @Param("from") ZonedDateTime from,
                               @Param("to") ZonedDateTime to);

    /** [Long adId, String adTitle, Long visits, Long uniqueVisitors] por anuncio de origen. */
    @Query("""
        SELECT v.ad.id, v.ad.title, COUNT(v), COUNT(DISTINCT v.consumer.id)
        FROM CommercialPageVisit v
        WHERE v.commercial.id = :commercialId
          AND v.createdAt >= :from AND v.createdAt < :to
          AND v.ad IS NOT NULL
        GROUP BY v.ad.id, v.ad.title
        ORDER BY COUNT(v) DESC
    """)
    List<Object[]> visitsByAd(@Param("commercialId") Long commercialId,
                              @Param("from") ZonedDateTime from,
                              @Param("to") ZonedDateTime to);

    List<CommercialPageVisit> findTop20ByCommercialIdOrderByCreatedAtDesc(Long commercialId);
}
