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
import com.verygana2.dtos.pqrs.responses.PqrsResponseDTO;
import com.verygana2.dtos.product.responses.FeaturedProductResponseDTO;
import com.verygana2.dtos.purchase.requests.ReportPurchaseItemRequestDTO;
import com.verygana2.models.enums.pqrs.MarketplaceIssueReason;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.services.interfaces.finance.CashRefundService;
import com.verygana2.services.interfaces.marketplace.PurchaseItemService;
import com.verygana2.services.interfaces.pqrs.PqrsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link PurchaseItemController}: métricas de ventas del comercial
 * autenticado (total, mensual y productos más vendidos), y el reporte de
 * incidencias de un ítem por parte del comprador.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PurchaseItemController")
class PurchaseItemControllerTest {

    @Mock private PurchaseItemService purchaseItemService;
    @Mock private PqrsService pqrsService;
    @Mock private CashRefundService cashRefundService;

    private PurchaseItemController controller;

    @BeforeEach
    void setUp() {
        controller = new PurchaseItemController(purchaseItemService, pqrsService, cashRefundService);
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
    @DisplayName("getTopSellingProductsPage: delega con el commercialId del JWT y el pageable")
    void getTopSellingProductsPage_delegates() {
        var pageable = PageRequest.of(0, 5);
        var expected = PagedResponse.<FeaturedProductResponseDTO>builder().build();
        when(purchaseItemService.getTopSellingProductsPage(9L, pageable)).thenReturn(expected);

        var response = controller.getTopSellingProductsPage(jwtWithUserId(9L), pageable);

        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("reportIssue: valida el ítem con el consumerId del JWT y delega la creación del PQRS")
    void reportIssue_validatesItemAndDelegatesToPqrsService() {
        PurchaseItem item = new PurchaseItem();
        ReportPurchaseItemRequestDTO request = new ReportPurchaseItemRequestDTO();
        request.setReason(MarketplaceIssueReason.NOT_DELIVERED);
        request.setDescription("Nunca llegó el producto");
        PqrsResponseDTO expected = new PqrsResponseDTO();

        when(purchaseItemService.getReportableItem(5L, 9L)).thenReturn(item);
        when(pqrsService.createPqrsForPurchaseItem(item, MarketplaceIssueReason.NOT_DELIVERED,
                "Nunca llegó el producto", 9L)).thenReturn(expected);

        var response = controller.reportIssue(jwtWithUserId(9L), 5L, request);

        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("submitCashRefundBankDetails: delega en CashRefundService con el consumerId del JWT")
    void submitCashRefundBankDetails_delegatesToCashRefundService() {
        var request = new com.verygana2.dtos.finance.requests.SubmitCashRefundBankDetailsRequestDTO();

        var response = controller.submitCashRefundBankDetails(jwtWithUserId(9L), 5L, request);

        org.mockito.Mockito.verify(cashRefundService).submitBankDetails(5L, 9L, request);
        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }
}
