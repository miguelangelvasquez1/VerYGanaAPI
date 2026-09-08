package com.verygana2.repositories.marketplace;

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

import com.verygana2.dtos.product.responses.FeaturedProductResponseDTO;
import com.verygana2.dtos.user.commercial.responses.DailySaleResponseDTO;
import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.enums.DocumentType;
import com.verygana2.models.enums.finance.PayoutStatus;
import com.verygana2.models.enums.marketplace.ProductStatus;
import com.verygana2.models.enums.marketplace.ProductType;
import com.verygana2.models.enums.marketplace.PurchaseItemStatus;
import com.verygana2.models.enums.marketplace.PurchaseStatus;
import com.verygana2.models.enums.marketplace.StockStatus;
import com.verygana2.models.enums.pqrs.PqrsStatus;
import com.verygana2.models.enums.pqrs.PqrsType;
import com.verygana2.models.finance.Payout;
import com.verygana2.models.finance.PayoutItem;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductCategory;
import com.verygana2.models.marketplace.ProductImageAsset;
import com.verygana2.models.marketplace.ProductReview;
import com.verygana2.models.marketplace.ProductStock;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.pqrs.Pqrs;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para PurchaseItemRepository — el
 * repositorio más grande del dominio marketplace. Cubre reviews pendientes,
 * ownership, reportes de ventas/comisiones del comercial (incluyendo los dos
 * bugs documentados en el repo: conteo por snapshot commercialId con producto
 * purgado, y suma de comisiones sin truncar), catálogo de más vendidos
 * (actual vs. histórico), reclamos físicos pendientes/expirados, el
 * desvinculado masivo de producto y la elegibilidad para payout.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:purchase-item-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("PurchaseItemRepository (integración H2)")
class PurchaseItemRepositoryTest {

    private static final AtomicLong SEQ = new AtomicLong(1);

    @Autowired
    private EntityManager em;

    @Autowired
    private PurchaseItemRepository purchaseItemRepository;

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

