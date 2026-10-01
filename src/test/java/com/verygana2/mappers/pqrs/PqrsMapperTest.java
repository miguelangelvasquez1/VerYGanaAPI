package com.verygana2.mappers.pqrs;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.dtos.pqrs.responses.PqrsAdminDetailDTO;
import com.verygana2.dtos.pqrs.responses.PqrsResponseDTO;
import com.verygana2.models.User;
import com.verygana2.models.commercial.CommercialOnboarding;
import com.verygana2.models.enums.commercial.OnboardingStep;
import com.verygana2.models.enums.pqrs.PqrsStatus;
import com.verygana2.models.enums.pqrs.PqrsType;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductCategory;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.pqrs.Pqrs;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.utils.pqrs.RequesterNameResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests del mapper MapStruct de PQRS ({@link PqrsMapperImpl}, generado en
 * target/generated-sources a partir de {@link PqrsMapper}). Se instancia la
 * clase generada directamente (sin contexto de Spring) para verificar que
 * el mapeo automático de campos y el post-procesamiento manual
 * ({@code @AfterMapping} que resuelve el nombre del solicitante) son correctos.
 * El test vive en el mismo paquete para poder inyectar el mock en el campo
 * {@code protected} heredado sin necesidad de un setter.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PqrsMapper")
class PqrsMapperTest {

    @Mock private RequesterNameResolver requesterNameResolver;

    private PqrsMapperImpl mapper;

    @BeforeEach
    void setUp() {
        mapper = new PqrsMapperImpl();
        mapper.requesterNameResolver = requesterNameResolver;
    }

    private Pqrs samplePqrs() {
        User requester = new User();
        requester.setId(1L);
        requester.setEmail("consumidor@test.com");
        requester.setPhoneNumber("3001234567");

        return Pqrs.builder()
                .id(10L)
                .type(PqrsType.QUEJA)
                .status(PqrsStatus.RECIBIDA)
                .requester(requester)
                .subject("Producto no llegó")
                .description("El producto nunca llegó a mi dirección")
                .response(null)
                .dueDate(ZonedDateTime.now().plusDays(15))
                .createdAt(ZonedDateTime.now())
                .build();
    }

    @Test
    @DisplayName("toResponseDTO: copia todos los campos planos, incluyendo el radicado derivado (getBased)")
    void toResponseDTO_mapsAllFlatFields() {
        Pqrs pqrs = samplePqrs();

        PqrsResponseDTO dto = mapper.toResponseDTO(pqrs);

        assertThat(dto.getId()).isEqualTo(10L);
        assertThat(dto.getBased()).isEqualTo(pqrs.getBased());
        assertThat(dto.getType()).isEqualTo(PqrsType.QUEJA);
        assertThat(dto.getStatus()).isEqualTo(PqrsStatus.RECIBIDA);
        assertThat(dto.getSubject()).isEqualTo("Producto no llegó");
        assertThat(dto.getDescription()).isEqualTo("El producto nunca llegó a mi dirección");
    }

    @Test
    @DisplayName("toResponseDTO con null: retorna null (comportamiento estándar de MapStruct)")
    void toResponseDTO_nullInput_returnsNull() {
        assertThat(mapper.toResponseDTO(null)).isNull();
    }

    @Test
    @DisplayName("toAdminDetailDTO: además de los campos planos, copia los datos del requester y resuelve su nombre")
    void toAdminDetailDTO_mapsRequesterInfoAndResolvesName() {
        Pqrs pqrs = samplePqrs();
        when(requesterNameResolver.resolve(pqrs.getRequester())).thenReturn("Ana Gómez");

        PqrsAdminDetailDTO dto = mapper.toAdminDetailDTO(pqrs);

        assertThat(dto.getRequesterId()).isEqualTo(1L);
        assertThat(dto.getRequesterEmail()).isEqualTo("consumidor@test.com");
        assertThat(dto.getRequesterPhone()).isEqualTo("3001234567");
        // Este campo no viene de una propiedad plana de Pqrs: lo llena el
        // @AfterMapping invocando a RequesterNameResolver.
        assertThat(dto.getRequesterName()).isEqualTo("Ana Gómez");
    }

