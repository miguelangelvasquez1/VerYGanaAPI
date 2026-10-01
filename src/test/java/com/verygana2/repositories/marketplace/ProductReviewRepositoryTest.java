package com.verygana2.repositories.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductCategory;
import com.verygana2.models.marketplace.ProductReview;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para ProductReviewRepository.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:product-review-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("ProductReviewRepository (integración H2)")
class ProductReviewRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private ProductReviewRepository productReviewRepository;

    private static final AtomicLong SEQ = new AtomicLong(1);

    // ==================== HELPERS ====================

    private ProductCategory persistCategory() {
        long n = SEQ.getAndIncrement();
        ProductCategory category = new ProductCategory();
        category.setName("categoria-review-" + n);
        em.persist(category);
        em.flush();
        return category;
    }

    private Product persistProduct(CommercialDetails commercial, ProductCategory category) {
        long n = SEQ.getAndIncrement();
        Product product = new Product();
        product.setCommercial(commercial);
        product.setProductCategory(category);
        product.setName("Producto review " + n);
        product.setPriceCents(50_000L);
        product.setMaxKeysPct(20);
        em.persist(product);
        em.flush();
        return product;
    }

    /**
     * ProductReview.purchaseItem es nullable=false y unique: cada review
     * necesita su propio PurchaseItem (y este su propia Purchase). PurchaseItem
     * no tiene @PrePersist, así que createdAt se setea manualmente.
     */
    private PurchaseItem persistPurchaseItem(ConsumerDetails consumer, Product product) {
        long n = SEQ.getAndIncrement();
        Purchase purchase = new Purchase();
        purchase.setConsumer(consumer);
        purchase.setReferenceId("REF-" + n);
        em.persist(purchase);
        em.flush();

        PurchaseItem item = new PurchaseItem();
        item.setPurchase(purchase);
        item.setProduct(product);
        item.setUnitPriceCents(product.getPriceCents());
        item.setSubtotalCents(product.getPriceCents());
        item.setMaxKeysPctAtPurchase(product.getMaxKeysPct());
        item.setCreatedAt(ZonedDateTime.now(ZoneOffset.UTC));
        em.persist(item);
        em.flush();
        return item;
    }

    /**
     * Persiste una review. El @PrePersist de ProductReview siempre fuerza
     * visible=true al crear; si se pide oculta, se actualiza el campo después
     * con un segundo flush.
     */
    private ProductReview persistReview(ConsumerDetails consumer, Product product, int rating, boolean visible) {
        PurchaseItem purchaseItem = persistPurchaseItem(consumer, product);

        ProductReview review = new ProductReview();
        review.setConsumer(consumer);
        review.setProduct(product);
        review.setPurchaseItem(purchaseItem);
        review.setRating(rating);
        review.setComment("Comentario de prueba");
        em.persist(review);
        em.flush();

        if (!visible) {
            review.setVisible(false);
            em.flush();
        }
        return review;
    }

    // ==================== productAvgRating ====================

    @Nested
    @DisplayName("productAvgRating")
    class ProductAvgRating {

        @Test
        @DisplayName("promedia solo las reviews visibles del producto")
        void averagesOnlyVisibleReviews() {
            ConsumerDetails consumerA = TestEntities.persistConsumer(em);
            ConsumerDetails consumerB = TestEntities.persistConsumer(em);
            ConsumerDetails consumerC = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Product product = persistProduct(commercial, persistCategory());

            persistReview(consumerA, product, 4, true);
            persistReview(consumerB, product, 2, true);
            persistReview(consumerC, product, 1, false); // oculta, no debe contar

            Double avg = productReviewRepository.productAvgRating(product.getId());

            assertThat(avg).isCloseTo(3.0, within(0.0001));
        }

        @Test
        @DisplayName("retorna null si el producto no tiene reviews")
        void returnsNullWhenNoReviews() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Product product = persistProduct(commercial, persistCategory());

            assertThat(productReviewRepository.productAvgRating(product.getId())).isNull();
        }
    }

    // ==================== productReviewCount ====================

    @Nested
    @DisplayName("productReviewCount")
    class ProductReviewCount {

        @Test
        @DisplayName("cuenta solo las reviews visibles del producto")
        void countsOnlyVisibleReviews() {
            ConsumerDetails consumerA = TestEntities.persistConsumer(em);
            ConsumerDetails consumerB = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Product product = persistProduct(commercial, persistCategory());

            persistReview(consumerA, product, 5, true);
            persistReview(consumerB, product, 3, false);

            assertThat(productReviewRepository.productReviewCount(product.getId())).isEqualTo(1);
        }
    }

    // ==================== commercialAvgRating ====================

    @Nested
    @DisplayName("commercialAvgRating")
    class CommercialAvgRating {

        @Test
        @DisplayName("promedia solo las reviews visibles entre todos los productos del comercial")
        void averagesOnlyVisibleReviewsAcrossProducts() {
            ConsumerDetails consumerA = TestEntities.persistConsumer(em);
            ConsumerDetails consumerB = TestEntities.persistConsumer(em);
            ConsumerDetails consumerC = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory();
            Product productOne = persistProduct(commercial, category);
            Product productTwo = persistProduct(commercial, category);

            persistReview(consumerA, productOne, 5, true);
            persistReview(consumerB, productTwo, 3, true);
            persistReview(consumerC, productTwo, 1, false); // oculta, no cuenta

            Double avg = productReviewRepository.commercialAvgRating(commercial.getId());

            assertThat(avg).isCloseTo(4.0, within(0.0001));
        }
    }

    // ==================== commercialReviewCount ====================

    @Nested
    @DisplayName("commercialReviewCount")
    class CommercialReviewCount {

        @Test
        @DisplayName("cuenta solo las reviews visibles entre todos los productos del comercial")
        void countsOnlyVisibleReviewsAcrossProducts() {
            ConsumerDetails consumerA = TestEntities.persistConsumer(em);
            ConsumerDetails consumerB = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory();
            Product productOne = persistProduct(commercial, category);
            Product productTwo = persistProduct(commercial, category);

            persistReview(consumerA, productOne, 5, true);
            persistReview(consumerB, productTwo, 2, false);

            assertThat(productReviewRepository.commercialReviewCount(commercial.getId())).isEqualTo(1);
        }
    }

    // ==================== getProductReviewByProductId ====================

    @Nested
    @DisplayName("getProductReviewByProductId")
    class GetProductReviewByProductId {

        @Test
        @DisplayName("trae todas las reviews del producto (visibles y ocultas), paginadas")
        void returnsAllReviewsRegardlessOfVisibility() {
            ConsumerDetails consumerA = TestEntities.persistConsumer(em);
            ConsumerDetails consumerB = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Product product = persistProduct(commercial, persistCategory());

            ProductReview visible = persistReview(consumerA, product, 5, true);
            ProductReview hidden = persistReview(consumerB, product, 1, false);

            Page<ProductReview> found = productReviewRepository.getProductReviewByProductId(
                    product.getId(), PageRequest.of(0, 10));

            assertThat(found.getContent()).extracting(ProductReview::getId)
                    .containsExactlyInAnyOrder(visible.getId(), hidden.getId());
        }
    }

    // ==================== existsByConsumerIdAndProductId ====================

    @Nested
    @DisplayName("existsByConsumerIdAndProductId")
    class ExistsByConsumerIdAndProductId {

        @Test
        @DisplayName("detecta si el consumer ya dejó review de ese producto")
        void detectsExistingReview() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            ConsumerDetails otherConsumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Product product = persistProduct(commercial, persistCategory());

            persistReview(consumer, product, 4, true);

            assertThat(productReviewRepository.existsByConsumerIdAndProductId(consumer.getId(), product.getId()))
                    .isTrue();
            assertThat(productReviewRepository.existsByConsumerIdAndProductId(otherConsumer.getId(), product.getId()))
                    .isFalse();
        }
    }
}