    /** Product.onCreate() fuerza status=PENDING; se actualiza con un segundo flush si hace falta otro status. */
    private Product persistProduct(CommercialDetails commercial, ProductCategory category, String name,
            long priceCents, ProductStatus status, ProductType type) {
        Product product = new Product();
        product.setCommercial(commercial);
        product.setProductCategory(category);
        product.setName(name);
        product.setDescription("Descripción de " + name);
        product.setPriceCents(priceCents);
        product.setMaxKeysPct(20);
        if (type != null) {
            product.setProductType(type);
        }
        em.persist(product);
        em.flush();

        if (status != null && status != ProductStatus.PENDING) {
            product.setStatus(status);
            em.flush();
        }
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

    private Purchase persistPurchase(ConsumerDetails consumer) {
        long n = SEQ.getAndIncrement();
        Purchase purchase = new Purchase();
        purchase.setReferenceId("REF-" + n);
        purchase.setConsumer(consumer);
        purchase.setStatus(PurchaseStatus.COMPLETED);
        purchase.setTotalCents(10000L);
        purchase.setKeysValueCents(0L);
        purchase.setCashCents(10000L);
        purchase.setCommissionCents(1000L);
        purchase.setNetToCommercialsCents(9000L);
        em.persist(purchase);
        em.flush();
        return purchase;
    }

    /** Builder base con los NOT NULL cubiertos; cada test ajusta status/deliveredAt/montos según el caso. */
    private PurchaseItem.PurchaseItemBuilder baseItem(Purchase purchase, Product product, Long commercialId) {
        return PurchaseItem.builder()
                .purchase(purchase)
                .product(product)
                .productNameSnapshot(product != null ? product.getName() : "Producto purgado")
                .commercialId(commercialId)
                .unitPriceCents(10000L)
                .subtotalCents(10000L)
                .maxKeysPctAtPurchase(20)
                .createdAt(now());
    }

    private PurchaseItem persist(PurchaseItem item) {
        em.persist(item);
        em.flush();
        return item;
    }

    private ProductReview persistReview(ConsumerDetails consumer, Product product, PurchaseItem item) {
        ProductReview review = ProductReview.builder()
                .consumer(consumer)
                .product(product)
                .purchaseItem(item)
                .rating(5)
                .comment("Excelente producto")
                .build();
        em.persist(review);
        em.flush();
        return review;
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

    private PayoutItem persistPayoutItem(Payout payout, PurchaseItem item) {
        PayoutItem payoutItem = PayoutItem.builder()
                .payout(payout)
                .purchaseItem(item)
                .amountCents(9000L)
                .build();
        em.persist(payoutItem);
        em.flush();
        return payoutItem;
    }

    private Pqrs persistPqrs(ConsumerDetails consumer, PurchaseItem item, PqrsStatus status) {
        Pqrs pqrs = Pqrs.builder()
                .type(PqrsType.RECLAMO)
                .status(status)
                .requester(consumer.getUser())
                .subject("Problema con el producto")
                .description("Descripción del problema reportado")
                .dueDate(now().plusDays(5))
                .purchaseItem(item)
                .build();
        em.persist(pqrs);
        em.flush();
        return pqrs;
    }

    // ==================== findDeliveredItemsWithoutReview ====================

    @Nested
    @DisplayName("findDeliveredItemsWithoutReview")
    class FindDeliveredItemsWithoutReview {

        @Test
        @DisplayName("trae ítems CLAIMED sin review del consumer, ordenados por deliveredAt DESC")
        void returnsClaimedItemsWithoutReviewOrderedByDeliveredAtDesc() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria sin review");
            Product product = persistProduct(commercial, category, "Producto sin review", 10000,
                    ProductStatus.ACTIVE, null);
            Purchase purchase = persistPurchase(consumer);

            PurchaseItem older = persist(baseItem(purchase, product, commercial.getId())
                    .status(PurchaseItemStatus.CLAIMED).deliveredAt(now().minusDays(3)).build());
            PurchaseItem newer = persist(baseItem(purchase, product, commercial.getId())
                    .status(PurchaseItemStatus.CLAIMED).deliveredAt(now()).build());

            List<PurchaseItem> result = purchaseItemRepository.findDeliveredItemsWithoutReview(consumer.getId());

            assertThat(result).extracting(PurchaseItem::getId).containsExactly(newer.getId(), older.getId());
        }

        @Test
        @DisplayName("excluye ítems ya reseñados, PENDING, y de otros consumers")
        void excludesReviewedPendingAndOtherConsumers() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            ConsumerDetails other = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria exclusiones review");
            Product product = persistProduct(commercial, category, "Producto exclusiones review", 10000,
                    ProductStatus.ACTIVE, null);

            Purchase purchase = persistPurchase(consumer);
            PurchaseItem reviewed = persist(baseItem(purchase, product, commercial.getId())
                    .status(PurchaseItemStatus.CLAIMED).deliveredAt(now()).build());
            persistReview(consumer, product, reviewed);

            persist(baseItem(purchase, product, commercial.getId())
                    .status(PurchaseItemStatus.PENDING).deliveredAt(now()).build());

            Purchase otherPurchase = persistPurchase(other);
            persist(baseItem(otherPurchase, product, commercial.getId())
                    .status(PurchaseItemStatus.CLAIMED).deliveredAt(now()).build());

            List<PurchaseItem> result = purchaseItemRepository.findDeliveredItemsWithoutReview(consumer.getId());

            assertThat(result).isEmpty();
        }
    }

    // ==================== canUserReviewPurchaseItem ====================

    @Nested
    @DisplayName("canUserReviewPurchaseItem")
    class CanUserReviewPurchaseItem {

        @Test
        @DisplayName("true solo cuando el ítem es CLAIMED, sin review, y pertenece al consumer dado")
        void trueOnlyForClaimedUnreviewedOwnedItem() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            ConsumerDetails other = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria can review");
            Product product = persistProduct(commercial, category, "Producto can review", 10000,
                    ProductStatus.ACTIVE, null);
            Purchase purchase = persistPurchase(consumer);

            PurchaseItem claimable = persist(baseItem(purchase, product, commercial.getId())
                    .status(PurchaseItemStatus.CLAIMED).deliveredAt(now()).build());

            assertThat(purchaseItemRepository.canUserReviewPurchaseItem(claimable.getId(), consumer.getId()))
                    .isTrue();
            assertThat(purchaseItemRepository.canUserReviewPurchaseItem(claimable.getId(), other.getId()))
                    .isFalse();

            PurchaseItem pending = persist(baseItem(purchase, product, commercial.getId())
                    .status(PurchaseItemStatus.PENDING).deliveredAt(now()).build());
            assertThat(purchaseItemRepository.canUserReviewPurchaseItem(pending.getId(), consumer.getId()))
                    .isFalse();

