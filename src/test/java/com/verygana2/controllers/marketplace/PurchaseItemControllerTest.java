package com.verygana2.controllers.marketplace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.jwt.Jwt;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.product.responses.FeaturedProductResponseDTO;
import com.verygana2.services.interfaces.marketplace.PurchaseItemService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link PurchaseItemController}: métricas de ventas del comercial
 * autenticado (total, mensual y productos más vendidos).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PurchaseItemController")
class PurchaseItemControllerTest {

    @Mock private PurchaseItemService purchaseItemService;

    private PurchaseItemController controller;

    @BeforeEach
    void setUp() {
        controller = new PurchaseItemController(purchaseItemService);
    }

    private Jwt jwtWithUserId(Long userId) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("userId")).thenReturn(userId);
        return jwt;
    }

    @Test
    @DisplayName("getTotalCommercialSales: delega con el commercialId del JWT")
    void getTotalCommercialSales_delegates() {
        when(purchaseItemService.getTotalSalesbyCommercial(9L)).thenReturn(42L);

        assertThat(controller.getTotalCommercialSales(jwtWithUserId(9L)).getBody()).isEqualTo(42L);
    }

    @Test
    @DisplayName("getTotalCommercialSalesByMonth: pasa year/month recibidos como query params")
    void getTotalCommercialSalesByMonth_delegates() {
        when(purchaseItemService.getTotalCommercialSalesByMonth(9L, 2026, 3)).thenReturn(10);

        var response = controller.getTotalCommercialSalesByMonth(jwtWithUserId(9L), 2026, 3);

        assertThat(response.getBody()).isEqualTo(10);
    }

    @Test
    @DisplayName("getTopSellingProductsPage: delega con el commercialId del JWT y el pageable")
    void getTopSellingProductsPage_delegates() {
        var pageable = PageRequest.of(0, 5);
        var expected = PagedResponse.<FeaturedProductResponseDTO>builder().build();
        when(purchaseItemService.getTopSellingProductsPage(9L, pageable)).thenReturn(expected);

        var response = controller.getTopSellingProductsPage(jwtWithUserId(9L), pageable);

        assertThat(response.getBody()).isSameAs(expected);
    }
}
