package com.verygana2.services.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.verygana2.dtos.product.responses.FeaturedProductResponseDTO;
import com.verygana2.dtos.product.responses.ProductKeyPromotionDTO;
import com.verygana2.dtos.product.responses.ProductResponseDTO;
import com.verygana2.dtos.product.responses.ProductSummaryResponseDTO;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.promotions.KeyCoveragePromotion;
import com.verygana2.models.marketplace.promotions.KeyCoveragePromotionOffer;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.marketplace.KeyCoveragePromotionOfferRepository;

/**
 * Qué productos del catálogo se anuncian con promoción especial de llaves. Que una
 * oferta esté "vigente" (autorizada, aceptada y dentro de fechas) lo decide la consulta
 * del repositorio, cubierta en KeyCoveragePromotionServiceIntegrationTest; aquí se prueba
 * lo que se le entrega al frontend con ese resultado.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductKeyPromotionResolver")
class ProductKeyPromotionResolverTest {

    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 10, 11, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final ZonedDateTime ENDS = NOW.plusDays(5);

    @Mock private KeyCoveragePromotionOfferRepository offerRepository;

    private ProductKeyPromotionResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ProductKeyPromotionResolver(offerRepository, Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));
        ReflectionTestUtils.setField(resolver, "keyValueCents", 1_000L);
    }

    private static ProductSummaryResponseDTO summary(long id, String pricePesos) {
        return ProductSummaryResponseDTO.builder().id(id).price(new BigDecimal(pricePesos)).build();
    }

    /** Oferta vigente de un producto de $100.000 con tope ordinario 50 % y presupuesto de sobra. */
    private KeyCoveragePromotionOffer liveOffer(long productId, int pct) {
        return liveOffer(productId, pct, 10_000_000L, 100_000_000L);
    }

    private KeyCoveragePromotionOffer liveOffer(long productId, int pct, long priceCents, long budgetCents) {
        KeyCoveragePromotion promotion = KeyCoveragePromotion.draft(new KeyCoveragePromotion.Terms(
                "Semana del " + pct, pct, NOW.minusDays(1), ENDS, budgetCents, null, null));
        Product product = new Product();
        product.setId(productId);
        product.setPriceCents(priceCents);
        product.setMaxKeysPct(50);
        product.setCommercial(new CommercialDetails());
        return KeyCoveragePromotionOffer.invite(promotion, product);
    }

    private void decorate(List<ProductSummaryResponseDTO> page) {
        resolver.decorate(page, ProductSummaryResponseDTO::getId, ProductSummaryResponseDTO::getPrice,
                ProductSummaryResponseDTO::setSpecialKeyPromotion);
    }

    @Test
    @DisplayName("producto con promoción vigente: lleva el tope especial, sus llaves máximas, el dinero mínimo y la fecha de fin")
    void productWithLivePromotionIsMarked() {
        ProductSummaryResponseDTO promoted = summary(1, "100000");
        when(offerRepository.findLiveByProductIds(anyCollection(), any())).thenReturn(List.of(liveOffer(1, 70)));

        decorate(List.of(promoted));

        assertThat(promoted.getSpecialKeyPromotion())
                .isEqualTo(new ProductKeyPromotionDTO(70, 7_000L, 3_000_000L, ENDS));
    }

    @Test
    @DisplayName("solo se marca el producto incorporado: los demás de la página quedan sin promoción")
    void onlyTheIncludedProductIsMarked() {
        ProductSummaryResponseDTO promoted = summary(1, "100000");
        ProductSummaryResponseDTO ordinary = summary(2, "100000");
        when(offerRepository.findLiveByProductIds(anyCollection(), any())).thenReturn(List.of(liveOffer(1, 60)));

        decorate(List.of(promoted, ordinary));

        assertThat(promoted.getSpecialKeyPromotion().keysPct()).isEqualTo(60);
        assertThat(ordinary.getSpecialKeyPromotion()).isNull();
    }

    @Test
    @DisplayName("presupuesto agotado: la promoción sigue vigente pero ya no se anuncia, el producto vuelve a su tope ordinario")
    void exhaustedBudgetIsNotAdvertised() {
        ProductSummaryResponseDTO product = summary(1, "100000");
        // El margen de una unidad son $20.000 (del 50 % al 70 %) y quedan $19.990.
        KeyCoveragePromotionOffer almostExhausted = liveOffer(1, 70, 10_000_000L, 1_999_000L);
        when(offerRepository.findLiveByProductIds(anyCollection(), any())).thenReturn(List.of(almostExhausted));

        decorate(List.of(product));

        assertThat(product.getSpecialKeyPromotion()).isNull();
    }

    @Test
    @DisplayName("presupuesto justo para una unidad: todavía se anuncia")
    void budgetForExactlyOneUnitIsStillAdvertised() {
        ProductSummaryResponseDTO product = summary(1, "100000");
        when(offerRepository.findLiveByProductIds(anyCollection(), any()))
                .thenReturn(List.of(liveOffer(1, 70, 10_000_000L, 2_000_000L)));

        decorate(List.of(product));

        assertThat(product.getSpecialKeyPromotion()).isNotNull();
    }

    @Test
    @DisplayName("una sola consulta para toda la página, sin importar cuántos productos tenga")
    void oneQueryPerPage() {
        List<ProductSummaryResponseDTO> page = List.of(summary(1, "100000"), summary(2, "50000"), summary(3, "89900"));
        when(offerRepository.findLiveByProductIds(anyCollection(), any())).thenReturn(List.of());

        decorate(page);

        verify(offerRepository, times(1)).findLiveByProductIds(List.of(1L, 2L, 3L), NOW);
        assertThat(page).allSatisfy(dto -> assertThat(dto.getSpecialKeyPromotion()).isNull());
    }

    @Test
    @DisplayName("página vacía: no consulta nada")
    void emptyPageDoesNotQuery() {
        decorate(List.of());

        verify(offerRepository, never()).findLiveByProductIds(anyCollection(), any());
    }

    @Test
    @DisplayName("las llaves máximas son enteras: $89.900 al 70 % son 6.293 llaves y $26.970 en dinero")
    void maxKeysAreWholeKeys() {
        ProductSummaryResponseDTO promoted = summary(1, "89900");
        when(offerRepository.findLiveByProductIds(anyCollection(), any()))
                .thenReturn(List.of(liveOffer(1, 70, 8_990_000L, 100_000_000L)));

        decorate(List.of(promoted));

        assertThat(promoted.getSpecialKeyPromotion().maxKeysAllowed()).isEqualTo(6_293L);
        assertThat(promoted.getSpecialKeyPromotion().minCashCents()).isEqualTo(2_697_000L);
    }

    @Test
    @DisplayName("funciona igual con el detalle del producto y con los más vendidos del comercial")
    void worksForDetailAndFeaturedProducts() {
        ProductResponseDTO detail = new ProductResponseDTO();
        detail.setId(1L);
        detail.setPrice(new BigDecimal("100000"));
        // El constructor que usa la consulta de más vendidos recibe el precio en centavos.
        FeaturedProductResponseDTO featured = new FeaturedProductResponseDTO(1L, "Combo", "img.png", 10_000_000L, 4.5, 12L);
        when(offerRepository.findLiveByProductIds(anyCollection(), any())).thenReturn(List.of(liveOffer(1, 70)));

        resolver.decorate(List.of(detail), ProductResponseDTO::getId, ProductResponseDTO::getPrice,
                ProductResponseDTO::setSpecialKeyPromotion);
        resolver.decorate(List.of(featured), FeaturedProductResponseDTO::getId, FeaturedProductResponseDTO::getPrice,
                FeaturedProductResponseDTO::setSpecialKeyPromotion);

        assertThat(detail.getSpecialKeyPromotion().maxKeysAllowed()).isEqualTo(7_000L);
        assertThat(featured.getSpecialKeyPromotion().maxKeysAllowed()).isEqualTo(7_000L);
    }
}
