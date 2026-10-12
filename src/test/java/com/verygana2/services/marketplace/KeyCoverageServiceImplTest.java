package com.verygana2.services.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.verygana2.dtos.purchase.requests.CreatePurchaseItemRequestDTO;
import com.verygana2.dtos.purchase.requests.KeyCoverageQuoteRequestDTO;
import com.verygana2.dtos.purchase.responses.KeyCoverageQuoteResponseDTO;
import com.verygana2.models.enums.marketplace.ProductStatus;
import com.verygana2.models.finance.KeyWallet;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.promotions.KeyCoveragePromotion;
import com.verygana2.models.marketplace.promotions.KeyCoveragePromotionOffer;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.finance.KeyWalletRepository;
import com.verygana2.repositories.marketplace.KeyCoveragePromotionOfferRepository;
import com.verygana2.repositories.marketplace.PurchaseItemRepository;
import com.verygana2.services.interfaces.finance.KeyLedgerService;
import com.verygana2.services.interfaces.finance.TreasuryService;
import com.verygana2.services.interfaces.marketplace.ProductService;
import com.verygana2.testsupport.KeyLedgerStubs;

/**
 * Porción en Llaves (Contrato B 2.26, 6.22.8, 7.9). Los casos siguen las pruebas mínimas
 * del contrato sobre una oferta de $100.000: 0 %, 50 %, 60 %, 70 %, saldo menor, igual y
 * superior al límite, respaldo insuficiente y el usuario que pide menos que el máximo.
 * 1 llave = 1.000 centavos.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KeyCoverageServiceImpl")
class KeyCoverageServiceImplTest {

    private static final long KEY = 1_000L;
    private static final long PRICE = 10_000_000L; // $100.000
    private static final Long CONSUMER = 9L;
    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 10, 7, 12, 0, 0, 0, ZoneOffset.UTC);

    @Mock private KeyCoveragePromotionOfferRepository offerRepository;
    @Mock private PurchaseItemRepository purchaseItemRepository;
    @Mock private KeyWalletRepository keyWalletRepository;
    @Mock private KeyLedgerService keyLedgerService;
    @Mock private TreasuryService treasuryService;
    @Mock private ProductService productService;

    private KeyCoverageServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new KeyCoverageServiceImpl(offerRepository, purchaseItemRepository, keyWalletRepository,
                keyLedgerService, treasuryService, productService, Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "keyValueCents", KEY);
        KeyLedgerStubs.applyToWallet(keyLedgerService);
        lenient().when(treasuryService.keysReserveHeadroomCents()).thenReturn(1_000_000_000L);
    }

    // ─── Fixtures ─────────────────────────────────────────────────────────────

    private Product product(long id, int maxKeysPct) {
        Product product = new Product();
        product.setId(id);
        product.setName("Producto " + id);
        product.setStatus(ProductStatus.ACTIVE);
        product.setPriceCents(PRICE);
        product.setMaxKeysPct(maxKeysPct);
        CommercialDetails commercial = new CommercialDetails();
        commercial.setId(50L);
        product.setCommercial(commercial);
        return product;
    }

    private KeyCoveragePromotion promotion(long id, int pct, long budgetCents, Long maxPerUser) {
        KeyCoveragePromotion promotion = KeyCoveragePromotion.draft(new KeyCoveragePromotion.Terms(
                "Promo " + pct, pct, NOW.minusDays(1), NOW.plusDays(5), budgetCents, maxPerUser, null));
        ReflectionTestUtils.setField(promotion, "id", id);
        promotion.authorize(1L, NOW.minusDays(2));
        return promotion;
    }

    private void givenLivePromotion(Product product, KeyCoveragePromotion promotion) {
        when(offerRepository.findLiveByProductIds(anyCollection(), any()))
                .thenReturn(List.of(KeyCoveragePromotionOffer.invite(promotion, product)));
    }

    private void givenWallet(long keys) {
        when(keyWalletRepository.findByConsumerId(CONSUMER))
                .thenReturn(Optional.of(KeyWallet.builder().purchaseKeysCents(keys * KEY).build()));
    }

    private KeyCoverageQuoteResponseDTO quote(Product product, int quantity) {
        when(productService.getById(product.getId())).thenReturn(product);
        return service.quote(CONSUMER, KeyCoverageQuoteRequestDTO.builder()
                .items(List.of(CreatePurchaseItemRequestDTO.builder()
                        .productId(product.getId()).quantity(quantity).build()))
                .build());
    }

    // ─── Tope de cobertura ────────────────────────────────────────────────────

    @Nested
    @DisplayName("tope de cobertura por unidad")
    class Limits {

        @Test
        @DisplayName("ordinario 50 %: oferta de $100.000 → hasta $50.000 en llaves")
        void ordinaryFiftyPercent() {
            KeyCoveragePlan plan = service.planFor(CONSUMER, List.of(product(1, 50)));

            assertThat(plan.maxCoverageCents()).isEqualTo(5_000_000L);
            assertThat(plan.units().get(0).pctApplied()).isEqualTo(50);
            assertThat(plan.units().get(0).promotionId()).isNull();
        }

        @Test
        @DisplayName("0 %: un producto sin cobertura no admite llaves")
        void zeroPercent() {
            assertThat(service.planFor(CONSUMER, List.of(product(1, 0))).maxCoverageCents()).isZero();
        }

        @Test
        @DisplayName("el plan BASIC sigue en 20 %: el ordinario es un tope, no un valor fijo para todos")
        void basicPlanKeepsItsOwnLimit() {
            assertThat(service.planFor(CONSUMER, List.of(product(1, 20))).maxCoverageCents()).isEqualTo(2_000_000L);
        }

        @Test
        @DisplayName("sin promoción nunca pasa de 50 %, aunque el producto traiga un porcentaje mayor")
        void ordinaryIsCappedAtFifty() {
            KeyCoveragePlan plan = service.planFor(CONSUMER, List.of(product(1, 65)));

            assertThat(plan.maxCoverageCents()).isEqualTo(5_000_000L);
            assertThat(plan.units().get(0).pctApplied()).isEqualTo(50);
        }

        @Test
        @DisplayName("especial 60 %: hasta $60.000")
        void specialSixtyPercent() {
            Product product = product(1, 50);
            givenLivePromotion(product, promotion(7, 60, 100_000_000L, null));

            assertThat(service.planFor(CONSUMER, List.of(product)).maxCoverageCents()).isEqualTo(6_000_000L);
        }

        @Test
        @DisplayName("especial autorizada al 70 %: hasta $70.000, con $20.000 de margen de la promoción")
        void specialSeventyPercent() {
            Product product = product(1, 50);
            givenLivePromotion(product, promotion(7, 70, 100_000_000L, null));

            KeyCoveragePlan plan = service.planFor(CONSUMER, List.of(product));

            assertThat(plan.maxCoverageCents()).isEqualTo(7_000_000L);
            assertThat(plan.maxPromotionExcessCents()).isEqualTo(2_000_000L);
            assertThat(plan.units().get(0).promotionId()).isEqualTo(7L);
            assertThat(plan.units().get(0).pctApplied()).isEqualTo(70);
        }

        @Test
        @DisplayName("oferta no incluida: la promoción de un producto no se extiende a otro")
        void promotionDoesNotExtendToOtherProducts() {
            Product promoted = product(1, 50);
            Product other = product(2, 50);
            givenLivePromotion(promoted, promotion(7, 70, 100_000_000L, null));

            KeyCoveragePlan plan = service.planFor(CONSUMER, List.of(promoted, other));

            assertThat(plan.units().get(0).limitCents()).isEqualTo(7_000_000L);
            assertThat(plan.units().get(1).limitCents()).isEqualTo(5_000_000L);
            assertThat(plan.units().get(1).promotionId()).isNull();
        }

        @Test
        @DisplayName("agotamiento: sin presupuesto para el margen de la unidad, rige el tope ordinario")
        void exhaustedBudgetFallsBackToOrdinary() {
            Product product = product(1, 50);
            // El margen de una unidad son $20.000 y solo quedan $19.999.
            givenLivePromotion(product, promotion(7, 70, 1_999_900L, null));

            KeyCoveragePlan plan = service.planFor(CONSUMER, List.of(product));

            assertThat(plan.maxCoverageCents()).isEqualTo(5_000_000L);
            assertThat(plan.units().get(0).promotionId()).isNull();
        }

        @Test
        @DisplayName("el presupuesto se reparte entre las unidades de la misma compra: alcanza para una, no para dos")
        void budgetIsSharedAcrossUnitsOfThePurchase() {
            Product product = product(1, 50);
            givenLivePromotion(product, promotion(7, 70, 3_000_000L, null));

            KeyCoveragePlan plan = service.planFor(CONSUMER, List.of(product, product));

            assertThat(plan.units().get(0).limitCents()).isEqualTo(7_000_000L);
            assertThat(plan.units().get(1).limitCents()).isEqualTo(5_000_000L);
        }

        @Test
        @DisplayName("límite por usuario: quien ya lo consumió vuelve al tope ordinario")
        void perUserLimitFallsBackToOrdinary() {
            Product product = product(1, 50);
            givenLivePromotion(product, promotion(7, 70, 100_000_000L, 3_000_000L));
            when(purchaseItemRepository.sumPromotionExcessByConsumer(7L, CONSUMER)).thenReturn(2_000_000L);

            KeyCoveragePlan plan = service.planFor(CONSUMER, List.of(product));

            // Le quedan $10.000 de margen y la unidad necesita $20.000.
            assertThat(plan.maxCoverageCents()).isEqualTo(5_000_000L);
        }
    }

    // ─── Cotización ───────────────────────────────────────────────────────────

    /**
     * La cotización da los límites; el dinero a pagar para las llaves que el usuario
     * elija lo calcula el cliente con totalCents y keyValueCents.
     */
    @Nested
    @DisplayName("cotización previa a la compra")
    class Quote {

        @Test
        @DisplayName("saldo igual al tope: puede usar las 5.000 llaves del tope")
        void balanceEqualToLimit() {
            givenWallet(5_000);

            KeyCoverageQuoteResponseDTO quote = quote(product(1, 50), 1);

            assertThat(quote.totalCents()).isEqualTo(PRICE);
            assertThat(quote.keyValueCents()).isEqualTo(KEY);
            assertThat(quote.availableKeys()).isEqualTo(5_000);
            assertThat(quote.maxApplicableKeys()).isEqualTo(5_000);
            assertThat(quote.usableKeys()).isEqualTo(5_000);
            assertThat(quote.backingSufficient()).isTrue();
        }

        @Test
        @DisplayName("saldo menor al tope: el tope sigue en 5.000 pero solo puede usar las 2.000 que tiene")
        void balanceBelowLimit() {
            givenWallet(2_000);

            KeyCoverageQuoteResponseDTO quote = quote(product(1, 50), 1);

            assertThat(quote.availableKeys()).isEqualTo(2_000);
            assertThat(quote.maxApplicableKeys()).isEqualTo(5_000);
            assertThat(quote.usableKeys()).isEqualTo(2_000);
            // Le faltan 3.000 para el tope y no es por respaldo.
            assertThat(quote.backingSufficient()).isTrue();
        }

        @Test
        @DisplayName("saldo superior al tope: no puede usar más que el tope")
        void balanceAboveLimit() {
            givenWallet(9_000);

            KeyCoverageQuoteResponseDTO quote = quote(product(1, 50), 1);

            assertThat(quote.availableKeys()).isEqualTo(9_000);
            assertThat(quote.usableKeys()).isEqualTo(5_000);
        }

        @Test
        @DisplayName("sin llaves, o sin billetera: no puede usar ninguna")
        void noKeys() {
            givenWallet(0);
            assertThat(quote(product(1, 50), 1).usableKeys()).isZero();

            when(keyWalletRepository.findByConsumerId(CONSUMER)).thenReturn(Optional.empty());
            KeyCoverageQuoteResponseDTO withoutWallet = quote(product(1, 50), 1);
            assertThat(withoutWallet.availableKeys()).isZero();
            assertThat(withoutWallet.usableKeys()).isZero();
        }

        @Test
        @DisplayName("respaldo insuficiente: lo utilizable baja a lo que KEYS_RESERVE puede pagar, en llaves enteras")
        void insufficientBacking() {
            givenWallet(9_000);
            when(treasuryService.keysReserveHeadroomCents()).thenReturn(1_500_500L); // $15.005

            KeyCoverageQuoteResponseDTO quote = quote(product(1, 50), 1);

            assertThat(quote.maxApplicableKeys()).isEqualTo(5_000);
            assertThat(quote.usableKeys()).isEqualTo(1_500);
            assertThat(quote.backingSufficient()).isFalse();
        }

        @Test
        @DisplayName("varias unidades: el total y el tope suman unidad por unidad, y hay una sola línea por producto")
        void severalUnitsOfTheSameProduct() {
            givenWallet(20_000);

            KeyCoverageQuoteResponseDTO quote = quote(product(1, 50), 3);

            assertThat(quote.totalCents()).isEqualTo(3 * PRICE);
            assertThat(quote.maxApplicableKeys()).isEqualTo(15_000);
            assertThat(quote.items()).singleElement()
                    .satisfies(item -> {
                        assertThat(item.productId()).isEqualTo(1L);
                        assertThat(item.maxKeysPct()).isEqualTo(50);
                        assertThat(item.promotionEndsAt()).isNull();
                    });
        }

        @Test
        @DisplayName("promoción al 70 %: sube el tope e informa hasta cuándo rige")
        void specialPromotionIsReported() {
            Product product = product(1, 50);
            givenLivePromotion(product, promotion(7, 70, 100_000_000L, null));
            givenWallet(9_000);

            KeyCoverageQuoteResponseDTO quote = quote(product, 1);

            assertThat(quote.maxApplicableKeys()).isEqualTo(7_000);
            assertThat(quote.usableKeys()).isEqualTo(7_000);
            KeyCoverageQuoteResponseDTO.Item item = quote.items().get(0);
            assertThat(item.maxKeysPct()).isEqualTo(70);
            assertThat(item.promotionEndsAt()).isEqualTo(NOW.plusDays(5));
        }

        @Test
        @DisplayName("promoción que alcanza para una unidad de dos: la línea anuncia el tope ordinario, el total sí cuenta el margen")
        void promotionCoveringOnlySomeUnitsIsNotAdvertisedForAll() {
            Product product = product(1, 50);
            // Presupuesto para el margen de una sola unidad ($20.000 de $30.000).
            givenLivePromotion(product, promotion(7, 70, 3_000_000L, null));
            givenWallet(20_000);

            KeyCoverageQuoteResponseDTO quote = quote(product, 2);

            assertThat(quote.maxApplicableKeys()).isEqualTo(12_000); // 7.000 + 5.000
            KeyCoverageQuoteResponseDTO.Item item = quote.items().get(0);
            assertThat(item.maxKeysPct()).isEqualTo(50);
            assertThat(item.promotionEndsAt()).isNull();
        }
    }
}
