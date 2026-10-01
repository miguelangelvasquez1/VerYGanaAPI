package com.verygana2.services.marketplace;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.dtos.product.responses.AllyCommercialResponseDTO;
import com.verygana2.dtos.product.responses.AllyPromotionResponseDTO;
import com.verygana2.exceptions.AllyPromotionException;
import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.exceptions.InvalidStatusException;
import com.verygana2.models.enums.marketplace.ProductStatus;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.marketplace.AllyProductPromotion;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductImageAsset;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.marketplace.AllyProductPromotionRepository;
import com.verygana2.repositories.marketplace.ProductRepository;

import jakarta.persistence.EntityNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link AllyPromotionServiceImpl}. NOTA: {@code toggleAllyPromotion} tiene
 * {@code @RequirePlanCapability} (aspecto AOP); en un test unitario Mockito puro ese
 * aspecto nunca se ejecuta (no hay proxy de Spring), así que estos tests asumen que el
 * guard ya pasó y solo cubren la lógica interna del método.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AllyPromotionServiceImpl")
class AllyPromotionServiceImplTest {

    @Mock private AllyProductPromotionRepository allyProductPromotionRepository;
    @Mock private ProductRepository productRepository;
    @Mock private CommercialDetailsRepository commercialDetailsRepository;

