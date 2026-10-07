package com.verygana2.services.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.hibernate.ObjectNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.verygana2.dtos.promotions.KeyCoveragePromotionOfferResponseDTO;
import com.verygana2.dtos.promotions.KeyCoveragePromotionRequestDTO;
import com.verygana2.dtos.promotions.KeyCoveragePromotionResponseDTO;
import com.verygana2.exceptions.BusinessException;
import com.verygana2.exceptions.InvalidStatusException;
import com.verygana2.exceptions.TreasuryInsufficientFundsException;
import com.verygana2.models.enums.CommercialActivityType;
import com.verygana2.models.enums.finance.TreasuryAccountCode;
import com.verygana2.models.enums.marketplace.KeyCoveragePromotionOfferStatus;
import com.verygana2.models.enums.marketplace.KeyCoveragePromotionStatus;
import com.verygana2.models.enums.marketplace.ProductStatus;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductCategory;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.marketplace.promotions.KeyCoveragePromotion;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.marketplace.KeyCoveragePromotionOfferRepository;
import com.verygana2.repositories.marketplace.KeyCoveragePromotionRepository;
import com.verygana2.services.interfaces.NotificationService;
import com.verygana2.services.interfaces.finance.TreasuryService;
import com.verygana2.services.interfaces.marketplace.ProductService;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Promoción especial de cobertura con llaves (Contrato B 6.22.8, 7.9.2) contra H2 real:
 * quién puede entrar, que nadie participa sin aceptar, que la autorización aparta la
 * garantía, y el ciclo del presupuesto con una compra. Tesorería va simulada: sus
 * movimientos se prueban en TreasuryServiceImplTest.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:key-promotion-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        // V1__baseline.sql es MySQL puro: el esquema de prueba lo genera Hibernate.
        "spring.flyway.enabled=false",
        "financial.key-value-cents=1000"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ KeyCoveragePromotionServiceImpl.class, CommissionPolicy.class })
@DisplayName("KeyCoveragePromotionService — promoción especial 51–70 % (integración H2)")
class KeyCoveragePromotionServiceIntegrationTest {

    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 10, 7, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final long BUDGET = 5_000_000L; // $50.000 de exceso
    private static final Long ADMIN = 1L;
    private static final AtomicLong SEQ = new AtomicLong(1);

    @Autowired private KeyCoveragePromotionServiceImpl service;
    @Autowired private EntityManager em;
    @Autowired private KeyCoveragePromotionRepository promotionRepository;
    @Autowired private KeyCoveragePromotionOfferRepository offerRepository;

    @MockitoBean private TreasuryService treasuryService;
    @MockitoBean private NotificationService notificationService;
    @MockitoBean private ProductService productService;
    @MockitoBean private Clock clock;

    private final AtomicReference<Instant> now = new AtomicReference<>(NOW.toInstant());

