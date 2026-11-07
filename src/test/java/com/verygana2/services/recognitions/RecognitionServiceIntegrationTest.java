package com.verygana2.services.recognitions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.verygana2.config.TreasuryConfig;
import com.verygana2.dtos.recognitions.CreateRecognitionRequestDTO;
import com.verygana2.dtos.recognitions.RecognitionDetailDTO;
import com.verygana2.event.PurchaseItemClaimedEvent;
import com.verygana2.exceptions.BusinessException;
import com.verygana2.exceptions.InvalidStatusException;
import com.verygana2.models.enums.AccountStatus;
import com.verygana2.models.enums.CommercialActivityType;
import com.verygana2.models.enums.finance.TreasuryAccountCode;
import com.verygana2.models.enums.marketplace.ComputableSaleStatus;
import com.verygana2.models.enums.marketplace.ProductStatus;
import com.verygana2.models.enums.marketplace.ProductType;
import com.verygana2.models.enums.marketplace.PurchaseBuyerType;
import com.verygana2.models.enums.marketplace.PurchaseItemStatus;
import com.verygana2.models.enums.marketplace.SaleComponentStatus;
import com.verygana2.models.enums.marketplace.SaleComponentType;
import com.verygana2.models.enums.marketplace.SalePayerType;
import com.verygana2.models.enums.marketplace.StockStatus;
import com.verygana2.models.enums.recognitions.RecognitionCriterion;
import com.verygana2.models.enums.recognitions.RecognitionStatus;
import com.verygana2.models.finance.TreasuryAccount;
import com.verygana2.models.finance.Wallet;
import com.verygana2.models.finance.plans.Investment;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.finance.plans.PlanFeature;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductCategory;
import com.verygana2.models.marketplace.ProductStock;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.marketplace.SaleComponent;
import com.verygana2.models.recognitions.Recognition;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.finance.PayoutItemRepository;
import com.verygana2.repositories.finance.prosperity.ProsperityAccountRepository;
import com.verygana2.repositories.finance.prosperity.ProsperityLedgerEntryRepository;
import com.verygana2.repositories.marketplace.ProductStockRepository;
import com.verygana2.repositories.marketplace.SaleComponentRepository;
import com.verygana2.repositories.recognitions.RecognitionRepository;
import com.verygana2.security.ProductCodeEncryptor;
import com.verygana2.services.UserIdResolver;
import com.verygana2.services.finance.KeyBackingCalculator;
import com.verygana2.services.finance.ProsperityServiceImpl;
import com.verygana2.services.finance.TreasuryServiceImpl;
import com.verygana2.services.interfaces.EmailService;
import com.verygana2.services.interfaces.details.ConsumerDetailsService;
import com.verygana2.services.interfaces.marketplace.ProductService;
import com.verygana2.services.marketplace.CommissionPolicy;
import com.verygana2.services.marketplace.PurchaseDeliverer;
import com.verygana2.services.marketplace.PurchaseItemAssembler;
import com.verygana2.services.marketplace.SaleComponentRecorder;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Compra de VERYGANA para un reconocimiento (Contrato B 10.16 y 5.9), de punta a punta con
 * Prosperidad, tesorería y componentes de venta reales sobre H2. Los montos son los
 * ejemplos del PDF: producto de $100.000 de un empresario de PRODUCTOS (10 %).
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:recognitions-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "CASE_INSENSITIVE_IDENTIFIERS=TRUE;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        // V1__baseline.sql es MySQL puro: el esquema de prueba lo genera Hibernate.
        "spring.flyway.enabled=false",
        "financial.key-value-cents=1000"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ RecognitionServiceImpl.class, InstitutionalPurchaseReverser.class, PurchaseItemAssembler.class,
        PurchaseDeliverer.class, SaleComponentRecorder.class, CommissionPolicy.class, ProsperityServiceImpl.class,
        com.verygana2.mappers.finance.ProsperityMapperImpl.class, TreasuryServiceImpl.class, UserIdResolver.class })
@DisplayName("RecognitionService — compras de VERYGANA para reconocimientos (integración H2)")
class RecognitionServiceIntegrationTest {

    private static final long PRICE = 10_000_000L;          // $100.000
    private static final long FUND = 50_000_000L;           // $500.000 en FORTIFICATION
    private static final Long ADMIN = 1L;
    private static final AtomicLong SEQ = new AtomicLong(1);

