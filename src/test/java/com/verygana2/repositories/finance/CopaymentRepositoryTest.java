package com.verygana2.repositories.finance;

import static org.assertj.core.api.Assertions.assertThat;

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

import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.enums.finance.CopaymentStatus;
import com.verygana2.models.enums.marketplace.ProductStatus;
import com.verygana2.models.enums.marketplace.PurchaseStatus;
import com.verygana2.models.enums.marketplace.StockStatus;
import com.verygana2.models.finance.Copayment;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductCategory;
import com.verygana2.models.marketplace.ProductImageAsset;
import com.verygana2.models.marketplace.ProductStock;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para CopaymentRepository — el
 * repositorio con la cadena de fetch joins más compleja del dominio finance
 * (purchase → items → product → imageAsset, items → assignedProductStock,
 * consumer, y en findExpiredPending además consumer → user). Cubre que el
 * DISTINCT evita duplicar el Copayment raíz cuando la compra tiene varios
 * ítems (fan-out del LEFT JOIN a items).
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:copayment-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("CopaymentRepository (integración H2)")
class CopaymentRepositoryTest {

    private static final AtomicLong SEQ = new AtomicLong(1);

    @Autowired
    private EntityManager em;

    @Autowired
    private CopaymentRepository copaymentRepository;

    // ==================== HELPERS ====================

    private static ZonedDateTime now() {
        return ZonedDateTime.now();
    }

    private ProductCategory persistCategory(String name) {
        ProductCategory category = new ProductCategory();
        category.setName(name);
        em.persist(category);
        em.flush();
        return category;
    }

    private Product persistProduct(CommercialDetails commercial, ProductCategory category, String name) {
        Product product = new Product();
        product.setCommercial(commercial);
        product.setProductCategory(category);
        product.setName(name);
        product.setDescription("Descripción de " + name);
        product.setPriceCents(10000L);
        product.setMaxKeysPct(20);
        em.persist(product);
        em.flush();

        product.setStatus(ProductStatus.ACTIVE);
        em.flush();
        return product;
    }

    private ProductImageAsset persistImageAsset(Product product, String objectKey) {
        ProductImageAsset asset = new ProductImageAsset();
        asset.setObjectKey(objectKey);
        asset.setSizeBytes(1024L);
        asset.setStatus(AssetStatus.VALIDATED);
        asset.setProduct(product);
        em.persist(asset);
        em.flush();
        return asset;
    }

    private ProductStock persistStock(Product product, String code) {
        ProductStock stock = ProductStock.builder()
                .product(product)
                .code(code)
                .codeHash("hash-" + code)
                .status(StockStatus.SOLD)
                .build();
        em.persist(stock);
        em.flush();
        return stock;
    }

    private Purchase persistPurchase(ConsumerDetails consumer) {
        long n = SEQ.getAndIncrement();
        Purchase purchase = new Purchase();
        purchase.setReferenceId("REF-COPAY-" + n);
        purchase.setConsumer(consumer);
        purchase.setStatus(PurchaseStatus.COMPLETED);
        purchase.setTotalCents(20000L);
        purchase.setKeysValueCents(0L);
        purchase.setCashCents(20000L);
        purchase.setCommissionCents(2000L);
        purchase.setNetToCommercialsCents(18000L);
        em.persist(purchase);
        em.flush();
        return purchase;
    }

    private PurchaseItem persistItem(Purchase purchase, Product product, ProductStock stock) {
        PurchaseItem item = PurchaseItem.builder()
                .purchase(purchase)
                .product(product)
                .productNameSnapshot(product.getName())
                .commercialId(product.getCommercial().getId())
                .assignedProductStock(stock)
                .unitPriceCents(10000L)
                .subtotalCents(10000L)
                .maxKeysPctAtPurchase(20)
                .createdAt(now())
                .build();
        em.persist(item);
        em.flush();
        return item;
    }

    private Copayment.CopaymentBuilder baseCopayment(Purchase purchase, ConsumerDetails consumer) {
        return Copayment.builder()
                .purchase(purchase)
                .consumer(consumer)
                .keysUsed(0L)
                .keysValueCents(0L)
                .cashAmountCents(20000L)
                .totalAmountCents(20000L);
    }

    private Copayment persist(Copayment copayment) {
        em.persist(copayment);
        em.flush();
        return copayment;
    }

    // ==================== findByPurchaseReferenceIdWithDetails ====================

    @Nested
    @DisplayName("findByPurchaseReferenceIdWithDetails")
    class FindByPurchaseReferenceIdWithDetails {

