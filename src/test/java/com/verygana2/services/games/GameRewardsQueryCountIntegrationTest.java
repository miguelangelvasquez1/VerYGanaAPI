package com.verygana2.services.games;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.verygana2.config.TimeConfig;
import com.verygana2.dtos.game.RewardCardResponseDTO;
import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.enums.marketplace.ProductStatus;
import com.verygana2.models.enums.marketplace.StockStatus;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.marketplace.AllyProductPromotion;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductCategory;
import com.verygana2.models.marketplace.ProductImageAsset;
import com.verygana2.models.marketplace.ProductStock;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.services.finance.KeyWalletServiceImpl;
import com.verygana2.services.interfaces.finance.KeyLedgerService;
import com.verygana2.services.interfaces.finance.KeyWalletService;
import com.verygana2.services.interfaces.levels.LevelService;
import com.verygana2.services.marketplace.ProductListingFactsResolver;
import com.verygana2.storage.service.PublicUrlBuilder;
import com.verygana2.testsupport.PublicUrlBuilders;
import com.verygana2.testsupport.TestEntities;
import com.verygana2.utils.games.GameConfigStamper;
import com.verygana2.utils.games.GameResponseEnvelope;
import com.verygana2.utils.games.PreviewRewardSamples;
import com.verygana2.utils.validators.games.GameConfigValidator;

import jakarta.persistence.EntityManager;