    @Autowired private RecognitionServiceImpl service;
    @Autowired private InstitutionalPurchaseReverser reverser;
    @Autowired private ProsperityServiceImpl prosperityService;
    @Autowired private EntityManager em;
    @Autowired private RecognitionRepository recognitionRepository;
    @Autowired private SaleComponentRepository saleComponentRepository;
    @Autowired private ProsperityAccountRepository prosperityAccountRepository;
    @Autowired private ProsperityLedgerEntryRepository ledgerRepository;
    @Autowired private ApplicationEventPublisher eventPublisher;

    @MockitoBean private TreasuryConfig treasuryConfig;
    @MockitoBean private KeyBackingCalculator keyBackingCalculator;
    @MockitoBean private ProductService productService;
    @MockitoBean private ProductStockRepository productStockRepository;
    @MockitoBean private ConsumerDetailsService consumerDetailsService;
    @MockitoBean private PayoutItemRepository payoutItemRepository;
    @MockitoBean private EmailService emailService;
    @MockitoBean private PasswordEncoder passwordEncoder;
    @MockitoBean private ProductCodeEncryptor productCodeEncryptor;
    @MockitoBean private com.verygana2.storage.service.PublicUrlBuilder publicUrlBuilder;

    private ConsumerDetails beneficiary;

    @BeforeEach
    void setUp() {
        when(treasuryConfig.getVatPct()).thenReturn(19);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-pin");
        when(consumerDetailsService.getConsumerById(anyLong()))
                .thenAnswer(inv -> em.find(ConsumerDetails.class, inv.getArgument(0, Long.class)));
        // El repositorio real usa FOR UPDATE SKIP LOCKED, que H2 no entiende.
        when(productStockRepository.findNextAvailableForProduct(anyLong())).thenAnswer(inv -> {
            Product product = em.find(Product.class, inv.getArgument(0, Long.class));
            return product.getStockItems().stream().filter(s -> s.getStatus() == StockStatus.AVAILABLE).findFirst();
        });
        when(productStockRepository.save(any(ProductStock.class))).thenAnswer(inv -> inv.getArgument(0));

        for (TreasuryAccountCode code : TreasuryAccountCode.values()) {
            TestEntities.persistTreasuryAccount(em, code, code == TreasuryAccountCode.FORTIFICATION ? FUND : 0L);
        }
        beneficiary = TestEntities.persistConsumer(em);
    }

    // ─── Fixtures ─────────────────────────────────────────────────────────────

    /** Empresario STANDARD de PRODUCTOS (10 %) con Saldo de Prosperidad = {@code balanceCents}. */
    private CommercialDetails standardCommercial(long balanceCents) {
        CommercialDetails commercial = TestEntities.persistCommercial(em, PlanCode.STANDARD);
        commercial.setCommercialActivityType(CommercialActivityType.PRODUCTS);
        Plan plan = commercial.getCurrentPlan();
        plan.setSaleCommissionPct(10);
        boolean hasMultiplier = plan.getFeatures().stream()
                .anyMatch(pf -> pf.getFeature().getCode().equals("PROSPERITY_THRESHOLD_MULTIPLIER"));
        if (!hasMultiplier) {
            PlanFeature pf = TestEntities.persistIntPlanFeature(em, plan, "PROSPERITY_THRESHOLD_MULTIPLIER", 4);
            plan.getFeatures().add(pf);
        }
        Wallet wallet = TestEntities.persistWallet(em, commercial, 0L);
        if (balanceCents > 0) {
            Investment investment = Investment.builder()
                    .wallet(wallet)
                    .planAtDeposit(plan)
                    .depositAmountCents(balanceCents / 4)
                    .vatAmountCents(0L)
                    .confirmed(true)
                    .confirmedAt(ZonedDateTime.now(ZoneOffset.UTC))
                    .build();
            em.persist(investment);
            em.flush();
            prosperityService.generateThreshold(investment);
        }
        em.flush();
        return commercial;
    }