    private AllyPromotionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AllyPromotionServiceImpl(allyProductPromotionRepository, productRepository,
                commercialDetailsRepository);
    }

    private CommercialDetails commercial(Long id, String companyName, PlanCode planCode) {
        CommercialDetails commercial = new CommercialDetails();
        commercial.setId(id);
        commercial.setCompanyName(companyName);
        if (planCode != null) {
            Plan plan = Plan.builder().code(planCode).build();
            commercial.setCurrentPlan(plan);
        }
        return commercial;
    }

    private Product product(Long id, CommercialDetails owner, ProductStatus status) {
        Product product = new Product();
        product.setId(id);
        product.setName("Producto aliado");
        product.setCommercial(owner);
        product.setStatus(status);
        product.setPriceCents(100_000L);
        return product;
    }

    @Nested
    @DisplayName("toggleAllyPromotion")
    class ToggleAllyPromotion {

        @Test
        @DisplayName("producto inexistente: lanza EntityNotFoundException")
        void productNotFound_throwsEntityNotFoundException() {
            when(productRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.toggleAllyPromotion(9L, 1L))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("la promoción ya existe: se borra y retorna sin validar elegibilidad/estado/límite (delete-first short-circuit)")
        void existingPromotion_deletesAndShortCircuits() {
            CommercialDetails ally = commercial(1L, "Aliado", null); // plan null: normalmente NO elegible
            Product product = product(1L, ally, ProductStatus.PENDING); // status normalmente inválido
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            AllyProductPromotion existing = AllyProductPromotion.builder().id(100L).build();
            when(allyProductPromotionRepository.findByPremiumCommercial_IdAndProduct_Id(9L, 1L))
                    .thenReturn(Optional.of(existing));

            service.toggleAllyPromotion(9L, 1L);

            verify(allyProductPromotionRepository).delete(existing);
            verify(allyProductPromotionRepository, never()).save(any());
            verify(commercialDetailsRepository, never()).findById(any());
        }

        @Test
        @DisplayName("promoción no existe, aliado no elegible (plan null): lanza InvalidRequestException")
        void notEligibleAlly_nullPlan_throwsInvalidRequestException() {
            CommercialDetails ally = commercial(1L, "Aliado", null);
            Product product = product(1L, ally, ProductStatus.ACTIVE);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(allyProductPromotionRepository.findByPremiumCommercial_IdAndProduct_Id(9L, 1L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.toggleAllyPromotion(9L, 1L))
                    .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        @DisplayName("promoción no existe, aliado no elegible (plan PREMIUM): lanza InvalidRequestException")
        void notEligibleAlly_premiumPlan_throwsInvalidRequestException() {
            CommercialDetails ally = commercial(1L, "Aliado", PlanCode.PREMIUM);
            Product product = product(1L, ally, ProductStatus.ACTIVE);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(allyProductPromotionRepository.findByPremiumCommercial_IdAndProduct_Id(9L, 1L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.toggleAllyPromotion(9L, 1L))
                    .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        @DisplayName("aliado elegible, producto no ACTIVE: lanza InvalidStatusException")
        void eligibleAllyButProductNotActive_throwsInvalidStatusException() {
            CommercialDetails ally = commercial(1L, "Aliado", PlanCode.BASIC);
            Product product = product(1L, ally, ProductStatus.PENDING);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(allyProductPromotionRepository.findByPremiumCommercial_IdAndProduct_Id(9L, 1L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.toggleAllyPromotion(9L, 1L))
                    .isInstanceOf(InvalidStatusException.class);
        }

        @Test
        @DisplayName("aliado elegible, producto ACTIVE, pero ya hay 3 promociones: lanza AllyPromotionException")
        void maxPromotionsReached_throwsAllyPromotionException() {
            CommercialDetails ally = commercial(1L, "Aliado", PlanCode.STANDARD);
            Product product = product(1L, ally, ProductStatus.ACTIVE);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(allyProductPromotionRepository.findByPremiumCommercial_IdAndProduct_Id(9L, 1L))
                    .thenReturn(Optional.empty());
            when(allyProductPromotionRepository.countByPremiumCommercial_Id(9L)).thenReturn(3L);

            assertThatThrownBy(() -> service.toggleAllyPromotion(9L, 1L))
                    .isInstanceOf(AllyPromotionException.class);
        }

        @Test
        @DisplayName("todo válido pero el comercio premium no existe: lanza EntityNotFoundException")
        void premiumCommercialNotFound_throwsEntityNotFoundException() {
            CommercialDetails ally = commercial(1L, "Aliado", PlanCode.STANDARD);
            Product product = product(1L, ally, ProductStatus.ACTIVE);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(allyProductPromotionRepository.findByPremiumCommercial_IdAndProduct_Id(9L, 1L))
                    .thenReturn(Optional.empty());
            when(allyProductPromotionRepository.countByPremiumCommercial_Id(9L)).thenReturn(0L);
            when(commercialDetailsRepository.findById(9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.toggleAllyPromotion(9L, 1L))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("caso feliz: todo válido, guarda una AllyProductPromotion nueva con premiumCommercial/product seteados")
        void happyPath_savesNewPromotion() {
            CommercialDetails ally = commercial(1L, "Aliado", PlanCode.BASIC);
            Product product = product(1L, ally, ProductStatus.ACTIVE);
            CommercialDetails premium = commercial(9L, "Premium", PlanCode.PREMIUM);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(allyProductPromotionRepository.findByPremiumCommercial_IdAndProduct_Id(9L, 1L))
                    .thenReturn(Optional.empty());
            when(allyProductPromotionRepository.countByPremiumCommercial_Id(9L)).thenReturn(2L);
            when(commercialDetailsRepository.findById(9L)).thenReturn(Optional.of(premium));

            service.toggleAllyPromotion(9L, 1L);

            ArgumentCaptor<AllyProductPromotion> captor = ArgumentCaptor.forClass(AllyProductPromotion.class);
            verify(allyProductPromotionRepository).save(captor.capture());
            assertThat(captor.getValue().getPremiumCommercial()).isEqualTo(premium);
            assertThat(captor.getValue().getProduct()).isEqualTo(product);
        }
    }

    @Nested
    @DisplayName("getMyPromotions")
    class GetMyPromotions {

        @Test
        @DisplayName("con promociones: mapea productId/productName/productImageUrl/allyCommercialId/allyCommercialName/priceCents/promotedAt")
        void withPromotions_mapsToResponseDTO() {
            CommercialDetails ally = commercial(1L, "Aliado", PlanCode.BASIC);
            Product product = product(10L, ally, ProductStatus.ACTIVE);
            product.setImageAsset(ProductImageAsset.builder().objectKey("products/10.png").build());
            ZonedDateTime createdAt = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
            AllyProductPromotion promo = AllyProductPromotion.builder()
                    .id(100L)
                    .premiumCommercial(commercial(9L, "Premium", PlanCode.PREMIUM))
                    .product(product)
                    .createdAt(createdAt)
                    .build();
            when(allyProductPromotionRepository.findByPremiumCommercialIdOrderByCreatedAtDesc(9L))
                    .thenReturn(List.of(promo));

            List<AllyPromotionResponseDTO> result = service.getMyPromotions(9L);

            assertThat(result).hasSize(1);
            AllyPromotionResponseDTO dto = result.get(0);
            assertThat(dto.getProductId()).isEqualTo(10L);
            assertThat(dto.getProductName()).isEqualTo("Producto aliado");
            assertThat(dto.getProductImageUrl()).isEqualTo("https://cdn.verygana.com/public/products/10.png");
            assertThat(dto.getAllyCommercialId()).isEqualTo(1L);
            assertThat(dto.getAllyCommercialName()).isEqualTo("Aliado");
            assertThat(dto.getPriceCents()).isEqualTo(100_000L);
            assertThat(dto.getPromotedAt()).isEqualTo(createdAt);
            verify(allyProductPromotionRepository).findByPremiumCommercialIdOrderByCreatedAtDesc(9L);
        }

        @Test
        @DisplayName("sin promociones: retorna lista vacía")
        void noPromotions_returnsEmptyList() {
            when(allyProductPromotionRepository.findByPremiumCommercialIdOrderByCreatedAtDesc(9L))
                    .thenReturn(List.of());

            assertThat(service.getMyPromotions(9L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("getMyAllies / getMyPromoters")
    class GetMyAlliesAndPromoters {

        @Test
        @DisplayName("getMyAllies: mapea commercialId/companyName/planCode desde currentPlan.code")
        void getMyAllies_mapsCommercialResponse() {
            CommercialDetails ally = commercial(1L, "Aliado", PlanCode.STANDARD);
            when(allyProductPromotionRepository.findDistinctAlliesOfPremium(9L)).thenReturn(List.of(ally));

            List<AllyCommercialResponseDTO> result = service.getMyAllies(9L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCommercialId()).isEqualTo(1L);
            assertThat(result.get(0).getCompanyName()).isEqualTo("Aliado");
            assertThat(result.get(0).getPlanCode()).isEqualTo("STANDARD");
        }

        @Test
        @DisplayName("getMyPromoters: mapea commercialId/companyName/planCode desde currentPlan.code")
        void getMyPromoters_mapsCommercialResponse() {
            CommercialDetails premium = commercial(9L, "Premium", PlanCode.PREMIUM);
            when(allyProductPromotionRepository.findDistinctPromotersOfCommercial(1L)).thenReturn(List.of(premium));

            List<AllyCommercialResponseDTO> result = service.getMyPromoters(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCommercialId()).isEqualTo(9L);
            assertThat(result.get(0).getCompanyName()).isEqualTo("Premium");
            assertThat(result.get(0).getPlanCode()).isEqualTo("PREMIUM");
        }

        @Test
        @DisplayName("commercial.currentPlan == null: planCode queda null, sin NPE")
        void nullCurrentPlan_planCodeIsNullWithoutNpe() {
            CommercialDetails allyWithoutPlan = commercial(1L, "Aliado sin plan", null);
            when(allyProductPromotionRepository.findDistinctAlliesOfPremium(9L))
                    .thenReturn(List.of(allyWithoutPlan));

            List<AllyCommercialResponseDTO> result = service.getMyAllies(9L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPlanCode()).isNull();
        }
    }
}
