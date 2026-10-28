package com.verygana2.services.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import org.assertj.core.api.SoftAssertions;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.verygana2.config.TimeConfig;
import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.product.responses.ProductSummaryResponseDTO;
import com.verygana2.mappers.TargetAudienceMapperImpl;
import com.verygana2.mappers.finance.MoneyMapper;
import com.verygana2.mappers.marketplace.ProductMapperImpl;
import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.enums.marketplace.ProductStatus;
import com.verygana2.models.enums.marketplace.StockStatus;
import com.verygana2.models.marketplace.FavoriteProduct;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductCategory;
import com.verygana2.models.marketplace.ProductImageAsset;
import com.verygana2.models.marketplace.ProductStock;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.security.ProductCodeEncryptor;
import com.verygana2.services.interfaces.NotificationService;
import com.verygana2.services.interfaces.details.AdminDetailsService;
import com.verygana2.services.interfaces.details.ConsumerDetailsService;
import com.verygana2.services.interfaces.marketplace.ProductCategoryService;
import com.verygana2.storage.service.AssetOrphanedService;
import com.verygana2.storage.service.PublicUrlBuilder;
import com.verygana2.storage.service.R2Service;
import com.verygana2.testsupport.PublicUrlBuilders;
import com.verygana2.testsupport.TestEntities;
import com.verygana2.utils.validators.AssetDurationService;
import com.verygana2.utils.validators.TargetAudienceAssembler;

import jakarta.persistence.EntityManager;