    private Product product(CommercialDetails commercial, ProductType type, int stock) {
        long n = SEQ.getAndIncrement();
        ProductCategory category = new ProductCategory();
        category.setName("categoria-reconocimiento-" + n);
        em.persist(category);

        Product product = new Product();
        product.setCommercial(commercial);
        product.setProductCategory(category);
        product.setName("Producto reconocimiento " + n);
        product.setPriceCents(PRICE);
        product.setMaxKeysPct(50);
        product.setProductType(type);
        em.persist(product);
        product.setStatus(ProductStatus.ACTIVE);
        for (int i = 0; i < stock; i++) {
            ProductStock s = ProductStock.builder().product(product).code("cifrado-" + n + "-" + i)
                    .codeHash("hash-" + n + "-" + i).status(StockStatus.AVAILABLE).build();
            em.persist(s);
            product.getStockItems().add(s);
        }
        em.flush();
        when(productService.getById(product.getId())).thenReturn(product);
        return product;
    }

    private CreateRecognitionRequestDTO request(Product product, int quantity) {
        return new CreateRecognitionRequestDTO(beneficiary.getUser().getPublicId(), product.getId(), quantity,
                RecognitionCriterion.KEYS_EARNED, 1_500L, ZonedDateTime.now(ZoneOffset.UTC).minusDays(30),
                ZonedDateTime.now(ZoneOffset.UTC), "Usuario con más llaves ganadas del mes");
    }

    private long treasury(TreasuryAccountCode code) {
        return em.createQuery("select t from TreasuryAccount t where t.code = :c", TreasuryAccount.class)
                .setParameter("c", code).getSingleResult().getBalanceCents();
    }

    private long prosperityBalance(CommercialDetails commercial) {
        return prosperityAccountRepository.findByCommercialId(commercial.getId())
                .map(a -> a.getBalanceCents()).orElse(0L);
    }

    private Purchase purchaseOf(RecognitionDetailDTO dto) {
        return em.find(Purchase.class, dto.purchaseId());
    }

    private String key() {
        return "clave-" + UUID.randomUUID();
    }

    // ─── Escenarios ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("compra y reconocimiento económico")
    class Purchasing {

        @Test
        @DisplayName("absorción parcial (PDF 10.16 F): V=100.000, S0=30.000 → A=30.000, B=70.000, C=7.000")
        void partialAbsorption() {
            CommercialDetails commercial = standardCommercial(3_000_000L);
            Product product = product(commercial, ProductType.DIGITAL, 3);

            RecognitionDetailDTO dto = service.create(request(product, 1), ADMIN, key());

            assertThat(dto.prosperityAbsorbedCents()).isEqualTo(3_000_000L);
            assertThat(dto.items().get(0).commissionBaseCents()).isEqualTo(7_000_000L);
            assertThat(dto.commissionCents()).isEqualTo(700_000L);
            assertThat(prosperityBalance(commercial)).isZero();

            // El fondo paga el precio una sola vez y la comisión sale de esa venta.
            assertThat(treasury(TreasuryAccountCode.FORTIFICATION)).isEqualTo(FUND - PRICE);
            assertThat(treasury(TreasuryAccountCode.PAYOUTS_PENDING)).isEqualTo(PRICE - 700_000L);
            // La comisión ya trae IVA: 700.000 × 19 / 119 = 111.764,7 → 111.765 a TAX_RESERVE.
            assertThat(treasury(TreasuryAccountCode.TAX_RESERVE)).isEqualTo(111_765L);
            assertThat(treasury(TreasuryAccountCode.OPERATIONS)).isEqualTo(588_235L);
            // La ganancia que muestra el ítem es exactamente lo que llegó a OPERATIONS.
            assertThat(dto.items().get(0).commissionNetOfVatCents()).isEqualTo(588_235L);
            assertThat(purchaseOf(dto).getCommissionNetOfVatCents()).isEqualTo(588_235L);
        }

        @Test
        @DisplayName("Prosperidad suficiente (PDF 10.16 F): S0=150.000 → comisión 0 y saldo posterior 50.000")
        void fullAbsorption() {
            CommercialDetails commercial = standardCommercial(15_000_000L);
            Product product = product(commercial, ProductType.DIGITAL, 1);

            RecognitionDetailDTO dto = service.create(request(product, 1), ADMIN, key());

            assertThat(dto.prosperityAbsorbedCents()).isEqualTo(PRICE);
            assertThat(dto.commissionCents()).isZero();
            assertThat(prosperityBalance(commercial)).isEqualTo(5_000_000L);
            // La compra y su entrega conservan sus registros aunque no generen comisión.
            assertThat(dto.items()).hasSize(1);
            assertThat(dto.status()).isEqualTo(RecognitionStatus.DELIVERED);
        }

