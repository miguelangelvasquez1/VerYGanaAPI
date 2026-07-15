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
import com.verygana2.dtos.purchase.requests.CreatePurchaseRequestDTO;
import com.verygana2.dtos.purchase.responses.ConsumerPurchaseResponseDTO;
import com.verygana2.dtos.purchase.responses.InitiatePurchaseResponseDTO;
import com.verygana2.dtos.purchase.responses.PurchaseResponseDTO;
import com.verygana2.services.interfaces.marketplace.PurchaseService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link PurchaseController}: iniciar una compra y consultar las
 * compras propias del consumidor autenticado.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PurchaseController")
class PurchaseControllerTest {

    @Mock private PurchaseService purchaseService;

    private PurchaseController controller;

    @BeforeEach
    void setUp() {
        controller = new PurchaseController(purchaseService);
    }

    private Jwt jwtWithUserId(Long userId) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("userId")).thenReturn(userId);
        return jwt;
    }

    @Test
    @DisplayName("createPurchase: delega con el consumerId del JWT")
    void createPurchase_delegates() {
        CreatePurchaseRequestDTO request = CreatePurchaseRequestDTO.builder().build();
        InitiatePurchaseResponseDTO expected = new InitiatePurchaseResponseDTO(
                1L, "ref", 1000L, 1000L, 0L, null, "url", java.time.Instant.now());
        when(purchaseService.createPurchase(9L, request)).thenReturn(expected);

        var response = controller.createPurchase(jwtWithUserId(9L), request);

        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("getPurchaseById: delega con el purchaseId del path y el consumerId del JWT")
    void getPurchaseById_delegates() {
        PurchaseResponseDTO expected = new PurchaseResponseDTO();
        when(purchaseService.getPurchaseResponseDTO(5L, 9L)).thenReturn(expected);

        var response = controller.getPurchaseById(jwtWithUserId(9L), 5L);

        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("getPurchases: delega con el consumerId del JWT y el pageable")
    void getPurchases_delegates() {
        var pageable = PageRequest.of(0, 20);
        var expected = PagedResponse.<ConsumerPurchaseResponseDTO>builder().build();
        when(purchaseService.getConsumerPurchases(9L, pageable)).thenReturn(expected);

        var response = controller.getPurchases(jwtWithUserId(9L), pageable);

        assertThat(response.getBody()).isSameAs(expected);
    }
}