    @BeforeEach
    void stubClock() {
        now.set(NOW.toInstant());
        when(clock.instant()).thenAnswer(invocation -> now.get());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    // ─── Fixtures ─────────────────────────────────────────────────────────────

    private KeyCoveragePromotionRequestDTO request(int pct) {
        return new KeyCoveragePromotionRequestDTO("Semana del " + pct, pct, NOW.minusHours(1), NOW.plusDays(7),
                BUDGET, null, "Aplica solo a las ofertas incorporadas");
    }

    /** Producto de $100.000 de un comercial con el plan dado, visible para productService. */
    private Product product(PlanCode planCode) {
        CommercialDetails commercial = TestEntities.persistCommercial(em, planCode);
        commercial.setCommercialActivityType(CommercialActivityType.PRODUCTS);

        long n = SEQ.getAndIncrement();
        ProductCategory category = new ProductCategory();
        category.setName("categoria-promo-" + n);
        em.persist(category);

        Product product = new Product();
        product.setCommercial(commercial);
        product.setProductCategory(category);
        product.setName("Producto promo " + n);
        product.setPriceCents(10_000_000L);
        product.setMaxKeysPct(50);
        em.persist(product);
        // Product.onCreate deja todo producto nuevo en PENDING: se activa después de persistir.
        product.setStatus(ProductStatus.ACTIVE);
        em.flush();
        when(productService.getById(product.getId())).thenReturn(product);
        return product;
    }

    private Long authorizedPromotionWith(Product product) {
        Long id = service.create(request(70)).id();
        service.addOffer(id, product.getId());
        service.authorize(id, ADMIN);
        return id;
    }

    private Long offerIdOf(Long promotionId) {
        return offerRepository.findByPromotionIdWithProduct(promotionId).get(0).getId();
    }

    private Long acceptedPromotionWith(Product product) {
        Long id = authorizedPromotionWith(product);
        service.acceptOffer(offerIdOf(id), product.getCommercial().getId());
        return id;
    }

    /** Compra en memoria con un ítem que toma {@code excessCents} del margen de la promoción. */
    private Purchase purchaseUsing(Long promotionId, long excessCents) {
        Purchase purchase = Purchase.builder().build();
        purchase.getItems().add(PurchaseItem.builder()
                .keyCoveragePromotionId(promotionId)
                .promotionExcessCents(excessCents)
                .build());
        return purchase;
    }

    private KeyCoveragePromotion reload(Long id) {
        em.flush();
        return promotionRepository.findById(id).orElseThrow();
    }

    // ─── Borrador y ofertas ───────────────────────────────────────────────────

    @Nested
    @DisplayName("borrador y ofertas")
    class Draft {

        @Test
        @DisplayName("solo entran ofertas de Empresas Tipo B (STANDARD)")
        void onlyStandardProductsCanJoin() {
            Long id = service.create(request(70)).id();
            Product basic = product(PlanCode.BASIC);

            assertThatThrownBy(() -> service.addOffer(id, basic.getId())).isInstanceOf(BusinessException.class);

            KeyCoveragePromotionResponseDTO withOffer = service.addOffer(id, product(PlanCode.STANDARD).getId());
            assertThat(withOffer.offers()).singleElement()
                    .extracting(KeyCoveragePromotionOfferResponseDTO::status)
                    .isEqualTo(KeyCoveragePromotionOfferStatus.PENDING_ACCEPTANCE);
        }

        @Test
        @DisplayName("un producto no puede estar en dos promociones abiertas a la vez")
        void productCannotBeInTwoOpenPromotions() {
            Product product = product(PlanCode.STANDARD);
            Long first = service.create(request(70)).id();
            service.addOffer(first, product.getId());
            Long second = service.create(request(60)).id();

            assertThatThrownBy(() -> service.addOffer(second, product.getId())).isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("un borrador no aplica a ninguna compra ni se le muestra al empresario")
        void draftHasNoEffect() {
            Product product = product(PlanCode.STANDARD);
            Long id = service.create(request(70)).id();
            service.addOffer(id, product.getId());

            assertThat(offerRepository.findLiveByProductIds(List.of(product.getId()), NOW)).isEmpty();
            assertThat(service.listOffers(product.getCommercial().getId())).isEmpty();
        }
    }

    // ─── Autorización ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("autorización")
    class Authorize {

        @Test
        @DisplayName("aparta el presupuesto como garantía desde FORTIFICATION y avisa al empresario invitado")
        void reservesGuaranteeAndNotifies() {
            Product product = product(PlanCode.STANDARD);
            Long id = service.create(request(70)).id();
            service.addOffer(id, product.getId());

            KeyCoveragePromotionResponseDTO authorized = service.authorize(id, ADMIN);

            assertThat(authorized.status()).isEqualTo(KeyCoveragePromotionStatus.AUTHORIZED);
            assertThat(authorized.authorizedAt()).isEqualTo(NOW);
            verify(treasuryService).reservePromotionGuarantee(eq(BUDGET), any());
            verify(notificationService).createInternalNotification(
                    eq(product.getCommercial().getId()), any(), any(), any());
        }

        @Test
        @DisplayName("sin ofertas no se autoriza: solo aplica a las expresamente incorporadas")
        void withoutOffers_isRejected() {
            Long id = service.create(request(70)).id();

            assertThatThrownBy(() -> service.authorize(id, ADMIN)).isInstanceOf(BusinessException.class);
            verify(treasuryService, never()).reservePromotionGuarantee(anyLong(), any());
        }

        @Test
        @DisplayName("sin fondos en FORTIFICATION la autorización falla y no se notifica a nadie")
        void withoutFunds_fails() {
            Product product = product(PlanCode.STANDARD);
            Long id = service.create(request(70)).id();
            service.addOffer(id, product.getId());
            doThrow(new TreasuryInsufficientFundsException(TreasuryAccountCode.FORTIFICATION, "sin fondos"))
                    .when(treasuryService).reservePromotionGuarantee(anyLong(), any());

            // La excepción sale del método @Transactional: en producción revierte el cambio de estado.
            assertThatThrownBy(() -> service.authorize(id, ADMIN))
                    .isInstanceOf(TreasuryInsufficientFundsException.class);
            verify(notificationService, never()).createInternalNotification(any(), any(), any(), any());
        }

        @Test
        @DisplayName("una promoción ya autorizada no se vuelve a autorizar ni apartar")
        void cannotAuthorizeTwice() {
            Long id = authorizedPromotionWith(product(PlanCode.STANDARD));

            assertThatThrownBy(() -> service.authorize(id, ADMIN)).isInstanceOf(InvalidStatusException.class);
            verify(treasuryService, times(1)).reservePromotionGuarantee(anyLong(), any());
        }
    }

    // ─── Aceptación del empresario ────────────────────────────────────────────

    @Nested
    @DisplayName("aceptación del empresario")
    class Acceptance {

        @Test
        @DisplayName("autorizada pero sin aceptar: no aplica al producto")
        void notAcceptedIsNotLive() {
            Product product = product(PlanCode.STANDARD);
            authorizedPromotionWith(product);

            assertThat(offerRepository.findLiveByProductIds(List.of(product.getId()), NOW)).isEmpty();
            assertThat(service.listActive()).isEmpty();
        }

        @Test
        @DisplayName("al aceptar queda vigente y se guarda lo que el empresario aceptó")
        void acceptMakesItLiveAndKeepsWhatWasAccepted() {
            Product product = product(PlanCode.STANDARD);
            Long id = authorizedPromotionWith(product);

            KeyCoveragePromotionOfferResponseDTO accepted =
                    service.acceptOffer(offerIdOf(id), product.getCommercial().getId());

            assertThat(accepted.status()).isEqualTo(KeyCoveragePromotionOfferStatus.ACCEPTED);
            assertThat(accepted.priceCents()).isEqualTo(10_000_000L);
            assertThat(accepted.ordinaryKeysPct()).isEqualTo(50);
            assertThat(accepted.promotionKeysPct()).isEqualTo(70);
            assertThat(accepted.maxKeysCoverageCents()).isEqualTo(7_000_000L);
            assertThat(accepted.minUserCashCents()).isEqualTo(3_000_000L);
            // Las llaves no reducen la Venta Computable: la comisión es sobre el precio completo.
            assertThat(accepted.commissionPct()).isEqualTo(10);
            assertThat(accepted.estimatedCommissionCents()).isEqualTo(1_000_000L);
            assertThat(accepted.estimatedNetToCommercialCents()).isEqualTo(9_000_000L);

            assertThat(offerRepository.findLiveByProductIds(List.of(product.getId()), NOW)).hasSize(1);
            assertThat(service.listActive()).singleElement()
                    .satisfies(active -> {
                        assertThat(active.productId()).isEqualTo(product.getId());
                        assertThat(active.keysPct()).isEqualTo(70);
                    });

            // Si el precio cambia después, lo aceptado no cambia.
            product.setPriceCents(20_000_000L);
            assertThat(service.listOffers(product.getCommercial().getId())).singleElement()
                    .extracting(KeyCoveragePromotionOfferResponseDTO::priceCents).isEqualTo(10_000_000L);
        }

        @Test
        @DisplayName("un empresario no puede responder la invitación de otro")
        void cannotAnswerSomeoneElsesOffer() {
            Product product = product(PlanCode.STANDARD);
            Long id = authorizedPromotionWith(product);
            Long stranger = TestEntities.persistCommercial(em, PlanCode.STANDARD).getId();

            assertThatThrownBy(() -> service.acceptOffer(offerIdOf(id), stranger))
                    .isInstanceOf(ObjectNotFoundException.class);
        }

        @Test
        @DisplayName("rechazada o retirada deja de aplicar; el retiro solo rige hacia adelante")
        void rejectedOrWithdrawnIsNotLive() {
            Product rejected = product(PlanCode.STANDARD);
            Long first = authorizedPromotionWith(rejected);
            service.rejectOffer(offerIdOf(first), rejected.getCommercial().getId());

            Product withdrawn = product(PlanCode.STANDARD);
            Long second = acceptedPromotionWith(withdrawn);
            service.withdrawOffer(offerIdOf(second), withdrawn.getCommercial().getId());

            assertThat(offerRepository.findLiveByProductIds(
                    List.of(rejected.getId(), withdrawn.getId()), NOW)).isEmpty();
        }

        @Test
        @DisplayName("expiración: pasada la vigencia no aplica, aunque el job de cierre no haya corrido")
        void pastEndIsNotLive() {
            Product product = product(PlanCode.STANDARD);
            acceptedPromotionWith(product);

            assertThat(offerRepository.findLiveByProductIds(List.of(product.getId()), NOW.plusDays(8))).isEmpty();
        }
    }

    // ─── Presupuesto en una compra ────────────────────────────────────────────

    @Nested
    @DisplayName("presupuesto en una compra")
    class PurchaseCycle {

        @Test
        @DisplayName("pago aprobado: lo reservado pasa a consumido")
        void reserveThenConfirm() {
            Long id = acceptedPromotionWith(product(PlanCode.STANDARD));
            Purchase purchase = purchaseUsing(id, 2_000_000L);

            service.reserveForPurchase(purchase);
            assertThat(reload(id).getReservedCents()).isEqualTo(2_000_000L);

            service.confirmForPurchase(purchase, 7_000_000L);

            KeyCoveragePromotion promotion = reload(id);
            assertThat(promotion.getReservedCents()).isZero();
            assertThat(promotion.getConsumedCents()).isEqualTo(2_000_000L);
            assertThat(promotion.availableBudgetCents()).isEqualTo(BUDGET - 2_000_000L);
            verify(treasuryService).drawPromotionGuarantee(eq(2_000_000L), eq(7_000_000L), any());
        }

        @Test
        @DisplayName("pago fallido: lo reservado vuelve a estar disponible")
        void reserveThenRelease() {
            Long id = acceptedPromotionWith(product(PlanCode.STANDARD));
            Purchase purchase = purchaseUsing(id, 2_000_000L);

            service.reserveForPurchase(purchase);
            service.releaseForPurchase(purchase);

            KeyCoveragePromotion promotion = reload(id);
            assertThat(promotion.getReservedCents()).isZero();
            assertThat(promotion.getConsumedCents()).isZero();
            verify(treasuryService, never()).drawPromotionGuarantee(anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("lo que la garantía repuso a KEYS_RESERVE queda registrado en la promoción")
        void guaranteeDrawnIsRecorded() {
            Long id = acceptedPromotionWith(product(PlanCode.STANDARD));
            Purchase purchase = purchaseUsing(id, 2_000_000L);
            when(treasuryService.drawPromotionGuarantee(anyLong(), anyLong(), any())).thenReturn(1_200_000L);

            service.reserveForPurchase(purchase);
            service.confirmForPurchase(purchase, 7_000_000L);

            assertThat(reload(id).getGuaranteeDrawnCents()).isEqualTo(1_200_000L);
        }

        @Test
        @DisplayName("agotamiento: una compra que pide más que el presupuesto restante se rechaza")
        void exhaustedBudgetRejectsReservation() {
            Long id = acceptedPromotionWith(product(PlanCode.STANDARD));
            service.reserveForPurchase(purchaseUsing(id, BUDGET));

            assertThatThrownBy(() -> service.reserveForPurchase(purchaseUsing(id, 1_000L)))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("promoción pausada entre la cotización y la compra: la reserva se rechaza")
        void pausedPromotionRejectsReservation() {
            Long id = acceptedPromotionWith(product(PlanCode.STANDARD));
            service.pause(id);

            assertThatThrownBy(() -> service.reserveForPurchase(purchaseUsing(id, 1_000_000L)))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("una compra sin promoción no toca ninguna")
        void purchaseWithoutPromotionTouchesNothing() {
            Purchase purchase = Purchase.builder().build();
            purchase.getItems().add(PurchaseItem.builder().build());

            service.reserveForPurchase(purchase);
            service.confirmForPurchase(purchase, 1_000_000L);
            service.releaseForPurchase(purchase);

            verify(treasuryService, never()).drawPromotionGuarantee(anyLong(), anyLong(), any());
        }
    }

    // ─── Cierre ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("cierre y devolución de la garantía")
    class Closure {

        @Test
        @DisplayName("al vencer se cierra y devuelve la garantía no usada, una sola vez")
        void closesAndReturnsGuaranteeOnce() {
            Long id = acceptedPromotionWith(product(PlanCode.STANDARD));
            now.set(NOW.plusDays(8).toInstant());

            assertThat(service.idsNeedingClosure()).containsExactly(id);
            service.close(id);
            service.close(id);

            KeyCoveragePromotion promotion = reload(id);
            assertThat(promotion.getStatus()).isEqualTo(KeyCoveragePromotionStatus.ENDED);
            assertThat(promotion.getGuaranteeReturnedAt()).isNotNull();
            verify(treasuryService, times(1)).returnPromotionGuarantee(eq(BUDGET), any());
            assertThat(service.idsNeedingClosure()).isEmpty();
        }

        @Test
        @DisplayName("devuelve la garantía descontando lo que repuso KEYS_RESERVE")
        void returnsGuaranteeMinusWhatWasDrawn() {
            Long id = acceptedPromotionWith(product(PlanCode.STANDARD));
            Purchase purchase = purchaseUsing(id, 2_000_000L);
            when(treasuryService.drawPromotionGuarantee(anyLong(), anyLong(), any())).thenReturn(500_000L);
            service.reserveForPurchase(purchase);
            service.confirmForPurchase(purchase, 7_000_000L);

            service.end(id);

            verify(treasuryService).returnPromotionGuarantee(eq(BUDGET - 500_000L), any());
        }

        @Test
        @DisplayName("con una compra en curso la garantía espera: se devuelve cuando ese pago se resuelve")
        void guaranteeWaitsForPurchasesInFlight() {
            Long id = acceptedPromotionWith(product(PlanCode.STANDARD));
            Purchase inFlight = purchaseUsing(id, 2_000_000L);
            service.reserveForPurchase(inFlight);

            service.end(id);
            verify(treasuryService, never()).returnPromotionGuarantee(anyLong(), any());
            assertThat(service.idsNeedingClosure()).isEmpty(); // todavía hay reserva

            service.releaseForPurchase(inFlight);
            assertThat(service.idsNeedingClosure()).containsExactly(id);
            service.close(id);

            verify(treasuryService).returnPromotionGuarantee(eq(BUDGET), any());
        }

        @Test
        @DisplayName("un borrador cerrado nunca apartó garantía: no hay nada que devolver")
        void endedDraftReturnsNothing() {
            Long id = service.create(request(70)).id();

            service.end(id);

            verify(treasuryService, never()).returnPromotionGuarantee(anyLong(), any());
        }

        @Test
        @DisplayName("una promoción vigente no se cierra antes de tiempo")
        void runningPromotionIsNotClosedEarly() {
            Long id = acceptedPromotionWith(product(PlanCode.STANDARD));

            service.close(id);

            assertThat(reload(id).getStatus()).isEqualTo(KeyCoveragePromotionStatus.AUTHORIZED);
            verify(treasuryService, never()).returnPromotionGuarantee(anyLong(), any());
        }
    }
}