        @Test
        @DisplayName("roles separados: VERYGANA compra, el empresario provee y el beneficiario no es comprador ni pagador")
        void rolesAreSeparated() {
            CommercialDetails commercial = standardCommercial(0L);
            Product product = product(commercial, ProductType.DIGITAL, 1);

            RecognitionDetailDTO dto = service.create(request(product, 1), ADMIN, key());
            Purchase purchase = purchaseOf(dto);

            assertThat(purchase.getBuyerType()).isEqualTo(PurchaseBuyerType.VERYGANA);
            assertThat(purchase.getConsumer()).isNull();
            assertThat(purchase.getBeneficiary().getId()).isEqualTo(beneficiary.getId());
            // Cero dinero y cero llaves del beneficiario.
            assertThat(purchase.getCashCents()).isZero();
            assertThat(purchase.getKeysValueCents()).isZero();

            List<SaleComponent> components = saleComponentRepository
                    .findByPurchaseItemIdOrderByIdAsc(purchase.getItems().get(0).getId());
            assertThat(components).singleElement().satisfies(c -> {
                assertThat(c.getComponentType()).isEqualTo(SaleComponentType.VERYGANA_PAYMENT);
                assertThat(c.getPayerType()).isEqualTo(SalePayerType.VERYGANA);
                assertThat(c.getAmountCents()).isEqualTo(PRICE);
                assertThat(c.getSourceRef()).isEqualTo(dto.fundingRef().toString());
            });
            assertThat(purchase.getItems().get(0).getComputableStatus()).isEqualTo(ComputableSaleStatus.RECOGNIZED);
        }

        @Test
        @DisplayName("misma tarifa: la comisión del reconocimiento es la de una venta ordinaria del mismo empresario")
        void sameRateAsOrdinarySale() {
            CommercialDetails commercial = standardCommercial(0L);
            Product product = product(commercial, ProductType.DIGITAL, 1);

            RecognitionDetailDTO dto = service.create(request(product, 1), ADMIN, key());

            assertThat(dto.items().get(0).commissionPctApplied())
                    .isEqualTo(new CommissionPolicy().commissionPctFor(commercial));
            assertThat(dto.commissionCents()).isEqualTo(1_000_000L);
        }

        @Test
        @DisplayName("fuente y pago del mismo recurso: $100.000 de financiación, no $200.000, y la conciliación cuadra")
        void fundingCountedOnce() {
            CommercialDetails commercial = standardCommercial(0L);
            Product product = product(commercial, ProductType.DIGITAL, 2);

            service.create(request(product, 2), ADMIN, key());

            assertThat(FUND - treasury(TreasuryAccountCode.FORTIFICATION)).isEqualTo(2 * PRICE);
            assertThat(service.reconcileFunding()).isZero();
        }

        @Test
        @DisplayName("reintento con la misma clave: devuelve el mismo reconocimiento y el fondo paga una sola vez")
        void retryRecoversExistingPurchase() {
            CommercialDetails commercial = standardCommercial(0L);
            Product product = product(commercial, ProductType.DIGITAL, 3);
            String key = key();

            RecognitionDetailDTO first = service.create(request(product, 1), ADMIN, key);
            RecognitionDetailDTO second = service.create(request(product, 1), ADMIN, key);

            assertThat(second.publicId()).isEqualTo(first.publicId());
            assertThat(recognitionRepository.count()).isEqualTo(1);
            assertThat(treasury(TreasuryAccountCode.FORTIFICATION)).isEqualTo(FUND - PRICE);
        }
    }

    @Nested
    @DisplayName("controles")
    class Controls {