/**
 * Premios del popup de un juego ({@code GameServiceImpl#getGameRewards}) sobre H2 con un Hibernate
 * real: el número de sentencias no depende de cuántos premios hay (CA-5) y las tarjetas conservan
 * sus campos, con el stock contado solo sobre unidades disponibles y vigentes (CA-6, CA-7).
 * Los aliados son de comercios y categorías distintos, para que cualquier carga por producto o por
 * comercio aparezca.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:game-rewards-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "CASE_INSENSITIVE_IDENTIFIERS=TRUE;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "financial.key-value-cents=1000",
        "cloudflare.r2.games-domain=games.test",
        "games.session-expiration-minutes=30"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ GameServiceImpl.class, ProductListingFactsResolver.class, TimeConfig.class,
        GameRewardsQueryCountIntegrationTest.UrlConfig.class })
@DisplayName("Premios del juego: consultas y campos (integración H2)")
class GameRewardsQueryCountIntegrationTest {

    @TestConfiguration
    static class UrlConfig {
        @Bean
        PublicUrlBuilder publicUrlBuilder() {
            return PublicUrlBuilders.forDomain("cdn.test");
        }
    }

    /** Presupuesto del plan: propios + aliados + carga del plan (proxy, rasgos) + hechos. */
    private static final long BUDGET = 8;
    private static final AtomicLong SEQ = new AtomicLong(1);

    @Autowired private GameServiceImpl gameService;
    @Autowired private EntityManager em;

    // Lo que no participa de los premios.
    @MockitoBean private GameConfigValidator gameConfigValidator;
    @MockitoBean private GameConfigStamper gameConfigStamper;
    @MockitoBean private GameResponseEnvelope gameResponseEnvelope;
    @MockitoBean private PreviewRewardSamples previewRewardSamples;
    @MockitoBean private KeyWalletService keyWalletService;
    @MockitoBean private KeyLedgerService keyLedgerService;
    @MockitoBean private LevelService levelService;
    @MockitoBean private CampaignScorer campaignScorer;
    @MockitoBean private CampaignScoringConfig campaignScoringConfig;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    }

    // ==================== FIXTURES ====================

    private record Seed(Product product, ProductCategory category, CommercialDetails owner, String imageKey,
            int stock) {
    }

    private record Measured(List<RewardCardResponseDTO> cards, long statements) {
    }

    private CommercialDetails premium() {
        CommercialDetails commercial = TestEntities.persistCommercial(em, PlanCode.PREMIUM);
        // El plan PREMIUM es uno solo en la prueba: la capacidad se le da una vez.
        Long features = em.createQuery("select count(pf) from PlanFeature pf where pf.plan = :plan", Long.class)
                .setParameter("plan", commercial.getCurrentPlan()).getSingleResult();
        if (features == 0) {
            TestEntities.persistBoolPlanFeature(em, commercial.getCurrentPlan(), "CAN_PROMOTE_ALLY_PRODUCTS", true);
        }
        return commercial;
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

    private Seed seedProduct(CommercialDetails owner, String name, boolean gameReward, int availableUnits) {
        long n = SEQ.getAndIncrement();
        ProductCategory category = new ProductCategory();
        category.setName("Categoria premio " + n);
        em.persist(category);

        Product product = new Product();
        product.setCommercial(owner);
        product.setProductCategory(category);
        product.setName(name);
        product.setDescription("Descripcion de " + name);
        product.setPriceCents(1_000_000L + n * 10_000L);
        product.setMaxKeysPct(20 + (int) (n % 3) * 10);
        em.persist(product);
        em.flush();
        product.setStatus(ProductStatus.ACTIVE);
        product.setIsGameReward(gameReward);
        em.flush();

        ProductImageAsset asset = new ProductImageAsset();
        asset.setObjectKey("products/" + UUID.randomUUID() + ".png");
        asset.setSizeBytes(1024L);
        asset.setStatus(AssetStatus.VALIDATED);
        asset.setProduct(product);
        em.persist(asset);

        addStock(product, StockStatus.AVAILABLE, null, availableUnits);
        return new Seed(product, category, owner, asset.getObjectKey(), availableUnits);
    }

    private void promote(CommercialDetails premium, Seed ally) {
        AllyProductPromotion promotion = new AllyProductPromotion();
        promotion.setPremiumCommercial(premium);
        promotion.setProduct(ally.product());
        em.persist(promotion);
        em.flush();
    }

    /** Un premio propio y los aliados que promociona, cada aliado de un comercio distinto. */
    private List<Seed> seedRewards(CommercialDetails premium, int own, int allies) {
        List<Seed> seeds = new ArrayList<>();
        for (int i = 0; i < own; i++) {
            seeds.add(seedProduct(premium, "Propio " + SEQ.get(), true, 20));
        }
        for (int i = 0; i < allies; i++) {
            Seed ally = seedProduct(TestEntities.persistCommercial(em), "Aliado " + SEQ.get(), false, 20);
            promote(premium, ally);
            seeds.add(ally);
        }
        return seeds;
    }

    /** Vacía el contexto, carga el comercial (como llega a getGameRewards) y cuenta las sentencias de la llamada. */
    private Measured measureRewards(Long commercialId) {
        em.flush();
        em.clear();
        CommercialDetails commercial = em.find(CommercialDetails.class, commercialId);
        statistics.clear();
        List<RewardCardResponseDTO> cards = gameService.getGameRewards(commercial);
        return new Measured(cards, statistics.getPrepareStatementCount());
    }

    private RewardCardResponseDTO byId(List<RewardCardResponseDTO> cards, Long id) {
        return cards.stream().filter(c -> c.getId().equals(id)).findFirst().orElseThrow();
    }

    private static String formatNumber(long number) {
        return String.format("%,d", number).replace(",", ".");
    }

    // ==================== TESTS ====================

    @Test
    @DisplayName("mismas sentencias con 1 premio propio y 1 aliado que con 3 propios y 3 aliados de comercios distintos")
    void sameQueriesForOneAndThreeOwnRewardsPlusAllies() {
        CommercialDetails small = premium();
        seedRewards(small, 1, 1);
        CommercialDetails large = premium();
        seedRewards(large, 3, 3);

        Measured one = measureRewards(small.getId());
        Measured many = measureRewards(large.getId());

        assertThat(one.cards()).hasSize(2);
        assertThat(many.cards()).hasSize(6);
        assertThat(many.statements())
                .as("sentencias con 6 premios (con 2: %d)", one.statements())
                .isEqualTo(one.statements());
        assertThat(many.statements())
                .as("sentencias con 6 premios; presupuesto del plan: %d", BUDGET)
                .isLessThanOrEqualTo(BUDGET);
    }

    @Test
    @DisplayName("cada tarjeta conserva todos sus campos, propios y aliados")
    void cardsUnchanged() {
        CommercialDetails premium = premium();
        List<Seed> seeds = seedRewards(premium, 2, 2);

        Measured result = measureRewards(premium.getId());

        assertThat(result.cards()).hasSize(4);
        for (Seed seed : seeds) {
            Product p = seed.product();
            RewardCardResponseDTO expected = RewardCardResponseDTO.builder()
                    .id(p.getId())
                    .name(p.getName())
                    .image_url("https://cdn.test/public/" + seed.imageKey())
                    .image_message(p.getMaxKeysPct() + "% Descuento")
                    .regular_price(p.getPriceCents() / 100)
                    .keys_message("Con [[" + formatNumber(p.getMaxKeysAllowed()) + "]] llaves pagas [[SOLO $"
                            + formatNumber(p.getMinCashCents() / 100) + " COP]]")
                    .commercial(seed.owner().getCompanyName())
                    .rating(0.0)
                    .max_keys_allowed(p.getMaxKeysAllowed())
                    .min_cash_cents(p.getMinCashCents())
                    .stock(seed.stock())
                    .category_name(seed.category().getName())
                    .build();
            assertThat(byId(result.cards(), p.getId())).usingRecursiveComparison().isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("el stock solo cuenta unidades disponibles y no vencidas")
    void mixedStock() {
        CommercialDetails premium = premium();
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        Seed seed = seedProduct(premium, "Mixto", true, 0);
        Product product = seed.product();
        addStock(product, StockStatus.AVAILABLE, null, 2);
        addStock(product, StockStatus.AVAILABLE, now.plusDays(5), 3);
        addStock(product, StockStatus.SOLD, null, 4);
        addStock(product, StockStatus.RESERVED, null, 1);
        addStock(product, StockStatus.INVALID, null, 1);
        addStock(product, StockStatus.AVAILABLE, now.minusDays(1), 2);

        Measured result = measureRewards(premium.getId());

        assertThat(byId(result.cards(), product.getId()).getStock()).isEqualTo(5);
    }

    @Test
    @DisplayName("un premio sin unidades muestra stock 0, no desaparece")
    void rewardWithoutStockShowsZero() {
        CommercialDetails premium = premium();
        Seed seed = seedProduct(premium, "Sin stock", true, 0);

        Measured result = measureRewards(premium.getId());

        assertThat(byId(result.cards(), seed.product().getId()).getStock()).isZero();
    }
}
