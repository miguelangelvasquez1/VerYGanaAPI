package com.verygana2.repositories.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import com.verygana2.models.enums.finance.PayoutStatus;
import com.verygana2.models.finance.Payout;
import com.verygana2.models.finance.PayoutItem;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para PayoutItemRepository.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:payout-item-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("PayoutItemRepository (integración H2)")
class PayoutItemRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private PayoutItemRepository payoutItemRepository;

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

    private Payout persistPayout(CommercialDetails commercial) {
        Payout payout = Payout.builder()
                .commercial(commercial)
                .grossAmountCents(10000L)
                .commissionAmountCents(1000L)
                .netAmountCents(9000L)
                .commissionPctApplied(10)
                .status(PayoutStatus.SCHEDULED)
                .scheduledAt(now())
                .periodStart(now().minusDays(1))
                .periodEnd(now())
                .build();
        em.persist(payout);
        em.flush();
        return payout;
    }

    private PayoutItem newPayoutItem(Payout payout, PurchaseItem item) {
        return PayoutItem.builder()
                .payout(payout)
                .purchaseItem(item)
                .amountCents(9000L)
                .build();
    }

    // ==================== existsByPurchaseItemId ====================

    @Nested
    @DisplayName("existsByPurchaseItemId")
    class ExistsByPurchaseItemId {

        @Test
        @DisplayName("true cuando el purchase item ya entró a un payout")
        void trueWhenPurchaseItemAlreadyHasPayoutItem() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Purchase purchase = persistPurchase(consumer, "REF-EXISTS-1");
            PurchaseItem item = persistPurchaseItem(purchase, commercial.getId());
            Payout payout = persistPayout(commercial);
            em.persist(newPayoutItem(payout, item));
            em.flush();

            assertThat(payoutItemRepository.existsByPurchaseItemId(item.getId())).isTrue();
        }

        @Test
        @DisplayName("false cuando el purchase item no tiene payout item asociado")
        void falseWhenPurchaseItemHasNoPayoutItem() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Purchase purchase = persistPurchase(consumer, "REF-EXISTS-2");
            PurchaseItem item = persistPurchaseItem(purchase, commercial.getId());

            assertThat(payoutItemRepository.existsByPurchaseItemId(item.getId())).isFalse();
        }
    }

    // ==================== unique purchase_item_id ====================

    @Nested
    @DisplayName("constraint UNIQUE en purchase_item_id")
    class UniquePurchaseItemIdConstraint {

        @Test
        @DisplayName("insertar un segundo PayoutItem para el mismo purchase item lanza DataIntegrityViolationException")
        void duplicatePurchaseItemThrowsDataIntegrityViolationException() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Purchase purchase = persistPurchase(consumer, "REF-UNIQUE-1");
            PurchaseItem item = persistPurchaseItem(purchase, commercial.getId());
            Payout payout = persistPayout(commercial);

            payoutItemRepository.saveAndFlush(newPayoutItem(payout, item));

            assertThatThrownBy(() -> payoutItemRepository.saveAndFlush(newPayoutItem(payout, item)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
