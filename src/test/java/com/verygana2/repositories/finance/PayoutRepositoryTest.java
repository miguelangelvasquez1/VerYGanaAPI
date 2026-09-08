package com.verygana2.repositories.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.verygana2.models.enums.finance.PayoutStatus;
import com.verygana2.models.enums.finance.WompiTransactionStatus;
import com.verygana2.models.enums.finance.WompiTransactionType;
import com.verygana2.models.finance.Payout;
import com.verygana2.models.finance.WompiTransaction;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para PayoutRepository.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:payout-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("PayoutRepository (integración H2)")
class PayoutRepositoryTest {

    private static final AtomicLong SEQ = new AtomicLong(1);

    @Autowired
    private EntityManager em;

    @Autowired
    private PayoutRepository payoutRepository;

    // ==================== HELPERS ====================

    private static ZonedDateTime now() {
        return ZonedDateTime.now();
    }

    private Payout.PayoutBuilder basePayout(CommercialDetails commercial) {
        return Payout.builder()
                .commercial(commercial)
                .grossAmountCents(10000L)
                .commissionAmountCents(1000L)
                .netAmountCents(9000L)
                .commissionPctApplied(10)
                .status(PayoutStatus.SCHEDULED)
                .scheduledAt(now())
                .periodStart(now().minusDays(1))
                .periodEnd(now());
    }

    private Payout persist(Payout payout) {
        em.persist(payout);
        em.flush();
        return payout;
    }

    private WompiTransaction persistWompiTransaction() {
        long n = SEQ.getAndIncrement();
        WompiTransaction tx = WompiTransaction.builder()
                .wompiId("wompi-" + n)
                .type(WompiTransactionType.TRANSFER_PAYOUT)
                .amountInCents(9000L)
                .status(WompiTransactionStatus.APPROVED)
                .reference("ref-" + n)
                .build();
        em.persist(tx);
        em.flush();
        return tx;
    }

    // ==================== sumTotalByCommercialIdAndPeriod ====================

    @Nested
    @DisplayName("sumTotalByCommercialIdAndPeriod")
    class SumTotalByCommercialIdAndPeriod {

        @Test
        @DisplayName("retorna 0 (COALESCE sobre SUM vacío) cuando no hay payouts pagados en el rango")
        void returnsZeroWhenNoRowsInRange() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);

            BigDecimal total = payoutRepository.sumTotalByCommercialIdAndPeriod(
                    commercial.getId(), now().minusDays(30), now().minusDays(29));

            assertThat(total).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("suma netAmountCents de los payouts pagados en [startDate, endDate)")
        void sumsNetAmountOfPaidPayoutsInRange() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);

            persist(basePayout(commercial).status(PayoutStatus.PAID).netAmountCents(5000L).paidAt(now()).build());
            persist(basePayout(commercial).status(PayoutStatus.PAID).netAmountCents(3000L).paidAt(now()).build());
            // Fuera del rango de fecha: no debe sumarse.
            persist(basePayout(commercial).status(PayoutStatus.PAID).netAmountCents(9999L)
                    .paidAt(now().minusDays(40)).build());

            BigDecimal total = payoutRepository.sumTotalByCommercialIdAndPeriod(
                    commercial.getId(), now().minusDays(1), now().plusDays(1));

            assertThat(total).isEqualByComparingTo(BigDecimal.valueOf(8000));
        }
    }

    // ==================== findByScheduledAtBetweenOrderByScheduledAtDesc ====================

    @Nested
    @DisplayName("findByScheduledAtBetweenOrderByScheduledAtDesc")
    class FindByScheduledAtBetween {

        @Test
        @DisplayName("trae todos los payouts programados en el rango, ordenados por scheduledAt DESC, sin restringir por comercial")
        void returnsAllPayoutsInRangeOrderedDesc() {
            CommercialDetails commercialA = TestEntities.persistCommercial(em);
            CommercialDetails commercialB = TestEntities.persistCommercial(em);

            Payout older = persist(basePayout(commercialA).scheduledAt(now().minusHours(5)).build());
            Payout newer = persist(basePayout(commercialB).scheduledAt(now()).build());
            // Fuera del rango: no debe incluirse.
            persist(basePayout(commercialA).scheduledAt(now().minusDays(10)).build());

            List<Payout> result = payoutRepository.findByScheduledAtBetweenOrderByScheduledAtDesc(
                    now().minusDays(1), now().plusDays(1));

            assertThat(result).extracting(Payout::getId).containsExactly(newer.getId(), older.getId());
        }
    }

    // ==================== findByStatus ====================

    @Nested
    @DisplayName("findByStatus")
    class FindByStatus {

        @Test
        @DisplayName("trae solo los payouts en el status indicado, sin restricción de fecha")
        void returnsOnlyPayoutsInGivenStatus() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Payout scheduled = persist(basePayout(commercial).status(PayoutStatus.SCHEDULED)
                    .scheduledAt(now().minusDays(100)).build());
            persist(basePayout(commercial).status(PayoutStatus.PAID).paidAt(now()).build());

            List<Payout> result = payoutRepository.findByStatus(PayoutStatus.SCHEDULED);

            assertThat(result).extracting(Payout::getId).containsExactly(scheduled.getId());
        }
    }

    // ==================== findByWompiTransactionId ====================

    @Nested
    @DisplayName("findByWompiTransactionId")
    class FindByWompiTransactionId {

        @Test
        @DisplayName("encuentra el Payout vinculado a la WompiTransaction, y vacío si no hay ninguno vinculado")
        void findsLinkedPayoutAndEmptyWhenNotLinked() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            WompiTransaction tx = persistWompiTransaction();
            Payout linked = persist(basePayout(commercial).status(PayoutStatus.PROCESSING).wompiTransaction(tx)
                    .build());
            persist(basePayout(commercial).status(PayoutStatus.SCHEDULED).build());

            Optional<Payout> found = payoutRepository.findByWompiTransactionId(tx.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(linked.getId());

            WompiTransaction unlinkedTx = persistWompiTransaction();
            assertThat(payoutRepository.findByWompiTransactionId(unlinkedTx.getId())).isEmpty();
        }
    }

    // ==================== findByCommercialIdAndPeriod ====================

    @Nested
    @DisplayName("findByCommercialIdAndPeriod")
    class FindByCommercialIdAndPeriod {

        @Test
        @DisplayName("pagina los payouts del comercial en el período, ordenados por scheduledAt DESC")
        void paginatesPayoutsOfCommercialInPeriodOrderedDesc() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            CommercialDetails other = TestEntities.persistCommercial(em);

            Payout older = persist(basePayout(commercial).scheduledAt(now().minusHours(3)).build());
            Payout newer = persist(basePayout(commercial).scheduledAt(now()).build());
            // De otro comercial: no debe incluirse.
            persist(basePayout(other).scheduledAt(now()).build());
            // Fuera del período: no debe incluirse.
            persist(basePayout(commercial).scheduledAt(now().minusDays(10)).build());

            Page<Payout> page = payoutRepository.findByCommercialIdAndPeriod(
                    commercial.getId(), now().minusDays(1), now().plusDays(1), PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getContent()).extracting(Payout::getId).containsExactly(newer.getId(), older.getId());
        }
    }
}