/**
 * Listados de productos (catálogo, comercio, admin y favoritos) sobre H2 con un Hibernate real:
 * dos cosas que un mock no ve.
 *
 * <ul>
 * <li>{@code sameQueries...}: el número de sentencias a la base para armar una página no depende
 * de cuántos productos tiene (RN-3). Cada producto del lote es de un comercio y una categoría
 * distintos y tiene 20 unidades de inventario e imagen, para que cualquier carga perezosa por
 * producto, por comercio o por unidad aparezca. Se cuenta con las estadísticas de Hibernate. La
 * consulta de conteo de Spring Data se omite cuando la página no se llena: por eso 20 productos
 * cuestan exactamente una sentencia más que 1 (salvo el admin, que se compara con páginas llenas
 * en ambos casos).</li>
 * <li>{@code fieldsUnchanged} y los casos de stock: cada campo del DTO, el orden y la paginación
 * coinciden con lo que se espera de los datos sembrados (RN-1, RN-2, CA-6..CA-8).</li>
 * </ul>
 *
 * Los campos pasan con el código de antes de la spec 016 (son la foto del "antes"); los
 * {@code sameQueries...} solo pasan cuando los listados dejan de consultar por producto.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:product-listing-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "CASE_INSENSITIVE_IDENTIFIERS=TRUE;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        // V1__baseline.sql es MySQL puro: el esquema de prueba lo genera Hibernate.
        "spring.flyway.enabled=false",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "financial.key-value-cents=1000",
        "app.base-url=https://api.test"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ ProductServiceImpl.class, ProductMapperImpl.class, MoneyMapper.class, TargetAudienceMapperImpl.class,
        ProductKeyPromotionResolver.class, ProductListingFactsResolver.class, TimeConfig.class,
        ProductListingQueryCountIntegrationTest.UrlConfig.class })
@DisplayName("Listados de productos: consultas por página y campos (integración H2)")
class ProductListingQueryCountIntegrationTest {

    @TestConfiguration
    static class UrlConfig {
        @Bean
        PublicUrlBuilder publicUrlBuilder() {
            return PublicUrlBuilders.forDomain("cdn.test");
        }
    }

    private static final AtomicLong SEQ = new AtomicLong(1);
    private static final int UNITS_PER_PRODUCT = 20;

    @Autowired private ProductServiceImpl service;
    @Autowired private EntityManager em;

    // Lo que no participa del listado.
    @MockitoBean private AdminDetailsService adminDetailsService;
    @MockitoBean private NotificationService notificationService;
    @MockitoBean private ProductCategoryService productCategoryService;
    @MockitoBean private ConsumerDetailsService consumerDetailsService;
    @MockitoBean private R2Service r2Service;
    @MockitoBean private AssetOrphanedService assetOrphanedService;
    @MockitoBean private ProductCodeEncryptor productCodeEncryptor;
    @MockitoBean private TargetAudienceAssembler targetAudienceAssembler;
    @MockitoBean private AssetDurationService assetDurationService;

    private Statistics statistics;
    private ZonedDateTime base;

    @BeforeEach
    void setUp() {
        statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        base = ZonedDateTime.now(ZoneOffset.UTC).minusDays(1);
    }

    // ==================== FIXTURES ====================

    /** Lo que se sembró de un producto: de aquí salen los valores esperados del DTO. */
    private record Seed(Product product, Long id, String name, long priceCents, int maxKeysPct, String categoryName,
            String companyName, UUID commercialPublicId, ProductStatus status, String imageKey, int stock) {
    }

    private record Measured<T>(T result, long statements) {
    }

    private ProductCategory persistCategory() {
        ProductCategory category = new ProductCategory();
        category.setName("Categoria " + SEQ.getAndIncrement());
        em.persist(category);
        em.flush();
        return category;
    }

    private void addStock(Product product, StockStatus status, ZonedDateTime expirationDate, int count) {
        for (int i = 0; i < count; i++) {
            ProductStock stock = new ProductStock();
            stock.setProduct(product);
            String hash = "hash-" + UUID.randomUUID();
            stock.setCode("cipher-" + hash);
            stock.setCodeHash(hash);
            stock.setStatus(status);
            stock.setExpirationDate(expirationDate);
            em.persist(stock);
        }
        em.flush();
    }

    /**
     * Siembra un producto. {@code createdAt} se fija con un UPDATE masivo (el callback de ciclo de
     * vida lo sobrescribiría) para que el orden por fecha sea determinista.
     */
    private Seed seedProduct(CommercialDetails commercial, String name, ProductStatus status, int index,
            ZonedDateTime createdAt, int availableUnits) {
        ProductCategory category = persistCategory();
        long priceCents = 1_000_000L + index * 10_000L;
        int maxKeysPct = 20 + (index % 3) * 10;

        Product product = new Product();
        product.setCommercial(commercial);
        product.setProductCategory(category);
        product.setName(name);
        product.setDescription("Descripcion de " + name);
        product.setPriceCents(priceCents);
        product.setMaxKeysPct(maxKeysPct);
        em.persist(product);
        em.flush();
        product.setStatus(status);
        em.flush();

        em.createQuery("UPDATE Product p SET p.createdAt = :createdAt WHERE p.id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", product.getId())
                .executeUpdate();

        ProductImageAsset asset = new ProductImageAsset();
        asset.setObjectKey("products/" + UUID.randomUUID() + ".png");
        asset.setSizeBytes(1024L);
        asset.setStatus(AssetStatus.VALIDATED);
        asset.setProduct(product);
        em.persist(asset);

        addStock(product, StockStatus.AVAILABLE, null, availableUnits);

        return new Seed(product, product.getId(), name, priceCents, maxKeysPct, category.getName(),
                commercial.getCompanyName(), commercial.getUser().getPublicId(), status, asset.getObjectKey(),
                availableUnits);
    }

    /**
     * Siembra {@code count} productos con 20 unidades cada uno. El producto i tiene createdAt
     * {@code newest - i minutos}, así que el orden "más reciente primero" es el orden de la lista.
     * Con {@code sharedCommercial == null} cada uno es de un comercio distinto.
     */
    private List<Seed> seedBatch(String prefix, int count, CommercialDetails sharedCommercial, ZonedDateTime newest,
            IntFunction<ProductStatus> statusOf) {
        List<Seed> seeds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            CommercialDetails commercial = sharedCommercial != null ? sharedCommercial
                    : TestEntities.persistCommercial(em);
            seeds.add(seedProduct(commercial, String.format("%s %02d", prefix, i), statusOf.apply(i), i,
                    newest.minusMinutes(i), UNITS_PER_PRODUCT));
        }
        return seeds;
    }

    private void favorite(ConsumerDetails consumer, Seed seed, ZonedDateTime createdAt) {
        FavoriteProduct favorite = new FavoriteProduct();
        favorite.setConsumer(consumer);
        favorite.setProduct(seed.product());
        em.persist(favorite);
        em.flush();
        em.createQuery("UPDATE FavoriteProduct f SET f.createdAt = :createdAt WHERE f.id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", favorite.getId())
                .executeUpdate();
    }

    /** Vacía el contexto de persistencia (sin él no se verían las cargas perezosas) y cuenta las sentencias. */
    private <T> Measured<T> measure(Supplier<T> call) {
        em.flush();
        em.clear();
        statistics.clear();
        T result = call.get();
        return new Measured<>(result, statistics.getPrepareStatementCount());
    }

    /** Igual que {@link #measure} pero sin contar: deja el contexto vacío para leer lo que quedó en la base. */
    private <T> T fresh(Supplier<T> call) {
        return measure(call).result();
    }

    /** Valor esperado del DTO de un producto, construido solo desde lo sembrado. */
    private ProductSummaryResponseDTO expected(Seed seed, boolean favorite) {
        String imageUrl = (seed.status() == ProductStatus.PENDING || seed.status() == ProductStatus.REJECTED)
                ? "https://api.test/products/" + seed.id() + "/private-image"
                : "https://cdn.test/public/" + seed.imageKey();
        long maxKeysValueCents = seed.priceCents() * seed.maxKeysPct() / 100;
        return ProductSummaryResponseDTO.builder()
                .id(seed.id())
                .name(seed.name())
                .imageUrl(imageUrl)
                .price(BigDecimal.valueOf(seed.priceCents()).divide(BigDecimal.valueOf(100)))
                .maxKeysAllowed(maxKeysValueCents / 1_000)
                .maxKeysPct(favorite ? null : (long) seed.maxKeysPct())
                .minCashCents(seed.priceCents() - maxKeysValueCents)
                .averageRate(0.0)
                .reviewCount(favorite ? null : 0)
                .categoryName(seed.categoryName())
                .stock(seed.stock())
                .status(seed.status())
                .commercialPublicId(seed.commercialPublicId())
                .companyName(seed.companyName())
                .isGameReward(favorite ? null : false)
                .specialKeyPromotion(null)
                .build();
    }

    private void assertPage(PagedResponse<ProductSummaryResponseDTO> actual, List<Seed> expectedSeeds,
            boolean favorite, PagedResponse.Meta expectedMeta) {
        assertThat(actual.getData())
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(expectedSeeds.stream().map(seed -> expected(seed, favorite)).toList());
        assertThat(actual.getMeta()).usingRecursiveComparison().isEqualTo(expectedMeta);
    }

    private static PagedResponse.Meta meta(int page, int size, long totalElements, int totalPages, boolean hasNext,
            boolean hasPrevious, boolean sorted) {
        return PagedResponse.Meta.builder().page(page).size(size).totalElements(totalElements)
                .totalPages(totalPages).hasNext(hasNext).hasPrevious(hasPrevious).sorted(sorted).build();
    }

    /** Stock mezclado: 5 disponibles vigentes entre unidades vendidas, reservadas, inválidas y vencidas. */
    private Seed seedMixedStockProduct(CommercialDetails commercial) {
        Seed seed = seedProduct(commercial, "Mixto", ProductStatus.ACTIVE, 0, base, 0);
        Product product = seed.product();
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        addStock(product, StockStatus.AVAILABLE, null, 3);
        addStock(product, StockStatus.AVAILABLE, now.plusDays(5), 2);
        addStock(product, StockStatus.SOLD, null, 4);
        addStock(product, StockStatus.RESERVED, null, 1);
        addStock(product, StockStatus.INVALID, null, 1);
        addStock(product, StockStatus.AVAILABLE, now.minusDays(1), 2);
        return new Seed(product, seed.id(), seed.name(), seed.priceCents(), seed.maxKeysPct(), seed.categoryName(),
                seed.companyName(), seed.commercialPublicId(), seed.status(), seed.imageKey(), 5);
    }

    private void assertSameQueries(Measured<?> one, Measured<?> many, long extraForCount, long budget) {
        SoftAssertions.assertSoftly(soft -> {
            soft.assertThat(many.statements())
                    .as("sentencias con 20 productos (con 1 producto: %d); deben ser las de 1 producto + %d",
                            one.statements(), extraForCount)
                    .isEqualTo(one.statements() + extraForCount);
            soft.assertThat(many.statements())
                    .as("sentencias con 20 productos (con 1 producto: %d); presupuesto del plan: %d",
                            one.statements(), budget)
                    .isLessThanOrEqualTo(budget);
        });
    }

    // ==================== CATÁLOGO DEL CONSUMIDOR ====================


    /** consumerId nulo = anónimo; con consumidor se aplica el orden personalizado (sin excluir nada). */
    private Long consumerIdFor(boolean personalized) {
        if (!personalized) {
            return null;
        }
        ConsumerDetails consumer = TestEntities.persistConsumer(em);
        when(consumerDetailsService.getConsumerById(consumer.getId())).thenReturn(consumer);
        return consumer.getId();
    }

    private PagedResponse<ProductSummaryResponseDTO> catalogList(Long consumerId) {
        return service.filterProducts(consumerId, null, null, null, null, 0, "createdAt", "DESC");
    }

    @ParameterizedTest(name = "personalizado={0}")
    @ValueSource(booleans = { false, true })
    @DisplayName("20 productos de 20 comercios cuestan las mismas sentencias que 1 (+1 del conteo)")
    void catalogSameQueriesForOneAndTwentyProducts(boolean personalized) {
        Long consumerId = consumerIdFor(personalized);
        seedBatch("Unico", 1, null, base.minusDays(1), i -> ProductStatus.ACTIVE);
        Measured<PagedResponse<ProductSummaryResponseDTO>> one = measure(() -> catalogList(consumerId));
        assertThat(one.result().getData()).hasSize(1);

        seedBatch("Lote", 20, null, base, i -> ProductStatus.ACTIVE);
        Measured<PagedResponse<ProductSummaryResponseDTO>> many = measure(() -> catalogList(consumerId));
        assertThat(many.result().getData()).hasSize(20);

        // página + conteo + hechos/stock + promociones = 4 (el consumidor está mockeado: sin sentencia propia)
        assertSameQueries(one, many, 1, 4);
    }

    @ParameterizedTest(name = "personalizado={0}")
    @ValueSource(booleans = { false, true })
    @DisplayName("cada campo del DTO, el orden y la paginación coinciden con lo sembrado")
    void catalogFieldsUnchanged(boolean personalized) {
        Long consumerId = consumerIdFor(personalized);
        List<Seed> seeds = seedBatch("Lote", 25, null, base, i -> ProductStatus.ACTIVE);

        PagedResponse<ProductSummaryResponseDTO> page = fresh(() -> catalogList(consumerId));

        assertPage(page, seeds.subList(0, 20), false, meta(0, 20, 25, 2, true, false, true));
    }

    @Test
    @DisplayName("stock mezclado: solo cuentan las AVAILABLE no vencidas")
    void catalogMixedStockShowsOnlyAvailableNotExpired() {
        Seed mixed = seedMixedStockProduct(TestEntities.persistCommercial(em));

        PagedResponse<ProductSummaryResponseDTO> page = fresh(() -> catalogList(null));

        assertThat(page.getData()).hasSize(1);
        assertThat(page.getData().get(0).getStock()).isEqualTo(mixed.stock()).isEqualTo(5);
    }

    @Test
    @DisplayName("un producto sin ninguna unidad muestra stock 0, no nulo ni error")
    void catalogProductWithoutStockShowsZero() {
        Seed empty = seedProduct(TestEntities.persistCommercial(em), "Vacio", ProductStatus.ACTIVE, 0, base, 0);

        assertThatCode(() -> fresh(() -> catalogList(null))).doesNotThrowAnyException();
        PagedResponse<ProductSummaryResponseDTO> page = fresh(() -> catalogList(null));

        assertThat(page.getData()).hasSize(1);
        assertThat(page.getData().get(0).getId()).isEqualTo(empty.id());
        assertThat(page.getData().get(0).getStock()).isEqualTo(0);
    }

    // ==================== PRODUCTOS DEL COMERCIO ====================


    private PagedResponse<ProductSummaryResponseDTO> commercialList(CommercialDetails commercial) {
        return service.getCommercialProducts(commercial.getId(), 0);
    }

    @Test
    @DisplayName("20 productos de 20 categorías cuestan las mismas sentencias que 1 (+1 del conteo)")
    void commercialSameQueriesForOneAndTwentyProducts() {
        CommercialDetails commercial = TestEntities.persistCommercial(em);
        seedBatch("Unico", 1, commercial, base.minusDays(1), i -> ProductStatus.ACTIVE);
        Measured<PagedResponse<ProductSummaryResponseDTO>> one = measure(() -> commercialList(commercial));
        assertThat(one.result().getData()).hasSize(1);

        seedBatch("Lote", 20, commercial, base, i -> ProductStatus.ACTIVE);
        Measured<PagedResponse<ProductSummaryResponseDTO>> many = measure(() -> commercialList(commercial));
        assertThat(many.result().getData()).hasSize(20);

        // existe el comercio + página + conteo + hechos/stock + promociones = 5
        assertSameQueries(one, many, 1, 5);
    }

    @Test
    @DisplayName("cada campo del DTO, el orden y la paginación coinciden con lo sembrado")
    void commercialFieldsUnchanged() {
        CommercialDetails commercial = TestEntities.persistCommercial(em);
        List<Seed> seeds = seedBatch("Lote", 25, commercial, base, i -> ProductStatus.ACTIVE);

        PagedResponse<ProductSummaryResponseDTO> page = fresh(() -> commercialList(commercial));

        assertPage(page, seeds.subList(0, 20), false, meta(0, 20, 25, 2, true, false, true));
    }

    @Test
    @DisplayName("stock mezclado: solo cuentan las AVAILABLE no vencidas")
    void commercialMixedStockShowsOnlyAvailableNotExpired() {
        CommercialDetails commercial = TestEntities.persistCommercial(em);
        Seed mixed = seedMixedStockProduct(commercial);

        PagedResponse<ProductSummaryResponseDTO> page = fresh(() -> commercialList(commercial));

        assertThat(page.getData()).hasSize(1);
        assertThat(page.getData().get(0).getStock()).isEqualTo(mixed.stock()).isEqualTo(5);
    }

    @Test
    @DisplayName("las unidades agregadas sin ninguna compra (como los seeds) cuentan en el stock")
    void commercialSeededStockIsCounted() {
        CommercialDetails commercial = TestEntities.persistCommercial(em);
        Seed seeded = seedProduct(commercial, "Seed", ProductStatus.ACTIVE, 0, base, 3);

        PagedResponse<ProductSummaryResponseDTO> page = fresh(() -> commercialList(commercial));

        assertThat(page.getData()).hasSize(1);
        assertThat(page.getData().get(0).getId()).isEqualTo(seeded.id());
        assertThat(page.getData().get(0).getStock()).isEqualTo(3);
    }

    // ==================== ADMIN ====================


    /** Con filtro de estado todos los productos son de ese estado; sin él se mezclan los tres. */
    private IntFunction<ProductStatus> adminStatusesFor(ProductStatus filter) {
        if (filter != null) {
            return i -> filter;
        }
        ProductStatus[] cycle = { ProductStatus.ACTIVE, ProductStatus.PENDING, ProductStatus.REJECTED };
        return i -> cycle[i % cycle.length];
    }

    @ParameterizedTest(name = "estado={0}, búsqueda={1}")
    @CsvSource(value = { "null,null", "ACTIVE,null", "null,alfa", "ACTIVE,alfa" }, nullValues = "null")
    @DisplayName("20 productos de 20 comercios cuestan las mismas sentencias que 1 (páginas llenas)")
    void adminSameQueriesForOneAndTwentyProducts(ProductStatus status, String search) {
        seedBatch("Alfa Unico", 1, null, base.minusDays(1), i -> status != null ? status : ProductStatus.ACTIVE);
        Measured<PagedResponse<ProductSummaryResponseDTO>> one = measure(
                () -> service.getAllProductsForAdmin(status, search, PageRequest.of(0, 1)));
        assertThat(one.result().getData()).hasSize(1);

        seedBatch("Alfa Lote", 20, null, base, adminStatusesFor(status));
        Measured<PagedResponse<ProductSummaryResponseDTO>> many = measure(
                () -> service.getAllProductsForAdmin(status, search, PageRequest.of(0, 20)));
        assertThat(many.result().getData()).hasSize(20);

        // página + conteo + hechos/stock + promociones = 4, en ambos casos
        assertSameQueries(one, many, 0, 4);
    }

    @ParameterizedTest(name = "estado={0}, búsqueda={1}")
    @CsvSource(value = { "null,null", "ACTIVE,null", "null,alfa", "ACTIVE,alfa" }, nullValues = "null")
    @DisplayName("cada campo del DTO (imagen privada de PENDING/REJECTED incluida), el orden y la paginación coinciden")
    void adminFieldsUnchanged(ProductStatus status, String search) {
        List<Seed> seeds = seedBatch("Alfa Lote", 25, null, base, adminStatusesFor(status));

        PagedResponse<ProductSummaryResponseDTO> page = fresh(() -> service.getAllProductsForAdmin(status, search,
                PageRequest.of(0, 20)));

        assertPage(page, seeds.subList(0, 20), false, meta(0, 20, 25, 2, true, false, false));
    }

    @Test
    @DisplayName("el filtro de búsqueda deja fuera los productos que no coinciden")
    void adminSearchExcludesNonMatching() {
        List<Seed> matching = seedBatch("Alfa Lote", 3, null, base, i -> ProductStatus.ACTIVE);
        seedBatch("Beta Lote", 3, null, base.minusDays(1), i -> ProductStatus.ACTIVE);

        PagedResponse<ProductSummaryResponseDTO> page = fresh(() -> service.getAllProductsForAdmin(null, "alfa",
                PageRequest.of(0, 20)));

        assertPage(page, matching, false, meta(0, 20, 3, 1, false, false, false));
    }

    @Test
    @DisplayName("stock mezclado: solo cuentan las AVAILABLE no vencidas")
    void adminMixedStockShowsOnlyAvailableNotExpired() {
        Seed mixed = seedMixedStockProduct(TestEntities.persistCommercial(em));

        PagedResponse<ProductSummaryResponseDTO> page = fresh(() -> service.getAllProductsForAdmin(null, null,
                PageRequest.of(0, 20)));

        assertThat(page.getData()).hasSize(1);
        assertThat(page.getData().get(0).getStock()).isEqualTo(mixed.stock()).isEqualTo(5);
    }

    // ==================== FAVORITOS ====================


    private List<Seed> seedFavorites(ConsumerDetails consumer, String prefix, int count, ZonedDateTime newest) {
        List<Seed> seeds = seedBatch(prefix, count, null, newest, i -> ProductStatus.ACTIVE);
        for (int i = 0; i < seeds.size(); i++) {
            favorite(consumer, seeds.get(i), newest.minusMinutes(i));
        }
        return seeds;
    }

    @Test
    @DisplayName("favoritos de 10 comercios distintos cuestan las mismas sentencias que 1 (+1 del conteo)")
    void favoritesSameQueriesForOneAndManyProducts() {
        ConsumerDetails consumer = TestEntities.persistConsumer(em);
        seedFavorites(consumer, "Unico", 1, base.minusDays(1));
        Measured<PagedResponse<ProductSummaryResponseDTO>> one = measure(
                () -> service.getFavorites(consumer.getId(), 0));
        assertThat(one.result().getData()).hasSize(1);

        // La página de favoritos es de 10: con 20 favoritos se llena y trae los 10 más recientes.
        seedFavorites(consumer, "Lote", 20, base);
        Measured<PagedResponse<ProductSummaryResponseDTO>> many = measure(
                () -> service.getFavorites(consumer.getId(), 0));
        assertThat(many.result().getData()).hasSize(10);

        // página + conteo + hechos/stock + promociones = 4
        assertSameQueries(one, many, 1, 4);
    }

    @Test
    @DisplayName("cada campo del DTO (con los nulos de favoritos), el orden y la paginación coinciden")
    void favoritesFieldsUnchanged() {
        ConsumerDetails consumer = TestEntities.persistConsumer(em);
        List<Seed> seeds = seedFavorites(consumer, "Lote", 12, base);

        PagedResponse<ProductSummaryResponseDTO> page = fresh(() -> service.getFavorites(consumer.getId(), 0));

        assertPage(page, seeds.subList(0, 10), true, meta(0, 10, 12, 2, true, false, true));
    }

    @Test
    @DisplayName("stock mezclado: solo cuentan las AVAILABLE no vencidas")
    void favoritesMixedStockShowsOnlyAvailableNotExpired() {
        ConsumerDetails consumer = TestEntities.persistConsumer(em);
        Seed mixed = seedMixedStockProduct(TestEntities.persistCommercial(em));
        favorite(consumer, mixed, base);

        PagedResponse<ProductSummaryResponseDTO> page = fresh(() -> service.getFavorites(consumer.getId(), 0));

        assertThat(page.getData()).hasSize(1);
        assertThat(page.getData().get(0).getStock()).isEqualTo(mixed.stock()).isEqualTo(5);
    }
}
