package com.verygana2.repositories.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.verygana2.models.enums.finance.CashRefundStatus;
import com.verygana2.models.finance.PurchaseItemCashRefund;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para PurchaseItemCashRefundRepository.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:purchase-item-cash-refund-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("PurchaseItemCashRefundRepository (integración H2)")
class PurchaseItemCashRefundRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private PurchaseItemCashRefundRepository cashRefundRepository;

    // ==================== HELPERS ====================

    private static ZonedDateTime now() {
        return ZonedDateTime.now();
    }

    private Purchase persistPurchase(ConsumerDetails consumer, String referenceId) {
        Purchase purchase = new Purchase();
        purchase.setReferenceId(referenceId);
        purchase.setConsumer(consumer);
        purchase.setTotalCents(10000L);
        purchase.setKeysValueCents(0L);
        purchase.setCashCents(10000L);
        purchase.setCommissionCents(1000L);
        purchase.setNetToCommercialsCents(9000L);
        em.persist(purchase);
        em.flush();
        return purchase;
    }

    private PurchaseItem persistPurchaseItem(Purchase purchase, Long commercialId) {
        PurchaseItem item = PurchaseItem.builder()
                .purchase(purchase)
                .commercialId(commercialId)
                .unitPriceCents(10000L)
                .subtotalCents(10000L)
                .maxKeysPctAtPurchase(20)
                .createdAt(now())
                .build();
        em.persist(item);
        em.flush();
        return item;
    }

    /**
     * Persiste un PurchaseItemCashRefund. createdAt siempre se fuerza a "now"
     * por el @PrePersist de la entidad, así que si se pide otro valor se
     * sobrescribe y se hace flush de nuevo (mismo patrón que
     * PrizeRepositoryTest.persistRaffleWinner).
     */
    private PurchaseItemCashRefund persistCashRefund(PurchaseItem item, CashRefundStatus status,
            ZonedDateTime createdAt) {
        PurchaseItemCashRefund refund = PurchaseItemCashRefund.builder()
                .purchaseItem(item)
                .amountCents(5000L)
                .status(status)
                .build();
        em.persist(refund);
        em.flush();

        if (createdAt != null) {
            // createdAt es updatable=false: el setter no se refleja en un UPDATE
            // normal del contexto de persistencia. Se fuerza con un bulk update
            // JPQL (que sí respeta el valor, ignorando el flag updatable) y
            // luego se refresca la entidad administrada.
            em.createQuery("UPDATE PurchaseItemCashRefund p SET p.createdAt = :createdAt WHERE p.id = :id")
                    .setParameter("createdAt", createdAt)
                    .setParameter("id", refund.getId())
                    .executeUpdate();
            em.refresh(refund);
        }
        return refund;
    }

    // ==================== findByPurchaseItemId ====================

    @Nested
    @DisplayName("findByPurchaseItemId")
    class FindByPurchaseItemId {

        @Test
        @DisplayName("trae el reembolso asociado al purchase item")
        void returnsCashRefundForPurchaseItem() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Purchase purchase = persistPurchase(consumer, "REF-CASH-1");
            PurchaseItem item = persistPurchaseItem(purchase, commercial.getId());
            PurchaseItemCashRefund refund = persistCashRefund(item, CashRefundStatus.PENDING_PAYMENT, null);

            Optional<PurchaseItemCashRefund> found = cashRefundRepository.findByPurchaseItemId(item.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(refund.getId());
        }

        @Test
        @DisplayName("vacío cuando el purchase item no tiene reembolso")
        void emptyWhenPurchaseItemHasNoCashRefund() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Purchase purchase = persistPurchase(consumer, "REF-CASH-2");
            PurchaseItem item = persistPurchaseItem(purchase, commercial.getId());

            Optional<PurchaseItemCashRefund> found = cashRefundRepository.findByPurchaseItemId(item.getId());

            assertThat(found).isEmpty();
        }
    }

    // ==================== findByStatusAndRangeDates ====================

    @Nested
    @DisplayName("findByStatusAndRangeDates")
    class FindByStatusAndRangeDates {

        @Test
        @DisplayName("sin filtros (status y fechas null) trae todos los reembolsos")
        void noFiltersReturnsAll() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Purchase purchase = persistPurchase(consumer, "REF-RANGE-NONE");

            PurchaseItemCashRefund pending = persistCashRefund(
                    persistPurchaseItem(purchase, commercial.getId()), CashRefundStatus.PENDING_PAYMENT,
                    now().minusDays(10));
            PurchaseItemCashRefund paid = persistCashRefund(
                    persistPurchaseItem(purchase, commercial.getId()), CashRefundStatus.PAID,
                    now().minusDays(1));

            Page<PurchaseItemCashRefund> page = cashRefundRepository.findByStatusAndRangeDates(null, null, null,
                    PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(PurchaseItemCashRefund::getId)
                    .containsExactlyInAnyOrder(pending.getId(), paid.getId());
        }

        @Test
        @DisplayName("solo status filtra por el estado indicado")
        void onlyStatusFilters() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Purchase purchase = persistPurchase(consumer, "REF-RANGE-STATUS");

            PurchaseItemCashRefund pending = persistCashRefund(
                    persistPurchaseItem(purchase, commercial.getId()), CashRefundStatus.PENDING_PAYMENT, now());
            persistCashRefund(persistPurchaseItem(purchase, commercial.getId()), CashRefundStatus.PAID, now());

            Page<PurchaseItemCashRefund> page = cashRefundRepository.findByStatusAndRangeDates(
                    CashRefundStatus.PENDING_PAYMENT, null, null, PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(PurchaseItemCashRefund::getId).containsExactly(pending.getId());
        }

        @Test
        @DisplayName("solo rango de fechas filtra por createdAt en [startDate, endDate)")
        void onlyDateRangeFilters() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Purchase purchase = persistPurchase(consumer, "REF-RANGE-DATES");

            PurchaseItemCashRefund withinRange = persistCashRefund(
                    persistPurchaseItem(purchase, commercial.getId()), CashRefundStatus.PENDING_PAYMENT,
                    now().minusDays(5));
            persistCashRefund(persistPurchaseItem(purchase, commercial.getId()), CashRefundStatus.PAID,
                    now().minusDays(40));

            Page<PurchaseItemCashRefund> page = cashRefundRepository.findByStatusAndRangeDates(null,
                    now().minusDays(10), now(), PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(PurchaseItemCashRefund::getId)
                    .containsExactly(withinRange.getId());
        }

        @Test
        @DisplayName("status y rango de fechas combinados filtran por ambos criterios")
        void statusAndDateRangeCombined() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Purchase purchase = persistPurchase(consumer, "REF-RANGE-BOTH");

            PurchaseItemCashRefund matches = persistCashRefund(
                    persistPurchaseItem(purchase, commercial.getId()), CashRefundStatus.PENDING_PAYMENT,
                    now().minusDays(5));
            // Mismo status pero fuera de rango.
            persistCashRefund(persistPurchaseItem(purchase, commercial.getId()), CashRefundStatus.PENDING_PAYMENT,
                    now().minusDays(40));
            // Mismo rango pero distinto status.
            persistCashRefund(persistPurchaseItem(purchase, commercial.getId()), CashRefundStatus.PAID,
                    now().minusDays(5));

            Page<PurchaseItemCashRefund> page = cashRefundRepository.findByStatusAndRangeDates(
                    CashRefundStatus.PENDING_PAYMENT, now().minusDays(10), now(), PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(PurchaseItemCashRefund::getId).containsExactly(matches.getId());
        }
    }
}
