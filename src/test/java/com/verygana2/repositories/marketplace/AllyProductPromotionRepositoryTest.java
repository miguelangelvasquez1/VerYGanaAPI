package com.verygana2.repositories.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.dao.DataIntegrityViolationException;

import com.verygana2.models.enums.marketplace.ProductStatus;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.marketplace.AllyProductPromotion;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductCategory;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

/**
 * Tests de integración H2 (modo MySQL) para AllyProductPromotionRepository.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:ally-product-promotion-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("AllyProductPromotionRepository (integración H2)")
class AllyProductPromotionRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private AllyProductPromotionRepository allyProductPromotionRepository;

    private static final AtomicLong SEQ = new AtomicLong(1);

    // ==================== HELPERS ====================

    private ProductCategory persistCategory() {
        long n = SEQ.getAndIncrement();
        ProductCategory category = new ProductCategory();
        category.setName("categoria-ally-" + n);
        em.persist(category);
        em.flush();
        return category;
    }

    /**
     * Persiste un producto con el status pedido. El @PrePersist de Product
     * fuerza status=PENDING al crear, así que si se pide otro status se hace
     * un segundo flush actualizándolo.
     */
    private Product persistProduct(CommercialDetails commercial, ProductStatus status) {
        long n = SEQ.getAndIncrement();
        Product product = new Product();
        product.setCommercial(commercial);
        product.setProductCategory(persistCategory());
        product.setName("Producto aliado " + n);
        product.setPriceCents(80_000L);
        product.setMaxKeysPct(20);
        em.persist(product);
        em.flush();

        if (status != ProductStatus.PENDING) {
            product.setStatus(status);
            em.flush();
        }
        return product;
    }

    /**
     * Persiste la promoción. El @PrePersist siempre fuerza createdAt=now();
     * si se pide un instante distinto (para testear orden desc de forma
     * determinística) se sobrescribe con un segundo flush.
     */
    private AllyProductPromotion persistPromotion(CommercialDetails premiumCommercial, Product product,
            ZonedDateTime createdAt) {
        AllyProductPromotion promotion = new AllyProductPromotion();
        promotion.setPremiumCommercial(premiumCommercial);
        promotion.setProduct(product);
        em.persist(promotion);
        em.flush();

        if (createdAt != null) {
            promotion.setCreatedAt(createdAt);
            em.flush();
        }
        return promotion;
    }

    private AllyProductPromotion persistPromotion(CommercialDetails premiumCommercial, Product product) {
        return persistPromotion(premiumCommercial, product, null);
    }

    // ==================== findByPremiumCommercial_IdAndProduct_Id ====================

    @Nested
    @DisplayName("findByPremiumCommercial_IdAndProduct_Id")
    class FindByPremiumCommercialIdAndProductId {

        @Test
        @DisplayName("encuentra la promoción exacta de ese premium y ese producto")
        void findsExactPromotion() {
            CommercialDetails premium = TestEntities.persistCommercial(em, PlanCode.PREMIUM);
            CommercialDetails ally = TestEntities.persistCommercial(em, PlanCode.BASIC);
            Product product = persistProduct(ally, ProductStatus.ACTIVE);
            AllyProductPromotion promotion = persistPromotion(premium, product);

            Optional<AllyProductPromotion> found = allyProductPromotionRepository
                    .findByPremiumCommercial_IdAndProduct_Id(premium.getId(), product.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(promotion.getId());
        }

        @Test
        @DisplayName("retorna vacío si no existe esa combinación premium/producto")
        void returnsEmptyWhenNoMatch() {
            CommercialDetails premium = TestEntities.persistCommercial(em, PlanCode.PREMIUM);
            CommercialDetails ally = TestEntities.persistCommercial(em, PlanCode.BASIC);
            Product product = persistProduct(ally, ProductStatus.ACTIVE);

            Optional<AllyProductPromotion> found = allyProductPromotionRepository
                    .findByPremiumCommercial_IdAndProduct_Id(premium.getId(), product.getId());

            assertThat(found).isEmpty();
        }
    }

    // ==================== countByPremiumCommercial_Id ====================

    @Nested
    @DisplayName("countByPremiumCommercial_Id")
    class CountByPremiumCommercialId {

        @Test
        @DisplayName("cuenta las promociones de ese premium")
        void countsPromotionsOfPremium() {
            CommercialDetails premium = TestEntities.persistCommercial(em, PlanCode.PREMIUM);
            CommercialDetails otherPremium = TestEntities.persistCommercial(em, PlanCode.PREMIUM);
            CommercialDetails ally = TestEntities.persistCommercial(em, PlanCode.BASIC);

            persistPromotion(premium, persistProduct(ally, ProductStatus.ACTIVE));
            persistPromotion(premium, persistProduct(ally, ProductStatus.ACTIVE));
            persistPromotion(otherPremium, persistProduct(ally, ProductStatus.ACTIVE));

            assertThat(allyProductPromotionRepository.countByPremiumCommercial_Id(premium.getId())).isEqualTo(2L);
        }
    }

    // ==================== findByPremiumCommercialIdOrderByCreatedAtDesc ====================

    @Nested
    @DisplayName("findByPremiumCommercialIdOrderByCreatedAtDesc")
    class FindByPremiumCommercialIdOrderByCreatedAtDesc {

        @Test
        @DisplayName("trae las promociones del premium ordenadas por createdAt descendente, con el producto cargado")
        void returnsPromotionsOrderedByCreatedAtDescWithProductFetched() {
            CommercialDetails premium = TestEntities.persistCommercial(em, PlanCode.PREMIUM);
            CommercialDetails ally = TestEntities.persistCommercial(em, PlanCode.BASIC);
            ZonedDateTime base = ZonedDateTime.now(ZoneOffset.UTC);

            AllyProductPromotion older = persistPromotion(premium, persistProduct(ally, ProductStatus.ACTIVE),
                    base.minusDays(2));
            AllyProductPromotion newer = persistPromotion(premium, persistProduct(ally, ProductStatus.ACTIVE),
                    base.minusHours(1));

            CommercialDetails otherPremium = TestEntities.persistCommercial(em, PlanCode.PREMIUM);
            persistPromotion(otherPremium, persistProduct(ally, ProductStatus.ACTIVE));

            List<AllyProductPromotion> found = allyProductPromotionRepository
                    .findByPremiumCommercialIdOrderByCreatedAtDesc(premium.getId());

            assertThat(found).extracting(AllyProductPromotion::getId)
                    .containsExactly(newer.getId(), older.getId());
            assertThat(found).extracting(p -> p.getProduct().getName())
                    .containsExactly(newer.getProduct().getName(), older.getProduct().getName());
        }
    }

    // ==================== findPromotedActiveProducts ====================

    @Nested
    @DisplayName("findPromotedActiveProducts")
    class FindPromotedActiveProducts {

        @Test
        @DisplayName("trae solo los productos promocionados con status ACTIVE")
        void returnsOnlyActivePromotedProducts() {
            CommercialDetails premium = TestEntities.persistCommercial(em, PlanCode.PREMIUM);
            CommercialDetails ally = TestEntities.persistCommercial(em, PlanCode.BASIC);

            Product activeProduct = persistProduct(ally, ProductStatus.ACTIVE);
            persistPromotion(premium, activeProduct);

            Product inactiveProduct = persistProduct(ally, ProductStatus.INACTIVE);
            persistPromotion(premium, inactiveProduct);

            List<Product> found = allyProductPromotionRepository.findPromotedActiveProducts(premium.getId());

            assertThat(found).extracting(Product::getId).containsExactly(activeProduct.getId());
        }
    }

    // ==================== findDistinctAlliesOfPremium ====================

    @Nested
    @DisplayName("findDistinctAlliesOfPremium")
    class FindDistinctAlliesOfPremium {

        @Test
        @DisplayName("trae los comerciales aliados distintos, sin duplicar cuando promociona varios productos del mismo aliado")
        void returnsDistinctAllies() {
            CommercialDetails premium = TestEntities.persistCommercial(em, PlanCode.PREMIUM);
            CommercialDetails allyOne = TestEntities.persistCommercial(em, PlanCode.BASIC);
            CommercialDetails allyTwo = TestEntities.persistCommercial(em, PlanCode.STANDARD);

            // Dos productos del mismo aliado: no debe duplicarse en el resultado.
            persistPromotion(premium, persistProduct(allyOne, ProductStatus.ACTIVE));
            persistPromotion(premium, persistProduct(allyOne, ProductStatus.ACTIVE));
            persistPromotion(premium, persistProduct(allyTwo, ProductStatus.ACTIVE));

            List<CommercialDetails> found = allyProductPromotionRepository.findDistinctAlliesOfPremium(premium.getId());

            assertThat(found).extracting(CommercialDetails::getId)
                    .containsExactlyInAnyOrder(allyOne.getId(), allyTwo.getId());
        }
    }

    // ==================== findDistinctPromotersOfCommercial ====================

    @Nested
    @DisplayName("findDistinctPromotersOfCommercial")
    class FindDistinctPromotersOfCommercial {

        @Test
        @DisplayName("trae los comerciales premium distintos que promocionan productos de ese comercial")
        void returnsDistinctPromoters() {
            CommercialDetails ally = TestEntities.persistCommercial(em, PlanCode.BASIC);
            CommercialDetails premiumOne = TestEntities.persistCommercial(em, PlanCode.PREMIUM);
            CommercialDetails premiumTwo = TestEntities.persistCommercial(em, PlanCode.PREMIUM);

            Product productOne = persistProduct(ally, ProductStatus.ACTIVE);
            Product productTwo = persistProduct(ally, ProductStatus.ACTIVE);

            persistPromotion(premiumOne, productOne);
            persistPromotion(premiumTwo, productTwo);

            List<CommercialDetails> found = allyProductPromotionRepository
                    .findDistinctPromotersOfCommercial(ally.getId());

            assertThat(found).extracting(CommercialDetails::getId)
                    .containsExactlyInAnyOrder(premiumOne.getId(), premiumTwo.getId());
        }
    }

    // ==================== unique constraint (premium_commercial_id, product_id) ====================

    @Nested
    @DisplayName("restricción única (premiumCommercial, product)")
    class UniqueConstraint {

        @Test
        @DisplayName("lanza DataIntegrityViolationException al persistir dos promociones con la misma combinación")
        void throwsOnDuplicateCombination() {
            CommercialDetails premium = TestEntities.persistCommercial(em, PlanCode.PREMIUM);
            CommercialDetails ally = TestEntities.persistCommercial(em, PlanCode.BASIC);
            Product product = persistProduct(ally, ProductStatus.ACTIVE);

            persistPromotion(premium, product);

            // AllyProductPromotion.id usa GenerationType.IDENTITY: el INSERT se
            // ejecuta de inmediato en persist(), no se difiere hasta flush().
            assertThatThrownBy(() -> {
                AllyProductPromotion duplicate = new AllyProductPromotion();
                duplicate.setPremiumCommercial(premium);
                duplicate.setProduct(product);
                em.persist(duplicate);
                em.flush();
            }).isInstanceOfAny(DataIntegrityViolationException.class, PersistenceException.class);
        }
    }
}
