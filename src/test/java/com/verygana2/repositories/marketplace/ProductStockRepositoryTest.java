package com.verygana2.repositories.marketplace;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneOffset;
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

import com.verygana2.models.enums.marketplace.ProductStatus;
import com.verygana2.models.enums.marketplace.StockStatus;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductCategory;
import com.verygana2.models.marketplace.ProductStock;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para ProductStockRepository.
 *
 * NOTA: findNextAvailableForProduct() usa "FOR UPDATE SKIP LOCKED", una
 * cláusula específica de MySQL/PostgreSQL que H2 no soporta (ni siquiera en
 * MODE=MySQL) — lanza una excepción de sintaxis al ejecutarla. Ese método no
 * se testea aquí; requeriría Testcontainers con MySQL real.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:product-stock-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("ProductStockRepository (integración H2)")
class ProductStockRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private ProductStockRepository productStockRepository;

    private static final AtomicLong SEQ = new AtomicLong(1);

    // ==================== HELPERS ====================

    private ProductCategory persistCategory() {
        long n = SEQ.getAndIncrement();
        ProductCategory category = new ProductCategory();
        category.setName("categoria-stock-" + n);
        em.persist(category);
        em.flush();
        return category;
    }

    private Product persistProduct(CommercialDetails commercial, ProductCategory category) {
        long n = SEQ.getAndIncrement();
        Product product = new Product();
        product.setCommercial(commercial);
        product.setProductCategory(category);
        product.setName("Producto stock " + n);
        product.setPriceCents(100_000L);
        product.setMaxKeysPct(20);
        em.persist(product);
        em.flush();
        return product;
    }

    private ProductStock persistStock(Product product, StockStatus status, String codeHash) {
        return persistStock(product, status, codeHash, null, null);
    }

    private ProductStock persistStock(Product product, StockStatus status, String codeHash,
            ZonedDateTime expirationDate, ZonedDateTime soldAt) {
        ProductStock stock = new ProductStock();
        stock.setProduct(product);
        stock.setCode("cipher-" + codeHash);
        stock.setCodeHash(codeHash);
        stock.setStatus(status);
        stock.setExpirationDate(expirationDate);
        stock.setSoldAt(soldAt);
        em.persist(stock);
        em.flush();
        return stock;
    }

    private static String uniqueHash() {
        return "hash-" + SEQ.getAndIncrement();
    }

    // ==================== findByIdWithLock ====================

    @Nested
    @DisplayName("findByIdWithLock")
    class FindByIdWithLock {

        @Test
        @DisplayName("recupera el stock con lock optimista (happy path de lectura)")
        void returnsStockWithOptimisticLock() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Product product = persistProduct(commercial, persistCategory());
            ProductStock stock = persistStock(product, StockStatus.AVAILABLE, uniqueHash());

            Optional<ProductStock> found = productStockRepository.findByIdWithLock(stock.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(stock.getId());
            assertThat(found.get().getVersion()).isNotNull();
        }

        @Test
        @DisplayName("retorna vacío si el id no existe")
        void returnsEmptyWhenNotFound() {
            Optional<ProductStock> found = productStockRepository.findByIdWithLock(999_999L);

            assertThat(found).isEmpty();
        }
    }

    // ==================== findByProductIdWithFilters ====================

    @Nested
    @DisplayName("findByProductIdWithFilters")
    class FindByProductIdWithFilters {

        @Test
        @DisplayName("sin filtros (status y soldDate null) trae todos los stocks del producto")
        void noFiltersReturnsAll() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Product product = persistProduct(commercial, persistCategory());
            persistStock(product, StockStatus.AVAILABLE, uniqueHash());
            persistStock(product, StockStatus.SOLD, uniqueHash());

            Product otherProduct = persistProduct(commercial, persistCategory());
            persistStock(otherProduct, StockStatus.AVAILABLE, uniqueHash());

            Page<ProductStock> found = productStockRepository.findByProductIdWithFilters(
                    product.getId(), null, null, PageRequest.of(0, 10));

            assertThat(found.getTotalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("filtra por status cuando se especifica")
        void filtersByStatus() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Product product = persistProduct(commercial, persistCategory());
            ProductStock available = persistStock(product, StockStatus.AVAILABLE, uniqueHash());
            persistStock(product, StockStatus.SOLD, uniqueHash());

            Page<ProductStock> found = productStockRepository.findByProductIdWithFilters(
                    product.getId(), StockStatus.AVAILABLE, null, PageRequest.of(0, 10));

            assertThat(found.getContent()).extracting(ProductStock::getId).containsExactly(available.getId());
        }

        @Test
        @DisplayName("filtra por soldDate cuando se especifica")
        void filtersBySoldDate() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Product product = persistProduct(commercial, persistCategory());
            ZonedDateTime soldToday = ZonedDateTime.now(ZoneOffset.UTC);
            ProductStock soldTodayStock = persistStock(product, StockStatus.SOLD, uniqueHash(), null, soldToday);
            persistStock(product, StockStatus.SOLD, uniqueHash(), null, soldToday.minusDays(5));

            Page<ProductStock> found = productStockRepository.findByProductIdWithFilters(
                    product.getId(), null, soldToday.toLocalDate(), PageRequest.of(0, 10));

            assertThat(found.getContent()).extracting(ProductStock::getId)
                    .containsExactly(soldTodayStock.getId());
        }

        @Test
        @DisplayName("no trae nada si soldDate no coincide con ningún stock")
        void returnsEmptyWhenSoldDateDoesNotMatch() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Product product = persistProduct(commercial, persistCategory());
            persistStock(product, StockStatus.AVAILABLE, uniqueHash());

            Page<ProductStock> found = productStockRepository.findByProductIdWithFilters(
                    product.getId(), null, LocalDate.now().minusYears(5), PageRequest.of(0, 10));

            assertThat(found.getContent()).isEmpty();
        }
    }

    // ==================== findByIdAndProductIdAndProductCommercialId ====================

    @Nested
    @DisplayName("findByIdAndProductIdAndProductCommercialId")
    class FindByIdAndProductIdAndProductCommercialId {

        @Test
        @DisplayName("encuentra el stock cuando product y commercial coinciden")
        void findsStockWhenMatches() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Product product = persistProduct(commercial, persistCategory());
            ProductStock stock = persistStock(product, StockStatus.AVAILABLE, uniqueHash());

            Optional<ProductStock> found = productStockRepository.findByIdAndProductIdAndProductCommercialId(
                    stock.getId(), product.getId(), commercial.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(stock.getId());
        }

        @Test
        @DisplayName("no encuentra el stock si el commercial no es el dueño del producto")
        void returnsEmptyWhenCommercialMismatch() {
            CommercialDetails owner = TestEntities.persistCommercial(em);
            CommercialDetails other = TestEntities.persistCommercial(em);
            Product product = persistProduct(owner, persistCategory());
            ProductStock stock = persistStock(product, StockStatus.AVAILABLE, uniqueHash());

            Optional<ProductStock> found = productStockRepository.findByIdAndProductIdAndProductCommercialId(
                    stock.getId(), product.getId(), other.getId());

            assertThat(found).isEmpty();
        }
    }

    // ==================== existsByProductIdAndCodeHash ====================

    @Nested
    @DisplayName("existsByProductIdAndCodeHash")
    class ExistsByProductIdAndCodeHash {

        @Test
        @DisplayName("detecta un codeHash existente para el producto")
        void detectsExistingCodeHash() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Product product = persistProduct(commercial, persistCategory());
            String hash = uniqueHash();
            persistStock(product, StockStatus.AVAILABLE, hash);

            assertThat(productStockRepository.existsByProductIdAndCodeHash(product.getId(), hash)).isTrue();
            assertThat(productStockRepository.existsByProductIdAndCodeHash(product.getId(), "no-existe")).isFalse();
        }
    }

    // ==================== findExistingCodeHashes ====================

    @Nested
    @DisplayName("findExistingCodeHashes")
    class FindExistingCodeHashes {

        @Test
        @DisplayName("retorna solo los codeHashes que ya existen para el producto")
        void returnsOnlyMatchingHashes() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Product product = persistProduct(commercial, persistCategory());
            String existingHash = uniqueHash();
            persistStock(product, StockStatus.AVAILABLE, existingHash);

            List<String> found = productStockRepository.findExistingCodeHashes(
                    product.getId(), List.of(existingHash, "hash-inexistente"));

            assertThat(found).containsExactly(existingHash);
        }
    }

    // ==================== countByProductIdAndStatus ====================

    @Nested
    @DisplayName("countByProductIdAndStatus")
    class CountByProductIdAndStatus {

        @Test
        @DisplayName("cuenta solo los stocks del producto con el status pedido")
        void countsOnlyMatchingStatus() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Product product = persistProduct(commercial, persistCategory());
            persistStock(product, StockStatus.AVAILABLE, uniqueHash());
            persistStock(product, StockStatus.AVAILABLE, uniqueHash());
            persistStock(product, StockStatus.SOLD, uniqueHash());

            Integer count = productStockRepository.countByProductIdAndStatus(product.getId(), StockStatus.AVAILABLE);

            assertThat(count).isEqualTo(2);
        }
    }
}
