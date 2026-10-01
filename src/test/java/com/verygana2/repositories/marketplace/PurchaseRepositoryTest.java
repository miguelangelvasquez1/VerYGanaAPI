package com.verygana2.repositories.marketplace;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
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

import com.verygana2.models.enums.marketplace.ProductStatus;
import com.verygana2.models.enums.marketplace.PurchaseStatus;
import com.verygana2.models.enums.marketplace.StockStatus;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductCategory;
import com.verygana2.models.marketplace.ProductStock;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para PurchaseRepository. Cubre las
 * 3 consultas del repositorio: la variante con fetch de ítems/producto/stock
 * usada en el detalle de "mi compra", y las derivadas de listado/ownership.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:purchase-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("PurchaseRepository (integración H2)")
class PurchaseRepositoryTest {

    private static final AtomicLong SEQ = new AtomicLong(1);

    @Autowired
    private EntityManager em;

    @Autowired
    private PurchaseRepository purchaseRepository;

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

    /** Product.onCreate() fuerza status=PENDING; se actualiza con un segundo flush si hace falta otro. */
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

    private ProductStock persistStock(Product product, String codeSuffix) {
        ProductStock stock = ProductStock.builder()
                .product(product)
                .code("encrypted-code-" + codeSuffix)
                .codeHash("hash-" + codeSuffix)
                .status(StockStatus.SOLD)
                .build();
        em.persist(stock);
        em.flush();
        return stock;
    }

    private Purchase persistPurchase(ConsumerDetails consumer, PurchaseStatus status) {
        long n = SEQ.getAndIncrement();
        Purchase purchase = new Purchase();
        purchase.setReferenceId("REF-" + n);
        purchase.setConsumer(consumer);
        purchase.setStatus(status);
        purchase.setTotalCents(10000L);
        purchase.setKeysValueCents(0L);
        purchase.setCashCents(10000L);
        purchase.setCommissionCents(1000L);
        purchase.setNetToCommercialsCents(9000L);
        em.persist(purchase);
        em.flush();
        return purchase;
    }

    private PurchaseItem persistItem(Purchase purchase, Product product, ProductStock stock, Long commercialId) {
        PurchaseItem item = PurchaseItem.builder()
                .purchase(purchase)
                .product(product)
                .assignedProductStock(stock)
                .productNameSnapshot(product != null ? product.getName() : "Producto purgado")
                .commercialId(commercialId)
                .unitPriceCents(10000L)
                .subtotalCents(10000L)
                .commissionCents(1000L)
                .netToCommercialCents(9000L)
                .maxKeysPctAtPurchase(20)
                .createdAt(now())
                .build();
        em.persist(item);
        em.flush();
        return item;
    }

    // ==================== findByIdAndConsumerIdWithItems ====================

    @Nested
    @DisplayName("findByIdAndConsumerIdWithItems")
    class FindByIdAndConsumerIdWithItems {

        @Test
        @DisplayName("trae la compra con consumer/user/items/product/stock ya inicializados para el consumer dueño")
        void fetchesFullGraphForOwningConsumer() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria detalle compra");
            Product product = persistProduct(commercial, category, "Producto detalle compra");
            ProductStock stock = persistStock(product, "detalle-1");

            Purchase purchase = persistPurchase(consumer, PurchaseStatus.COMPLETED);
            persistItem(purchase, product, stock, commercial.getId());

            em.clear();

            Optional<Purchase> found = purchaseRepository.findByIdAndConsumerIdWithItems(purchase.getId(),
                    consumer.getId());

            assertThat(found).isPresent();
            Purchase result = found.get();
            assertThat(result.getConsumer().getUser().getEmail()).isEqualTo(consumer.getUser().getEmail());
            assertThat(result.getItems()).hasSize(1);
            PurchaseItem fetchedItem = result.getItems().get(0);
            assertThat(fetchedItem.getProduct().getName()).isEqualTo("Producto detalle compra");
            assertThat(fetchedItem.getAssignedProductStock().getCodeHash()).isEqualTo("hash-detalle-1");
        }

        @Test
        @DisplayName("sigue trayendo la compra e ítems aunque el producto del ítem ya haya sido purgado (LEFT JOIN)")
        void fetchesPurchaseEvenWhenItemProductWasPurged() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            Purchase purchase = persistPurchase(consumer, PurchaseStatus.COMPLETED);
            persistItem(purchase, null, null, 999L);

            em.clear();

            Optional<Purchase> found = purchaseRepository.findByIdAndConsumerIdWithItems(purchase.getId(),
                    consumer.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getItems()).hasSize(1);
            assertThat(found.get().getItems().get(0).getProduct()).isNull();
        }

        @Test
        @DisplayName("retorna Optional.empty si el purchaseId no existe o pertenece a otro consumer")
        void returnsEmptyWhenNotFoundOrWrongOwner() {
            ConsumerDetails owner = TestEntities.persistConsumer(em);
            ConsumerDetails other = TestEntities.persistConsumer(em);
            Purchase purchase = persistPurchase(owner, PurchaseStatus.COMPLETED);

            assertThat(purchaseRepository.findByIdAndConsumerIdWithItems(999999L, owner.getId())).isEmpty();
            assertThat(purchaseRepository.findByIdAndConsumerIdWithItems(purchase.getId(), other.getId())).isEmpty();
        }
    }

    // ==================== findByConsumerId ====================

    @Nested
    @DisplayName("findByConsumerId")
    class FindByConsumerId {

        @Test
        @DisplayName("pagina solo las compras del consumer dado")
        void pagesOnlyPurchasesOfGivenConsumer() {
            ConsumerDetails owner = TestEntities.persistConsumer(em);
            ConsumerDetails other = TestEntities.persistConsumer(em);
            persistPurchase(owner, PurchaseStatus.COMPLETED);
            persistPurchase(owner, PurchaseStatus.PENDING);
            persistPurchase(other, PurchaseStatus.COMPLETED);

            Page<Purchase> page = purchaseRepository.findByConsumerId(owner.getId(), PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getContent()).allMatch(p -> p.getConsumer().getId().equals(owner.getId()));
        }
    }

    // ==================== findByIdAndConsumerId ====================

    @Nested
    @DisplayName("findByIdAndConsumerId")
    class FindByIdAndConsumerId {

        @Test
        @DisplayName("retorna la compra solo bajo el consumer dueño")
        void returnsPurchaseOnlyUnderOwningConsumer() {
            ConsumerDetails owner = TestEntities.persistConsumer(em);
            ConsumerDetails other = TestEntities.persistConsumer(em);
            Purchase purchase = persistPurchase(owner, PurchaseStatus.COMPLETED);

            Optional<Purchase> found = purchaseRepository.findByIdAndConsumerId(purchase.getId(), owner.getId());
            Optional<Purchase> notFound = purchaseRepository.findByIdAndConsumerId(purchase.getId(), other.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getReferenceId()).isEqualTo(purchase.getReferenceId());
            assertThat(notFound).isEmpty();
        }
    }
}