            PurchaseItem alreadyReviewed = persist(baseItem(purchase, product, commercial.getId())
                    .status(PurchaseItemStatus.CLAIMED).deliveredAt(now()).build());
            persistReview(consumer, product, alreadyReviewed);
            assertThat(purchaseItemRepository.canUserReviewPurchaseItem(alreadyReviewed.getId(), consumer.getId()))
                    .isFalse();
        }
    }

    // ==================== findByIdAndConsumerId ====================

    @Nested
    @DisplayName("findByIdAndConsumerId")
    class FindByIdAndConsumerId {

        @Test
        @DisplayName("trae el ítem con product y purchase fetch-eados solo para el consumer dueño")
        void fetchesItemWithProductAndPurchaseForOwningConsumer() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            ConsumerDetails other = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria find by id consumer");
            Product product = persistProduct(commercial, category, "Producto find by id consumer", 10000,
                    ProductStatus.ACTIVE, null);
            Purchase purchase = persistPurchase(consumer);
            PurchaseItem item = persist(baseItem(purchase, product, commercial.getId())
                    .status(PurchaseItemStatus.CLAIMED).deliveredAt(now()).build());

            em.clear();

            Optional<PurchaseItem> found = purchaseItemRepository.findByIdAndConsumerId(item.getId(),
                    consumer.getId());
            Optional<PurchaseItem> notFound = purchaseItemRepository.findByIdAndConsumerId(item.getId(),
                    other.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getProduct().getName()).isEqualTo("Producto find by id consumer");
            assertThat(found.get().getPurchase().getReferenceId()).isEqualTo(purchase.getReferenceId());
            assertThat(notFound).isEmpty();
        }
    }

    // ==================== countTotalSalesByCommercialId ====================

    @Nested
    @DisplayName("countTotalSalesByCommercialId")
    class CountTotalSalesByCommercialId {

        @Test
        @DisplayName("cuenta por el snapshot commercialId, incluso si el producto ya fue purgado (product=null)")
        void countsBySnapshotCommercialIdEvenWithPurgedProduct() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            CommercialDetails other = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria conteo ventas total");
            Product product = persistProduct(commercial, category, "Producto conteo ventas", 10000,
                    ProductStatus.ACTIVE, null);
            Purchase purchase = persistPurchase(consumer);

            persist(baseItem(purchase, product, commercial.getId()).status(PurchaseItemStatus.CLAIMED).build());
            // Producto purgado: product=null pero el snapshot commercialId sigue seteado y debe seguir contando.
            persist(baseItem(purchase, null, commercial.getId()).status(PurchaseItemStatus.CLAIMED).build());
            persist(baseItem(purchase, product, other.getId()).status(PurchaseItemStatus.CLAIMED).build());

            assertThat(purchaseItemRepository.countTotalSalesByCommercialId(commercial.getId())).isEqualTo(2L);
            assertThat(purchaseItemRepository.countTotalSalesByCommercialId(other.getId())).isEqualTo(1L);
        }
    }

    // ==================== countTotalSalesByCommercialIdAndDatesRange ====================

    @Nested
    @DisplayName("countTotalSalesByCommercialIdAndDatesRange")
    class CountTotalSalesByCommercialIdAndDatesRange {

        @Test
        @DisplayName("cuenta solo los ítems entregados dentro del rango [startDate, endDate)")
        void countsOnlyItemsDeliveredWithinRange() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Purchase purchase = persistPurchase(consumer);

            persist(baseItem(purchase, null, commercial.getId()).status(PurchaseItemStatus.CLAIMED)
                    .deliveredAt(now().minusDays(5)).build());
            persist(baseItem(purchase, null, commercial.getId()).status(PurchaseItemStatus.CLAIMED)
                    .deliveredAt(now().minusDays(40)).build());

            Integer count = purchaseItemRepository.countTotalSalesByCommercialIdAndDatesRange(
                    commercial.getId(), now().minusDays(10), now());

            assertThat(count).isEqualTo(1);
        }
    }

    // ==================== sumTotalCommercialSalesAmountByMonth ====================

    @Nested
    @DisplayName("sumTotalCommercialSalesAmountByMonth")
    class SumTotalCommercialSalesAmountByMonth {

        @Test
        @DisplayName("suma subtotalCents del rango y lo convierte de centavos a pesos")
        void sumsSubtotalCentsAndConvertsToPesos() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Purchase purchase = persistPurchase(consumer);

            persist(baseItem(purchase, null, commercial.getId()).status(PurchaseItemStatus.CLAIMED)
                    .deliveredAt(now()).subtotalCents(15000L).build());
            persist(baseItem(purchase, null, commercial.getId()).status(PurchaseItemStatus.CLAIMED)
                    .deliveredAt(now()).subtotalCents(25000L).build());

            BigDecimal total = purchaseItemRepository.sumTotalCommercialSalesAmountByMonth(
                    commercial.getId(), now().minusDays(1), now().plusDays(1));

            assertThat(total).isEqualByComparingTo(BigDecimal.valueOf(400));
        }

        @Test
        @DisplayName("retorna BigDecimal.ZERO (no lanza NPE) cuando no hay ventas en el rango")
        void returnsZeroWhenNoSalesInRange() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);

            BigDecimal total = purchaseItemRepository.sumTotalCommercialSalesAmountByMonth(
                    commercial.getId(), now().minusDays(30), now().minusDays(29));

            assertThat(total).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // ==================== findTotalCommercialSalesByMonth ====================

    @Nested
    @DisplayName("findTotalCommercialSalesByMonth")
    class FindTotalCommercialSalesByMonth {

        @Test
        @DisplayName("cuenta las ventas del comercial dentro del rango de fechas")
        void countsSalesWithinRange() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Purchase purchase = persistPurchase(consumer);

            persist(baseItem(purchase, null, commercial.getId()).status(PurchaseItemStatus.CLAIMED)
                    .deliveredAt(now()).build());
            persist(baseItem(purchase, null, commercial.getId()).status(PurchaseItemStatus.CLAIMED)
                    .deliveredAt(now().minusDays(90)).build());

            Integer count = purchaseItemRepository.findTotalCommercialSalesByMonth(
                    commercial.getId(), now().minusDays(1), now().plusDays(1));

            assertThat(count).isEqualTo(1);
        }
    }

    // ==================== sumTotalPlatformCommissionsByMonth ====================

    @Nested
    @DisplayName("sumTotalPlatformCommissionsByMonth")
    class SumTotalPlatformCommissionsByMonth {

        @Test
        @DisplayName("regresión: suma commissionCents directamente sin truncar con montos no múltiplos de 100")
        void sumsCommissionCentsWithoutIntegerTruncation() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Purchase purchase = persistPurchase(consumer);

            // La versión buggy (subtotalCents * pct / 100 con división entera)
            // truncaba comisiones pequeñas a 0. Con montos no redondos, la suma
            // correcta debe conservar el valor exacto.
            persist(baseItem(purchase, null, commercial.getId()).status(PurchaseItemStatus.CLAIMED)
                    .deliveredAt(now()).commissionCents(333L).build());
            persist(baseItem(purchase, null, commercial.getId()).status(PurchaseItemStatus.CLAIMED)
                    .deliveredAt(now()).commissionCents(667L).build());

            BigDecimal total = purchaseItemRepository.sumTotalPlatformCommissionsByMonth(
                    commercial.getId(), now().minusDays(1), now().plusDays(1));

            assertThat(total).isEqualByComparingTo(BigDecimal.valueOf(10)); // (333+667)/100 = 10.00
        }

        @Test
        @DisplayName("retorna BigDecimal.ZERO cuando no hay comisiones en el rango")
        void returnsZeroWhenNoCommissionsInRange() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);

            BigDecimal total = purchaseItemRepository.sumTotalPlatformCommissionsByMonth(
                    commercial.getId(), now().minusDays(30), now().minusDays(29));

            assertThat(total).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // ==================== findTopSellingProducts ====================

    @Nested
    @DisplayName("findTopSellingProducts")
    class FindTopSellingProducts {

        @Test
        @DisplayName("solo cuenta productos ACTIVE, agrupa y ordena por total de ventas DESC")
        void countsOnlyActiveProductsOrderedByTotalSalesDesc() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria top selling");

            Product bestSeller = persistProduct(commercial, category, "Producto más vendido", 20000,
                    ProductStatus.ACTIVE, null);
            persistImageAsset(bestSeller, "img-best-seller.png");
            Product secondSeller = persistProduct(commercial, category, "Producto segundo más vendido", 15000,
                    ProductStatus.ACTIVE, null);
            persistImageAsset(secondSeller, "img-second-seller.png");
            Product inactiveButSold = persistProduct(commercial, category, "Producto inactivo vendido", 10000,
                    ProductStatus.INACTIVE, null);
            persistImageAsset(inactiveButSold, "img-inactive-seller.png");

            Purchase purchase = persistPurchase(consumer);
            persist(baseItem(purchase, bestSeller, commercial.getId()).status(PurchaseItemStatus.CLAIMED)
                    .deliveredAt(now()).build());
            persist(baseItem(purchase, bestSeller, commercial.getId()).status(PurchaseItemStatus.CLAIMED)
                    .deliveredAt(now()).build());
            persist(baseItem(purchase, secondSeller, commercial.getId()).status(PurchaseItemStatus.CLAIMED)
                    .deliveredAt(now()).build());
            persist(baseItem(purchase, inactiveButSold, commercial.getId()).status(PurchaseItemStatus.CLAIMED)
                    .deliveredAt(now()).build());

            Page<FeaturedProductResponseDTO> page = purchaseItemRepository.findTopSellingProducts(
                    commercial.getId(), PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(FeaturedProductResponseDTO::getId)
                    .containsExactly(bestSeller.getId(), secondSeller.getId());
            assertThat(page.getContent().get(0).getTotalSales()).isEqualTo(2L);
            assertThat(page.getContent().get(0).getPrice()).isEqualByComparingTo(BigDecimal.valueOf(200));
        }
    }

    // ==================== findTopSellingProductsByDateRange ====================

    @Nested
    @DisplayName("findTopSellingProductsByDateRange")
    class FindTopSellingProductsByDateRange {

        @Test
        @DisplayName("a diferencia de findTopSellingProducts, SÍ incluye productos inactivos si vendieron en el rango")
        void includesInactiveProductsSoldWithinRange() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria top selling histórico");
            Product discontinued = persistProduct(commercial, category, "Producto descontinuado", 10000,
                    ProductStatus.INACTIVE, null);
            persistImageAsset(discontinued, "img-discontinued.png");

            Purchase purchase = persistPurchase(consumer);
            persist(baseItem(purchase, discontinued, commercial.getId()).status(PurchaseItemStatus.CLAIMED)
                    .deliveredAt(now().minusDays(5)).build());

            Page<FeaturedProductResponseDTO> page = purchaseItemRepository.findTopSellingProductsByDateRange(
                    commercial.getId(), now().minusDays(10), now(), PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(FeaturedProductResponseDTO::getId)
                    .containsExactly(discontinued.getId());
        }

        @Test
        @DisplayName("filtra por rango de deliveredAt")
        void filtersByDeliveredAtRange() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria top selling rango");
            Product product = persistProduct(commercial, category, "Producto rango histórico", 10000,
                    ProductStatus.ACTIVE, null);
            persistImageAsset(product, "img-rango.png");

            Purchase purchase = persistPurchase(consumer);
            persist(baseItem(purchase, product, commercial.getId()).status(PurchaseItemStatus.CLAIMED)
                    .deliveredAt(now().minusDays(40)).build());

            Page<FeaturedProductResponseDTO> page = purchaseItemRepository.findTopSellingProductsByDateRange(
                    commercial.getId(), now().minusDays(10), now(), PageRequest.of(0, 10));

            assertThat(page.getContent()).isEmpty();
        }
    }

    // ==================== findDailySalesByCommercialAndDateRange ====================

    @Nested
    @DisplayName("findDailySalesByCommercialAndDateRange")
    class FindDailySalesByCommercialAndDateRange {

        @Test
        @DisplayName("usa COALESCE(p.name, productNameSnapshot) cuando el producto ya fue purgado")
        void usesSnapshotNameWhenProductPurged() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Purchase purchase = persistPurchase(consumer);

            PurchaseItem purgedItem = persist(baseItem(purchase, null, commercial.getId())
                    .productNameSnapshot("Producto ya purgado snapshot")
                    .status(PurchaseItemStatus.CLAIMED)
                    .deliveredAt(now())
                    .subtotalCents(12345L)
                    .commissionCents(1234L)
                    .netToCommercialCents(11111L)
                    .build());

            Page<DailySaleResponseDTO> page = purchaseItemRepository.findDailySalesByCommercialAndDateRange(
                    commercial.getId(), now().minusDays(1), now().plusDays(1), PageRequest.of(0, 10));

            assertThat(page.getContent()).hasSize(1);
            DailySaleResponseDTO dto = page.getContent().get(0);
            assertThat(dto.getProductName()).isEqualTo("Producto ya purgado snapshot");
            assertThat(dto.getPurchaseItemId()).isEqualTo(purgedItem.getId());
            assertThat(dto.getSubtotalCents()).isEqualTo(12345L);
        }

        @Test
        @DisplayName("ordena por deliveredAt DESC")
        void ordersByDeliveredAtDesc() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria daily sales orden");
            Product product = persistProduct(commercial, category, "Producto daily sales", 10000,
                    ProductStatus.ACTIVE, null);
            Purchase purchase = persistPurchase(consumer);

            PurchaseItem older = persist(baseItem(purchase, product, commercial.getId())
                    .status(PurchaseItemStatus.CLAIMED).deliveredAt(now().minusHours(5)).build());
            PurchaseItem newer = persist(baseItem(purchase, product, commercial.getId())
                    .status(PurchaseItemStatus.CLAIMED).deliveredAt(now()).build());

            Page<DailySaleResponseDTO> page = purchaseItemRepository.findDailySalesByCommercialAndDateRange(
                    commercial.getId(), now().minusDays(1), now().plusDays(1), PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(DailySaleResponseDTO::getPurchaseItemId)
                    .containsExactly(newer.getId(), older.getId());
        }
    }

    // ==================== findExpiredUnclaimedPhysicalItems ====================

    @Nested
    @DisplayName("findExpiredUnclaimedPhysicalItems")
    class FindExpiredUnclaimedPhysicalItems {

        @Test
        @DisplayName("trae ítems PENDING físicos cuyo claimExpiresAt ya venció, con el grafo completo fetch-eado")
        void returnsExpiredPendingPhysicalItemsWithFullGraphFetched() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria expirados");
            Product physicalProduct = persistProduct(commercial, category, "Producto físico expirado", 10000,
                    ProductStatus.ACTIVE, ProductType.PHYSICAL);
            Purchase purchase = persistPurchase(consumer);

            PurchaseItem expired = persist(baseItem(purchase, physicalProduct, commercial.getId())
                    .status(PurchaseItemStatus.PENDING).claimExpiresAt(now().minusDays(1)).build());

            em.clear();

            List<PurchaseItem> result = purchaseItemRepository.findExpiredUnclaimedPhysicalItems(now());

            assertThat(result).extracting(PurchaseItem::getId).containsExactly(expired.getId());
            PurchaseItem fetched = result.get(0);
            assertThat(fetched.getProduct().getCommercial().getCompanyName()).isNotNull();
            assertThat(fetched.getPurchase().getConsumer().getUser().getEmail())
                    .isEqualTo(consumer.getUser().getEmail());
        }

        @Test
        @DisplayName("excluye ítems digitales, ya reclamados, o cuyo plazo aún no vence")
        void excludesDigitalClaimedOrNotYetExpired() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria expirados exclusiones");
            Product physicalProduct = persistProduct(commercial, category, "Producto físico exclusiones", 10000,
                    ProductStatus.ACTIVE, ProductType.PHYSICAL);
            Product digitalProduct = persistProduct(commercial, category, "Producto digital exclusiones", 10000,
                    ProductStatus.ACTIVE, ProductType.DIGITAL);
            Purchase purchase = persistPurchase(consumer);

            // Digital, aunque PENDING y con claimExpiresAt vencido, nunca aplica.
            persist(baseItem(purchase, digitalProduct, commercial.getId())
                    .status(PurchaseItemStatus.PENDING).claimExpiresAt(now().minusDays(1)).build());

            // Físico ya reclamado.
            persist(baseItem(purchase, physicalProduct, commercial.getId())
                    .status(PurchaseItemStatus.CLAIMED).claimExpiresAt(now().minusDays(1)).build());

            // Físico cuyo plazo aún no vence.
            persist(baseItem(purchase, physicalProduct, commercial.getId())
                    .status(PurchaseItemStatus.PENDING).claimExpiresAt(now().plusDays(5)).build());

            List<PurchaseItem> result = purchaseItemRepository.findExpiredUnclaimedPhysicalItems(now());

            assertThat(result).isEmpty();
        }
    }

    // ==================== detachProductReferences ====================

    @Nested
    @DisplayName("detachProductReferences")
    class DetachProductReferences {

        @Test
        @DisplayName("desvincula product y assignedProductStock de todos los ítems del producto, sin borrarlos")
        void detachesProductAndStockFromAllItemsOfProduct() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria detach");
            Product productToPurge = persistProduct(commercial, category, "Producto a purgar", 10000,
                    ProductStatus.INACTIVE, null);
            ProductStock stock = ProductStock.builder()
                    .product(productToPurge)
                    .code("code-detach")
                    .codeHash("hash-detach")
                    .status(StockStatus.SOLD)
                    .build();
            em.persist(stock);
            em.flush();

            Product otherProduct = persistProduct(commercial, category, "Otro producto", 10000, ProductStatus.ACTIVE,
                    null);

            Purchase purchase = persistPurchase(consumer);
            PurchaseItem itemA = persist(baseItem(purchase, productToPurge, commercial.getId())
                    .assignedProductStock(stock).status(PurchaseItemStatus.CLAIMED).build());
            PurchaseItem itemB = persist(baseItem(purchase, productToPurge, commercial.getId())
                    .status(PurchaseItemStatus.CLAIMED).build());
            PurchaseItem unrelatedItem = persist(baseItem(purchase, otherProduct, commercial.getId())
                    .status(PurchaseItemStatus.CLAIMED).build());

            purchaseItemRepository.detachProductReferences(productToPurge.getId());

            // clearAutomatically=true en el @Modifying ya limpió el contexto de persistencia.
            PurchaseItem reloadedA = purchaseItemRepository.findById(itemA.getId()).orElseThrow();
            PurchaseItem reloadedB = purchaseItemRepository.findById(itemB.getId()).orElseThrow();
            PurchaseItem reloadedUnrelated = purchaseItemRepository.findById(unrelatedItem.getId()).orElseThrow();

            assertThat(reloadedA.getProduct()).isNull();
            assertThat(reloadedA.getAssignedProductStock()).isNull();
            // El historial financiero/auditable (snapshot) se conserva intacto.
            assertThat(reloadedA.getProductNameSnapshot()).isEqualTo("Producto a purgar");
            assertThat(reloadedB.getProduct()).isNull();
            assertThat(reloadedUnrelated.getProduct()).isNotNull();
            assertThat(reloadedUnrelated.getProduct().getId()).isEqualTo(otherProduct.getId());
        }
    }

    // ==================== findPendingPhysicalItems ====================

    @Nested
    @DisplayName("findPendingPhysicalItems")
    class FindPendingPhysicalItems {

        @Test
        @DisplayName("sin filtros opcionales trae los ítems PENDING físicos del comercial")
        void noOptionalFiltersReturnsAllPendingPhysicalOfCommercial() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria pendientes físicos");
            Product physicalProduct = persistProduct(commercial, category, "Producto físico pendiente", 10000,
                    ProductStatus.ACTIVE, ProductType.PHYSICAL);
            Purchase purchase = persistPurchase(consumer);
            PurchaseItem pending = persist(baseItem(purchase, physicalProduct, commercial.getId())
                    .status(PurchaseItemStatus.PENDING).build());

            // No debe aparecer: mismo comercial pero ya CLAIMED.
            persist(baseItem(purchase, physicalProduct, commercial.getId())
                    .status(PurchaseItemStatus.CLAIMED).build());

            Page<PurchaseItem> page = purchaseItemRepository.findPendingPhysicalItems(
                    commercial.getId(), null, null, PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(PurchaseItem::getId).containsExactly(pending.getId());
        }

        @Test
        @DisplayName("filtra por documentType cuando se especifica")
        void filtersByDocumentType() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria pendientes doc type");
            Product physicalProduct = persistProduct(commercial, category, "Producto físico doc type", 10000,
                    ProductStatus.ACTIVE, ProductType.PHYSICAL);

            ConsumerDetails ccConsumer = TestEntities.persistConsumer(em); // documentType CC por defecto
            Purchase ccPurchase = persistPurchase(ccConsumer);
            PurchaseItem ccItem = persist(baseItem(ccPurchase, physicalProduct, commercial.getId())
                    .status(PurchaseItemStatus.PENDING).build());

            ConsumerDetails ceConsumer = TestEntities.persistConsumer(em);
            ceConsumer.setDocumentType(DocumentType.CE);
            em.flush();
            Purchase cePurchase = persistPurchase(ceConsumer);
            persist(baseItem(cePurchase, physicalProduct, commercial.getId())
                    .status(PurchaseItemStatus.PENDING).build());

            Page<PurchaseItem> page = purchaseItemRepository.findPendingPhysicalItems(
                    commercial.getId(), DocumentType.CC, null, PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(PurchaseItem::getId).containsExactly(ccItem.getId());
        }

        @Test
        @DisplayName("filtra por documentNumber con LIKE parcial, sin distinguir mayúsculas")
        void filtersByDocumentNumberPartialCaseInsensitive() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria pendientes doc number");
            Product physicalProduct = persistProduct(commercial, category, "Producto físico doc number", 10000,
                    ProductStatus.ACTIVE, ProductType.PHYSICAL);

            ConsumerDetails target = TestEntities.persistConsumer(em);
            target.setDocumentNumber("1029384756");
            em.flush();
            Purchase targetPurchase = persistPurchase(target);
            PurchaseItem targetItem = persist(baseItem(targetPurchase, physicalProduct, commercial.getId())
                    .status(PurchaseItemStatus.PENDING).build());

            ConsumerDetails other = TestEntities.persistConsumer(em);
            other.setDocumentNumber("5551234567");
            em.flush();
            Purchase otherPurchase = persistPurchase(other);
            persist(baseItem(otherPurchase, physicalProduct, commercial.getId())
                    .status(PurchaseItemStatus.PENDING).build());

            Page<PurchaseItem> page = purchaseItemRepository.findPendingPhysicalItems(
                    commercial.getId(), null, "9384", PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(PurchaseItem::getId).containsExactly(targetItem.getId());
        }
    }

    // ==================== findClaimedWithoutPayout ====================

    @Nested
    @DisplayName("findClaimedWithoutPayout")
    class FindClaimedWithoutPayout {

        @Test
        @DisplayName("trae ítems CLAIMED sin payout y sin PQRS abierto")
        void returnsClaimedItemsWithoutPayoutOrOpenPqrs() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria claimed sin payout");
            Product product = persistProduct(commercial, category, "Producto claimed sin payout", 10000,
                    ProductStatus.ACTIVE, null);
            Purchase purchase = persistPurchase(consumer);
            PurchaseItem eligible = persist(baseItem(purchase, product, commercial.getId())
                    .status(PurchaseItemStatus.CLAIMED).deliveredAt(now()).build());

            List<PurchaseItem> result = purchaseItemRepository.findClaimedWithoutPayout();

            assertThat(result).extracting(PurchaseItem::getId).contains(eligible.getId());
        }

        @Test
        @DisplayName("excluye ítems que ya entraron a un payout")
        void excludesItemsAlreadyInPayout() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria claimed con payout");
            Product product = persistProduct(commercial, category, "Producto claimed con payout", 10000,
                    ProductStatus.ACTIVE, null);
            Purchase purchase = persistPurchase(consumer);
            PurchaseItem paidItem = persist(baseItem(purchase, product, commercial.getId())
                    .status(PurchaseItemStatus.CLAIMED).deliveredAt(now()).build());
            Payout payout = persistPayout(commercial);
            persistPayoutItem(payout, paidItem);

            List<PurchaseItem> result = purchaseItemRepository.findClaimedWithoutPayout();

            assertThat(result).extracting(PurchaseItem::getId).doesNotContain(paidItem.getId());
        }

        @Test
        @DisplayName("excluye ítems con un PQRS todavía sin resolver, pero los incluye si el PQRS ya está RESUELTA")
        void excludesItemsWithOpenPqrsButIncludesResolvedOnes() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ProductCategory category = persistCategory("Categoria claimed pqrs");
            Product product = persistProduct(commercial, category, "Producto claimed pqrs", 10000,
                    ProductStatus.ACTIVE, null);
            Purchase purchase = persistPurchase(consumer);

            PurchaseItem withOpenPqrs = persist(baseItem(purchase, product, commercial.getId())
                    .status(PurchaseItemStatus.CLAIMED).deliveredAt(now()).build());
            persistPqrs(consumer, withOpenPqrs, PqrsStatus.RECIBIDA);

            PurchaseItem withResolvedPqrs = persist(baseItem(purchase, product, commercial.getId())
                    .status(PurchaseItemStatus.CLAIMED).deliveredAt(now()).build());
            persistPqrs(consumer, withResolvedPqrs, PqrsStatus.RESUELTA);

            List<PurchaseItem> result = purchaseItemRepository.findClaimedWithoutPayout();

            assertThat(result).extracting(PurchaseItem::getId)
                    .contains(withResolvedPqrs.getId())
                    .doesNotContain(withOpenPqrs.getId());
        }
    }
}