        @Test
        @DisplayName("fondo insuficiente (95.000 para 100.000): se rechaza y no queda stock reservado ni compra")
        void insufficientFundRejectsWholePurchase() {
            TestEntities.persistTreasuryAccount(em, TreasuryAccountCode.FORTIFICATION, 9_500_000L);
            CommercialDetails commercial = standardCommercial(0L);
            Product product = product(commercial, ProductType.DIGITAL, 1);

            assertThatThrownBy(() -> service.create(request(product, 1), ADMIN, key()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("fondo de fortalecimiento");

            assertThat(product.getStockItems().get(0).getStatus()).isEqualTo(StockStatus.AVAILABLE);
            assertThat(recognitionRepository.count()).isZero();
            assertThat(treasury(TreasuryAccountCode.FORTIFICATION)).isEqualTo(9_500_000L);
        }

        @Test
        @DisplayName("beneficiario sin cuenta activa: se rechaza")
        void inactiveBeneficiaryRejected() {
            beneficiary.getUser().setAccountStatus(AccountStatus.SUSPENDED);
            CommercialDetails commercial = standardCommercial(0L);
            Product product = product(commercial, ProductType.DIGITAL, 1);

            assertThatThrownBy(() -> service.create(request(product, 1), ADMIN, key()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("activa");
        }

        @Test
        @DisplayName("pago confirmado no es entrega: un producto físico queda PURCHASED hasta que se valida el PIN")
        void physicalStaysPurchasedUntilClaim() {
            CommercialDetails commercial = standardCommercial(0L);
            Product product = product(commercial, ProductType.PHYSICAL, 1);

            RecognitionDetailDTO dto = service.create(request(product, 1), ADMIN, key());

            assertThat(dto.status()).isEqualTo(RecognitionStatus.PURCHASED);
            assertThat(dto.items().get(0).status()).isEqualTo(PurchaseItemStatus.PENDING);
            assertThat(dto.items().get(0).computableStatus()).isEqualTo(ComputableSaleStatus.RECOGNIZED);
        }

        @Test
        @DisplayName("la entrega posterior actualiza el cumplimiento sin otra venta, otra absorción ni otra comisión")
        void deliveryDoesNotCreateAnotherSale() {
            CommercialDetails commercial = standardCommercial(3_000_000L);
            Product product = product(commercial, ProductType.PHYSICAL, 1);
            RecognitionDetailDTO dto = service.create(request(product, 1), ADMIN, key());
            long ledgerEntries = ledgerRepository.count();
            long components = saleComponentRepository.count();
            long payoutsPending = treasury(TreasuryAccountCode.PAYOUTS_PENDING);

            PurchaseItem item = purchaseOf(dto).getItems().get(0);
            item.setStatus(PurchaseItemStatus.CLAIMED);
            eventPublisher.publishEvent(new PurchaseItemClaimedEvent(this, dto.purchaseId(), item.getId()));
            eventPublisher.publishEvent(new PurchaseItemClaimedEvent(this, dto.purchaseId(), item.getId()));

            assertThat(service.get(dto.publicId()).status()).isEqualTo(RecognitionStatus.DELIVERED);
            assertThat(ledgerRepository.count()).isEqualTo(ledgerEntries);
            assertThat(saleComponentRepository.count()).isEqualTo(components);
            assertThat(treasury(TreasuryAccountCode.PAYOUTS_PENDING)).isEqualTo(payoutsPending);
        }
    }

    @Nested
    @DisplayName("candidatos")
    class Candidates {

        private void levelProfile(ConsumerDetails consumer, long xp) {
            em.persist(com.verygana2.models.levels.UserLevelProfile.builder()
                    .consumer(consumer)
                    .xpTotal(xp)
                    .lastActivityAt(java.time.LocalDateTime.now())
                    .build());
            em.flush();
        }

        @Test
        @DisplayName("ranking por XP: ordena de mayor a menor, excluye cuentas no activas y cuenta reconocimientos recibidos")
        void rankByXp() {
            ConsumerDetails top = TestEntities.persistConsumer(em);
            ConsumerDetails suspended = TestEntities.persistConsumer(em);
            suspended.getUser().setAccountStatus(AccountStatus.SUSPENDED);
            levelProfile(beneficiary, 500);
            levelProfile(top, 900);
            levelProfile(suspended, 5_000);
            CommercialDetails commercial = standardCommercial(0L);
            service.create(request(product(commercial, ProductType.DIGITAL, 1), 1), ADMIN, key());

            var page = service.candidates(RecognitionCriterion.XP, null, null,
                    org.springframework.data.domain.PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(c -> c.publicId())
                    .containsExactly(top.getUser().getPublicId(), beneficiary.getUser().getPublicId());
            assertThat(page.getContent().get(1).recognitionsReceived()).isEqualTo(1);
            assertThat(page.getContent().get(0).recognitionsReceived()).isZero();
        }

        @Test
        @DisplayName("criterios con periodo: sin fechas se rechaza")
        void periodRequired() {
            assertThatThrownBy(() -> service.candidates(RecognitionCriterion.KEYS_EARNED, null, null,
                    org.springframework.data.domain.PageRequest.of(0, 10)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("anulación y vencimiento")
    class Reversal {

        @Test
        @DisplayName("anular: el precio vuelve al fondo, la comisión se revierte, el Saldo se reintegra y el original se conserva")
        void cancelReturnsEverything() {
            CommercialDetails commercial = standardCommercial(3_000_000L);
            Product product = product(commercial, ProductType.DIGITAL, 1);
            RecognitionDetailDTO dto = service.create(request(product, 1), ADMIN, key());

            RecognitionDetailDTO cancelled = service.cancel(dto.publicId(), "Usuario reportó fraude", ADMIN);

            assertThat(cancelled.status()).isEqualTo(RecognitionStatus.CANCELLED);
            assertThat(treasury(TreasuryAccountCode.FORTIFICATION)).isEqualTo(FUND);
            assertThat(treasury(TreasuryAccountCode.PAYOUTS_PENDING)).isZero();
            assertThat(treasury(TreasuryAccountCode.OPERATIONS)).isZero();
            assertThat(treasury(TreasuryAccountCode.TAX_RESERVE)).isZero();
            assertThat(prosperityBalance(commercial)).isEqualTo(3_000_000L);

            RecognitionDetailDTO.Item item = cancelled.items().get(0);
            assertThat(item.status()).isEqualTo(PurchaseItemStatus.REFUNDED);
            assertThat(item.computableStatus()).isEqualTo(ComputableSaleStatus.REVERSED);
            assertThat(item.components()).extracting(RecognitionDetailDTO.Component::status)
                    .containsExactly(SaleComponentStatus.RECOGNIZED, SaleComponentStatus.REVERSED);
            assertThat(service.reconcileFunding()).isZero();
        }

        @Test
        @DisplayName("no se anula si un ítem ya se le pagó al comercio")
        void cannotCancelPaidOut() {
            CommercialDetails commercial = standardCommercial(0L);
            Product product = product(commercial, ProductType.DIGITAL, 1);
            RecognitionDetailDTO dto = service.create(request(product, 1), ADMIN, key());
            when(payoutItemRepository.existsByPurchaseItemId(anyLong())).thenReturn(true);

            assertThatThrownBy(() -> service.cancel(dto.publicId(), "tarde", ADMIN))
                    .isInstanceOf(InvalidStatusException.class);
            assertThat(treasury(TreasuryAccountCode.FORTIFICATION)).isEqualTo(FUND - PRICE);
        }

        @Test
        @DisplayName("físico vencido sin reclamar: el precio vuelve al fondo y el reconocimiento queda EXPIRED")
        void expiredPhysicalReturnsToFund() {
            CommercialDetails commercial = standardCommercial(0L);
            Product product = product(commercial, ProductType.PHYSICAL, 1);
            RecognitionDetailDTO dto = service.create(request(product, 1), ADMIN, key());
            PurchaseItem item = purchaseOf(dto).getItems().get(0);

            reverser.reverse(item, "EXPIRED_UNCLAIMED");
            item.setStatus(PurchaseItemStatus.EXPIRED_UNCLAIMED);
            Optional<Recognition> recognition = recognitionRepository.findByPurchaseId(dto.purchaseId());
            reverser.settleStatus(recognition.orElseThrow());

            assertThat(recognition.get().getStatus()).isEqualTo(RecognitionStatus.EXPIRED);
            assertThat(treasury(TreasuryAccountCode.FORTIFICATION)).isEqualTo(FUND);
            // Idempotente: un segundo intento no mueve dinero.
            assertThat(reverser.reverse(item, "EXPIRED_UNCLAIMED")).isFalse();
            assertThat(treasury(TreasuryAccountCode.FORTIFICATION)).isEqualTo(FUND);
        }
    }
}
