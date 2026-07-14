package com.verygana2.controllers.marketplace;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.verygana2.dtos.product.responses.ProductCategoryResponseDTO;
import com.verygana2.services.interfaces.marketplace.ProductCategoryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Test de {@link ProductCategoryController}: único endpoint público, lista
 * las categorías activas para poblar filtros del marketplace.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductCategoryController")
class ProductCategoryControllerTest {

    @Mock private ProductCategoryService productCategoryService;

    private ProductCategoryController controller;

    @BeforeEach
    void setUp() {
        controller = new ProductCategoryController(productCategoryService);
    }

    @Test
    @DisplayName("getActiveProductCategories: delega en el service y responde 200 con la lista")
    void getActiveProductCategories_returns200WithList() {
        List<ProductCategoryResponseDTO> expected = List.of(new ProductCategoryResponseDTO());
        when(productCategoryService.getActiveProductCategories()).thenReturn(expected);

        ResponseEntity<List<ProductCategoryResponseDTO>> response = controller.getActiveProductCategories();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
    }
}