        @Test
        @DisplayName("trae el copayment con la cadena completa fetch-eada, sin duplicar filas por el fan-out de items (DISTINCT)")
        void fetchesFullGraphWithoutDuplicatingRootDueToDistinct() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria copayment details");
            Product productA = persistProduct(commercial, category, "Producto copayment A");
            persistImageAsset(productA, "img-copay-a.png");
            Product productB = persistProduct(commercial, category, "Producto copayment B");
            persistImageAsset(productB, "img-copay-b.png");
            ProductStock stockA = persistStock(productA, "code-copay-a");

            Purchase purchase = persistPurchase(consumer);
            persistItem(purchase, productA, stockA);
            persistItem(purchase, productB, null);

            Copayment copayment = persist(baseCopayment(purchase, consumer).status(CopaymentStatus.COMPLETED)
                    .build());

            em.clear();

            Optional<Copayment> found = copaymentRepository.findByPurchaseReferenceIdWithDetails(
                    purchase.getReferenceId());

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(copayment.getId());
            // Sin el DISTINCT, el fan-out de 2 items habría hecho fallar este Optional
            // (findByPurchaseReferenceIdWithDetails) con NonUniqueResultException.
            assertThat(found.get().getPurchase().getItems()).hasSize(2);
            assertThat(found.get().getConsumer().getId()).isEqualTo(consumer.getId());

            PurchaseItem withStock = found.get().getPurchase().getItems().stream()
                    .filter(i -> i.getAssignedProductStock() != null)
                    .findFirst().orElseThrow();
            assertThat(withStock.getAssignedProductStock().getCode()).isEqualTo("code-copay-a");
            assertThat(withStock.getProduct().getImageAsset().getObjectKey()).isEqualTo("img-copay-a.png");
        }

        @Test
        @DisplayName("no encuentra nada para un referenceId inexistente")
        void returnsEmptyForUnknownReference() {
            assertThat(copaymentRepository.findByPurchaseReferenceIdWithDetails("REF-NO-EXISTE")).isEmpty();
        }
    }

    // ==================== findExpiredPending ====================

    @Nested
    @DisplayName("findExpiredPending")
    class FindExpiredPending {

        @Test
        @DisplayName("trae copagos PENDING creados antes del umbral, con consumer.user fetch-eado (doble salto)")
        void returnsPendingCopaymentsBeforeThresholdWithUserFetched() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria copayment expired");
            Product product = persistProduct(commercial, category, "Producto copayment expired");

            Purchase purchase = persistPurchase(consumer);
            persistItem(purchase, product, null);
            Copayment pending = persist(baseCopayment(purchase, consumer).status(CopaymentStatus.PENDING).build());

            em.clear();

            List<Copayment> result = copaymentRepository.findExpiredPending(CopaymentStatus.PENDING,
                    now().plusDays(1));

            assertThat(result).extracting(Copayment::getId).containsExactly(pending.getId());
            assertThat(result.get(0).getConsumer().getUser().getEmail()).isEqualTo(consumer.getUser().getEmail());
        }

        @Test
        @DisplayName("excluye copagos con otro status y los que aún no cruzan el umbral de fecha")
        void excludesOtherStatusAndNotYetExpired() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria copayment expired exclusiones");
            Product product = persistProduct(commercial, category, "Producto copayment expired exclusiones");

            Purchase purchaseCompleted = persistPurchase(consumer);
            persistItem(purchaseCompleted, product, null);
            persist(baseCopayment(purchaseCompleted, consumer).status(CopaymentStatus.COMPLETED).build());

            Purchase purchaseNotYetExpired = persistPurchase(consumer);
            persistItem(purchaseNotYetExpired, product, null);
            persist(baseCopayment(purchaseNotYetExpired, consumer).status(CopaymentStatus.PENDING).build());

            // El umbral queda en el pasado: purchase.createdAt (= ahora) no es anterior a él.
            List<Copayment> result = copaymentRepository.findExpiredPending(CopaymentStatus.PENDING,
                    now().minusDays(1));

            assertThat(result).isEmpty();
        }
    }

    // ==================== findByPurchaseId ====================

    @Nested
    @DisplayName("findByPurchaseId")
    class FindByPurchaseId {

        @Test
        @DisplayName("encuentra el copayment de la compra dada, y vacío para una compra sin copayment")
        void findsCopaymentOfPurchaseAndEmptyOtherwise() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            Purchase purchase = persistPurchase(consumer);
            Copayment copayment = persist(baseCopayment(purchase, consumer).status(CopaymentStatus.COMPLETED)
                    .build());

            Purchase purchaseWithoutCopayment = persistPurchase(consumer);

            assertThat(copaymentRepository.findByPurchaseId(purchase.getId())).map(Copayment::getId)
                    .contains(copayment.getId());
            assertThat(copaymentRepository.findByPurchaseId(purchaseWithoutCopayment.getId())).isEmpty();
        }
    }
}
