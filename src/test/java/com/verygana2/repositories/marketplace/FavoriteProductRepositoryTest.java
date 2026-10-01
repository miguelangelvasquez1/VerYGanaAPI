package com.verygana2.repositories.marketplace;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.verygana2.models.marketplace.FavoriteProduct;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductCategory;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para FavoriteProductRepository.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:favorite-product-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("FavoriteProductRepository (integración H2)")
class FavoriteProductRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private FavoriteProductRepository favoriteProductRepository;

    private static final AtomicLong SEQ = new AtomicLong(1);

    // ==================== HELPERS ====================

    private ProductCategory persistCategory() {
        long n = SEQ.getAndIncrement();
        ProductCategory category = new ProductCategory();
        category.setName("categoria-fav-" + n);
        em.persist(category);
        em.flush();
        return category;
    }

    /**
     * Persiste un producto con el status pedido. El @PrePersist de Product
     * fuerza status=PENDING al crear, así que si se pide otro status se hace
     * un segundo flush actualizándolo.
     */
    private Product persistProduct(CommercialDetails commercial, ProductCategory category, ProductStatus status) {
        long n = SEQ.getAndIncrement();
        Product product = new Product();
        product.setCommercial(commercial);
        product.setProductCategory(category);
        product.setName("Producto favorito " + n);
        product.setPriceCents(75_000L);
        product.setMaxKeysPct(20);
        em.persist(product);
        em.flush();

        if (status != ProductStatus.PENDING) {
            product.setStatus(status);
            em.flush();
        }
        return product;
    }

    private FavoriteProduct persistFavorite(ConsumerDetails consumer, Product product) {
        FavoriteProduct favorite = new FavoriteProduct();
        favorite.setConsumer(consumer);
        favorite.setProduct(product);
        em.persist(favorite);
        em.flush();
        return favorite;
    }

    // ==================== findByConsumerIdWithActiveProducts ====================

    @Nested
    @DisplayName("findByConsumerIdWithActiveProducts")
    class FindByConsumerIdWithActiveProducts {

        @Test
        @DisplayName("trae solo favoritos de productos ACTIVE, ordenados por createdAt descendente")
        void returnsOnlyActiveProductFavoritesOrderedByCreatedAtDesc() throws InterruptedException {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory();

            Product activeProductOne = persistProduct(commercial, category, ProductStatus.ACTIVE);
            FavoriteProduct favoriteOne = persistFavorite(consumer, activeProductOne);

            Thread.sleep(5); // asegura createdAt distinto para el orden desc

            Product activeProductTwo = persistProduct(commercial, category, ProductStatus.ACTIVE);
            FavoriteProduct favoriteTwo = persistFavorite(consumer, activeProductTwo);

            Product inactiveProduct = persistProduct(commercial, category, ProductStatus.INACTIVE);
            persistFavorite(consumer, inactiveProduct);

            Page<FavoriteProduct> found = favoriteProductRepository.findByConsumerIdWithActiveProducts(
                    consumer.getId(), PageRequest.of(0, 10));

            assertThat(found.getContent()).extracting(FavoriteProduct::getId)
                    .containsExactly(favoriteTwo.getId(), favoriteOne.getId());
        }

        @Test
        @DisplayName("retorna vacío si el consumer no tiene favoritos de productos activos")
        void returnsEmptyWhenNoActiveFavorites() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Product inactiveProduct = persistProduct(commercial, persistCategory(), ProductStatus.INACTIVE);
            persistFavorite(consumer, inactiveProduct);

            Page<FavoriteProduct> found = favoriteProductRepository.findByConsumerIdWithActiveProducts(
                    consumer.getId(), PageRequest.of(0, 10));

            assertThat(found.getContent()).isEmpty();
        }
    }

    // ==================== existsByConsumerIdAndProductId ====================

    @Nested
    @DisplayName("existsByConsumerIdAndProductId")
    class ExistsByConsumerIdAndProductId {

        @Test
        @DisplayName("detecta si el consumer ya marcó el producto como favorito")
        void detectsExistingFavorite() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Product product = persistProduct(commercial, persistCategory(), ProductStatus.ACTIVE);
            persistFavorite(consumer, product);

            assertThat(favoriteProductRepository.existsByConsumerIdAndProductId(consumer.getId(), product.getId()))
                    .isTrue();

            ConsumerDetails otherConsumer = TestEntities.persistConsumer(em);
            assertThat(favoriteProductRepository.existsByConsumerIdAndProductId(otherConsumer.getId(), product.getId()))
                    .isFalse();
        }
    }

    // ==================== findByConsumerIdAndProductId ====================

    @Nested
    @DisplayName("findByConsumerIdAndProductId")
    class FindByConsumerIdAndProductId {

        @Test
        @DisplayName("encuentra el favorito exacto de ese consumer y producto")
        void findsExactFavorite() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Product product = persistProduct(commercial, persistCategory(), ProductStatus.ACTIVE);
            FavoriteProduct favorite = persistFavorite(consumer, product);

            Optional<FavoriteProduct> found = favoriteProductRepository.findByConsumerIdAndProductId(
                    consumer.getId(), product.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(favorite.getId());
        }

        @Test
        @DisplayName("retorna vacío si no existe ese favorito")
        void returnsEmptyWhenNoFavorite() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Product product = persistProduct(commercial, persistCategory(), ProductStatus.ACTIVE);

            Optional<FavoriteProduct> found = favoriteProductRepository.findByConsumerIdAndProductId(
                    consumer.getId(), product.getId());

            assertThat(found).isEmpty();
        }
    }

    // ==================== countByConsumerId ====================

    @Nested
    @DisplayName("countByConsumerId")
    class CountByConsumerId {

        @Test
        @DisplayName("cuenta solo los favoritos de productos ACTIVE del consumer")
        void countsOnlyActiveProductFavorites() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory();

            Product activeProduct = persistProduct(commercial, category, ProductStatus.ACTIVE);
            persistFavorite(consumer, activeProduct);

            Product inactiveProduct = persistProduct(commercial, category, ProductStatus.INACTIVE);
            persistFavorite(consumer, inactiveProduct);

            assertThat(favoriteProductRepository.countByConsumerId(consumer.getId())).isEqualTo(1L);
        }
    }
}
