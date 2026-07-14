package com.verygana2.models.finance.plans;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.verygana2.models.enums.finance.plans.SubscriptionStatus;
import com.verygana2.models.finance.WompiTransaction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de la entidad {@link Subscription} (plan BASIC): activación tras el
 * pago de Wompi, vigencia de 1 mes, y las transiciones de expiración/renovación.
 */
@DisplayName("Subscription (entidad)")
class SubscriptionTest {

    @Test
    @DisplayName("activate: pasa a ACTIVE con vigencia de 1 mes desde ahora")
    void activate_setsActiveWithOneMonthPeriod() {
        Subscription sub = Subscription.builder().status(SubscriptionStatus.PENDING_PAYMENT).build();
        WompiTransaction tx = WompiTransaction.builder().build();

        sub.activate(tx);

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(sub.getWompiTransaction()).isSameAs(tx);
        assertThat(sub.getEndDate()).isAfter(sub.getStartDate().plusDays(29));
    }

    @Test
    @DisplayName("isCurrentlyActive: true solo si el status es ACTIVE y aún no vence")
    void isCurrentlyActive_requiresActiveAndNotExpired() {
        Subscription active = Subscription.builder()
                .status(SubscriptionStatus.ACTIVE).endDate(ZonedDateTime.now().plusDays(5)).build();
        assertThat(active.isCurrentlyActive()).isTrue();

        Subscription expired = Subscription.builder()
                .status(SubscriptionStatus.ACTIVE).endDate(ZonedDateTime.now().minusDays(1)).build();
        assertThat(expired.isCurrentlyActive()).isFalse();

        Subscription cancelled = Subscription.builder()
                .status(SubscriptionStatus.CANCELLED).endDate(ZonedDateTime.now().plusDays(5)).build();
        assertThat(cancelled.isCurrentlyActive()).isFalse();
    }

    @Test
    @DisplayName("daysRemaining: 0 si no tiene endDate, positivo si aún faltan días")
    void daysRemaining_computesFromEndDate() {
        assertThat(Subscription.builder().endDate(null).build().daysRemaining()).isZero();

        Subscription sub = Subscription.builder().endDate(ZonedDateTime.now().plusDays(10)).build();
        assertThat(sub.daysRemaining()).isBetween(8L, 10L);
    }

    @Test
    @DisplayName("expire: pasa a EXPIRED y marca terminatedAt")
    void expire_setsExpiredAndTerminatedAt() {
        Subscription sub = Subscription.builder().status(SubscriptionStatus.ACTIVE).build();

        sub.expire();

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(sub.getTerminatedAt()).isNotNull();
    }

    @Test
    @DisplayName("markAsRenewed: pasa a RENEWED y marca terminatedAt")
    void markAsRenewed_setsRenewedAndTerminatedAt() {
        Subscription sub = Subscription.builder().status(SubscriptionStatus.ACTIVE).build();

        sub.markAsRenewed();

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.RENEWED);
        assertThat(sub.getTerminatedAt()).isNotNull();
    }
}
