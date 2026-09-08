package com.verygana2.repositories.finance.plans;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.verygana2.models.enums.finance.plans.SubscriptionStatus;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.finance.plans.Subscription;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para SubscriptionRepository.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:subscription-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("SubscriptionRepository (integración H2)")
class SubscriptionRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    private final AtomicInteger refSeq = new AtomicInteger(1);

    // ==================== HELPERS ====================

    private static ZonedDateTime now() {
        return ZonedDateTime.now(ZoneOffset.UTC);
    }

    /**
     * Persiste una Subscription. Subscription.onCreate() (@PrePersist) siempre
     * pisa createdAt con "now" al insertar, y la columna es
     * updatable = false, así que un simple setter + flush no lo cambia en
     * BD. Para pruebas de período se sobreescribe con un UPDATE JPQL
     * explícito (bulk update), que sí ignora updatable = false.
     */
    private Subscription persistSubscription(CommercialDetails commercial, Plan plan, SubscriptionStatus status,
            ZonedDateTime startDate, ZonedDateTime endDate, ZonedDateTime createdAtOverride) {
        Subscription subscription = Subscription.builder()
                .commercial(commercial)
                .plan(plan)
                .amountPaidCents(20_000_00L)
                .status(status)
                .startDate(startDate)
                .endDate(endDate)
                .wompiReference("VG-SUB-" + refSeq.getAndIncrement())
                .build();
        em.persist(subscription);
        em.flush();

        if (createdAtOverride != null) {
            em.createQuery("UPDATE Subscription s SET s.createdAt = :val WHERE s.id = :id")
                    .setParameter("val", createdAtOverride)
                    .setParameter("id", subscription.getId())
                    .executeUpdate();
        }
        return subscription;
    }

    // ==================== findByWompiReference ====================

    @Nested
    @DisplayName("findByWompiReference")
    class FindByWompiReference {

        @Test
        @DisplayName("encuentra la suscripción por su referencia Wompi")
        void findsSubscriptionByReference() {
            CommercialDetails commercial = TestEntities.persistCommercial(em, PlanCode.BASIC);
            Subscription subscription = persistSubscription(commercial, commercial.getCurrentPlan(),
                    SubscriptionStatus.PENDING_PAYMENT, null, null, null);

            Optional<Subscription> found = subscriptionRepository
                    .findByWompiReference(subscription.getWompiReference());

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(subscription.getId());
        }

        @Test
        @DisplayName("retorna vacío si no existe esa referencia")
        void returnsEmptyWhenReferenceDoesNotExist() {
            Optional<Subscription> found = subscriptionRepository.findByWompiReference("NO-EXISTE");

            assertThat(found).isEmpty();
        }
    }

    // ==================== findByCommercialAndStatus ====================

    @Nested
    @DisplayName("findByCommercialAndStatus")
    class FindByCommercialAndStatus {

        @Test
        @DisplayName("encuentra la suscripción activa del comercial")
        void findsActiveSubscriptionForCommercial() {
            CommercialDetails commercial = TestEntities.persistCommercial(em, PlanCode.BASIC);
            Subscription active = persistSubscription(commercial, commercial.getCurrentPlan(),
                    SubscriptionStatus.ACTIVE, now().minusDays(1), now().plusDays(29), null);

            Optional<Subscription> found = subscriptionRepository.findByCommercialAndStatus(commercial,
                    SubscriptionStatus.ACTIVE);

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(active.getId());
        }

        @Test
        @DisplayName("retorna vacío si el comercial no tiene suscripción en ese estado")
        void returnsEmptyWhenNoSubscriptionInThatStatus() {
            CommercialDetails commercial = TestEntities.persistCommercial(em, PlanCode.BASIC);
            persistSubscription(commercial, commercial.getCurrentPlan(), SubscriptionStatus.EXPIRED,
                    now().minusDays(40), now().minusDays(10), null);

            Optional<Subscription> found = subscriptionRepository.findByCommercialAndStatus(commercial,
                    SubscriptionStatus.ACTIVE);

            assertThat(found).isEmpty();
        }
    }

    // ==================== findExpiredActive ====================

    @Nested
    @DisplayName("findExpiredActive")
    class FindExpiredActive {

        @Test
        @DisplayName("trae suscripciones ACTIVE cuyo endDate ya pasó")
        void bringsActiveSubscriptionsPastEndDate() {
            CommercialDetails commercial = TestEntities.persistCommercial(em, PlanCode.BASIC);
            Subscription expired = persistSubscription(commercial, commercial.getCurrentPlan(),
                    SubscriptionStatus.ACTIVE, now().minusDays(40), now().minusDays(10), null);

            List<Subscription> found = subscriptionRepository.findExpiredActive(now());

            assertThat(found).extracting(Subscription::getId).containsExactly(expired.getId());
        }

        @Test
        @DisplayName("no trae suscripciones ACTIVE cuyo endDate aún no pasó")
        void doesNotBringActiveSubscriptionsNotYetExpired() {
            CommercialDetails commercial = TestEntities.persistCommercial(em, PlanCode.BASIC);
            persistSubscription(commercial, commercial.getCurrentPlan(), SubscriptionStatus.ACTIVE,
                    now().minusDays(1), now().plusDays(29), null);

            List<Subscription> found = subscriptionRepository.findExpiredActive(now());

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("no trae suscripciones en otro estado aunque el endDate ya pasó")
        void doesNotBringNonActiveSubscriptions() {
            CommercialDetails commercial = TestEntities.persistCommercial(em, PlanCode.BASIC);
            persistSubscription(commercial, commercial.getCurrentPlan(), SubscriptionStatus.EXPIRED,
                    now().minusDays(40), now().minusDays(10), null);

            List<Subscription> found = subscriptionRepository.findExpiredActive(now());

            assertThat(found).isEmpty();
        }
    }

    // ==================== findExpiringBetween ====================

    @Nested
    @DisplayName("findExpiringBetween")
    class FindExpiringBetween {

        @Test
        @DisplayName("trae suscripciones ACTIVE cuyo endDate cae dentro del rango")
        void bringsActiveSubscriptionsExpiringInRange() {
            CommercialDetails commercial = TestEntities.persistCommercial(em, PlanCode.BASIC);
            Subscription expiringSoon = persistSubscription(commercial, commercial.getCurrentPlan(),
                    SubscriptionStatus.ACTIVE, now().minusDays(27), now().plusDays(3), null);

            List<Subscription> found = subscriptionRepository.findExpiringBetween(now(), now().plusDays(5));

            assertThat(found).extracting(Subscription::getId).containsExactly(expiringSoon.getId());
        }

        @Test
        @DisplayName("no trae suscripciones cuyo endDate cae fuera del rango")
        void doesNotBringSubscriptionsOutsideRange() {
            CommercialDetails commercial = TestEntities.persistCommercial(em, PlanCode.BASIC);
            persistSubscription(commercial, commercial.getCurrentPlan(), SubscriptionStatus.ACTIVE,
                    now().minusDays(1), now().plusDays(29), null);

            List<Subscription> found = subscriptionRepository.findExpiringBetween(now(), now().plusDays(5));

            assertThat(found).isEmpty();
        }
    }

    // ==================== findByCommercialIdAndPeriod ====================

    @Nested
    @DisplayName("findByCommercialIdAndPeriod")
    class FindByCommercialIdAndPeriod {

        @Test
        @DisplayName("filtra por rango de fechas y ordena descendente por createdAt")
        void filtersByDateRangeOrderedDescending() {
            CommercialDetails commercial = TestEntities.persistCommercial(em, PlanCode.BASIC);

            ZonedDateTime from = now().minusDays(10);
            ZonedDateTime to = now().minusDays(1);

            Subscription older = persistSubscription(commercial, commercial.getCurrentPlan(),
                    SubscriptionStatus.EXPIRED, now().minusDays(9), now().minusDays(8), now().minusDays(8));
            Subscription newer = persistSubscription(commercial, commercial.getCurrentPlan(),
                    SubscriptionStatus.ACTIVE, now().minusDays(3), now().plusDays(27), now().minusDays(3));
            persistSubscription(commercial, commercial.getCurrentPlan(), SubscriptionStatus.EXPIRED,
                    now().minusDays(25), now().minusDays(24), now().minusDays(20));

            List<Subscription> found = subscriptionRepository.findByCommercialIdAndPeriod(commercial.getId(), from,
                    to);

            assertThat(found).extracting(Subscription::getId).containsExactly(newer.getId(), older.getId());
        }
    }

    // ==================== findAbandonedCheckouts ====================

    @Nested
    @DisplayName("findAbandonedCheckouts")
    class FindAbandonedCheckouts {

        @Test
        @DisplayName("trae checkouts PENDING_PAYMENT creados antes del umbral")
        void bringsAbandonedPendingCheckouts() {
            CommercialDetails commercial = TestEntities.persistCommercial(em, PlanCode.BASIC);
            Subscription abandoned = persistSubscription(commercial, commercial.getCurrentPlan(),
                    SubscriptionStatus.PENDING_PAYMENT, null, null, now().minusHours(48));

            List<Subscription> found = subscriptionRepository.findAbandonedCheckouts(now().minusHours(24));

            assertThat(found).extracting(Subscription::getId).containsExactly(abandoned.getId());
        }

        @Test
        @DisplayName("no trae checkouts PENDING_PAYMENT recientes (dentro del umbral)")
        void doesNotBringRecentPendingCheckouts() {
            CommercialDetails commercial = TestEntities.persistCommercial(em, PlanCode.BASIC);
            persistSubscription(commercial, commercial.getCurrentPlan(), SubscriptionStatus.PENDING_PAYMENT, null,
                    null, now().minusHours(1));

            List<Subscription> found = subscriptionRepository.findAbandonedCheckouts(now().minusHours(24));

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("no trae suscripciones que no estén PENDING_PAYMENT aunque sean viejas")
        void doesNotBringNonPendingSubscriptions() {
            CommercialDetails commercial = TestEntities.persistCommercial(em, PlanCode.BASIC);
            persistSubscription(commercial, commercial.getCurrentPlan(), SubscriptionStatus.ACTIVE,
                    now().minusDays(3), now().plusDays(27), now().minusHours(48));

            List<Subscription> found = subscriptionRepository.findAbandonedCheckouts(now().minusHours(24));

            assertThat(found).isEmpty();
        }
    }
}