    @Test
    @DisplayName("toAdminDetailDTO: PQRS genérico (sin purchaseItem) — product/commercial quedan null, sin NPE")
    void toAdminDetailDTO_withoutPurchaseItem_productAndCommercialAreNull() {
        Pqrs pqrs = samplePqrs();
        when(requesterNameResolver.resolve(pqrs.getRequester())).thenReturn("Ana Gómez");

        PqrsAdminDetailDTO dto = mapper.toAdminDetailDTO(pqrs);

        assertThat(dto.getPurchaseItemId()).isNull();
        assertThat(dto.getProduct()).isNull();
        assertThat(dto.getCommercial()).isNull();
    }

    @Test
    @DisplayName("toAdminDetailDTO: PQRS de marketplace — mapea el contexto completo de producto y comercial")
    void toAdminDetailDTO_withPurchaseItem_mapsProductAndCommercialContext() {
        User commercialUser = new User();
        commercialUser.setId(50L);
        commercialUser.setEmail("tienda@test.com");
        commercialUser.setPhoneNumber("3009876543");

        CommercialOnboarding onboarding = new CommercialOnboarding();
        onboarding.setCurrentStep(OnboardingStep.COMPLETED);

        Plan plan = Plan.builder().name("Premium").build();

        CommercialDetails commercial = new CommercialDetails();
        commercial.setUser(commercialUser);
        commercial.setCompanyName("Tienda XYZ");
        commercial.setNit("900123456");
        commercial.setMunicipalityName("Medellín");
        commercial.setDepartmentName("Antioquia");
        commercial.setOnboarding(onboarding);
        commercial.setCurrentPlan(plan);

        ProductCategory category = new ProductCategory();
        category.setName("Tecnología");

        Product product = new Product();
        product.setId(7L);
        product.setName("Audífonos");
        product.setDescription("Audífonos inalámbricos");
        product.setProductCategory(category);
        product.setPriceCents(50000L);
        product.setCommercial(commercial);

        Purchase purchase = Purchase.builder().id(200L).build();
        PurchaseItem item = new PurchaseItem();
        item.setId(15L);
        item.setPurchase(purchase);
        item.setProduct(product);

        Pqrs pqrs = samplePqrs();
        pqrs.setPurchaseItem(item);
        when(requesterNameResolver.resolve(pqrs.getRequester())).thenReturn("Ana Gómez");

        PqrsAdminDetailDTO dto = mapper.toAdminDetailDTO(pqrs);

        assertThat(dto.getPurchaseItemId()).isEqualTo(15L);
        assertThat(dto.getProduct()).isNotNull();
        assertThat(dto.getProduct().getId()).isEqualTo(7L);
        assertThat(dto.getProduct().getName()).isEqualTo("Audífonos");
        assertThat(dto.getProduct().getDescription()).isEqualTo("Audífonos inalámbricos");
        assertThat(dto.getProduct().getCategoryName()).isEqualTo("Tecnología");
        assertThat(dto.getProduct().getPriceCents()).isEqualTo(50000L);

        assertThat(dto.getCommercial()).isNotNull();
        assertThat(dto.getCommercial().getCommercialUserId()).isEqualTo(50L);
        assertThat(dto.getCommercial().getCompanyName()).isEqualTo("Tienda XYZ");
        assertThat(dto.getCommercial().getNit()).isEqualTo("900123456");
        assertThat(dto.getCommercial().getContactEmail()).isEqualTo("tienda@test.com");
        assertThat(dto.getCommercial().getContactPhone()).isEqualTo("3009876543");
        assertThat(dto.getCommercial().getCurrentPlanName()).isEqualTo("Premium");
    }
}
