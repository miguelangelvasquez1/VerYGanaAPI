package com.verygana2.repositories.finance.plans;
 
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
 
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
 
import com.verygana2.models.enums.finance.plans.SubscriptionStatus;
import com.verygana2.models.finance.plans.Subscription;
import com.verygana2.models.userDetails.CommercialDetails;
 
@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
 
    /**
     * Lookup principal desde el webhook.
     * Cuando llega el evento de Wompi, buscamos por la referencia
     * para encontrar la Subscription sin necesitar commercial en WompiTransaction.
     */
    Optional<Subscription> findByWompiReference(String wompiReference);
 
    /**
     * Suscripción activa actual de un comercial.
     */
    Optional<Subscription> findByCommercialAndStatus(
            CommercialDetails commercial, SubscriptionStatus status);
 
    /**
     * Suscripciones vencidas que el job debe expirar.
     */
    @Query("SELECT s FROM Subscription s WHERE s.status = 'ACTIVE' AND s.endDate < :now")
    List<Subscription> findExpiredActive(@Param("now") ZonedDateTime now);
 
    /**
     * Suscripciones próximas a vencer para recordatorios.
     */
    @Query("SELECT s FROM Subscription s WHERE s.status = 'ACTIVE' " +
           "AND s.endDate BETWEEN :from AND :to")
    List<Subscription> findExpiringBetween(
            @Param("from") ZonedDateTime from,
            @Param("to") ZonedDateTime to);

    /**
     * Historial filtrado por rango de fechas para el panel de facturación.
     */
    @Query("SELECT s FROM Subscription s WHERE s.commercial.id = :commercialId " +
           "AND s.createdAt >= :from AND s.createdAt < :to " +
           "ORDER BY s.createdAt DESC")
    List<Subscription> findByCommercialIdAndPeriod(
            @Param("commercialId") Long commercialId,
            @Param("from") ZonedDateTime from,
            @Param("to") ZonedDateTime to);

    /**
     * Limpieza de pagos abandonados — checkouts generados hace más de X horas
     * que nunca fueron completados.
     */
    @Query("SELECT s FROM Subscription s WHERE s.status = 'PENDING_PAYMENT' " +
           "AND s.createdAt < :before")
    List<Subscription> findAbandonedCheckouts(@Param("before") ZonedDateTime before);
}