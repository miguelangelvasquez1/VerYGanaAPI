package com.verygana2.controllers.marketplace;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.generic.AssetUploadPermissionDTO;
import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.dtos.generic.EntityUpdatedResponseDTO;
import com.verygana2.dtos.product.requests.ConfirmProductCreationRequestDTO;
import com.verygana2.dtos.product.requests.ProductStockRequestDTO;
import com.verygana2.dtos.product.requests.UpdateProductRequestDTO;
import com.verygana2.dtos.product.responses.ProductResponseDTO;
import com.verygana2.dtos.product.responses.ProductStockResponseDTO;
import com.verygana2.dtos.product.responses.ProductSummaryResponseDTO;
import com.verygana2.models.enums.marketplace.ProductStatus;
import com.verygana2.services.interfaces.marketplace.ProductService;
import com.verygana2.services.interfaces.marketplace.ProductStockService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link ProductController}: cubre los 3 grupos de endpoints
 * (gestión del producto por el comercial, stock digital, y catálogo público
 * de consulta/favoritos). Como es un controller delgado, cada test solo
 * confirma que se extrae el id correcto del JWT/path, se delega en el
 * service adecuado y se traduce al status HTTP esperado — la lógica de
 * negocio real ya está cubierta en {@code ProductServiceImplTest} y
 * {@code ProductStockServiceImplTest}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductController")
class ProductControllerTest {

    @Mock private ProductService productService;
    @Mock private ProductStockService productStockService;

    private ProductController controller;

    @BeforeEach
    void setUp() {
        controller = new ProductController(productService, productStockService);
    }

    private Jwt jwtWithUserId(Long userId) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("userId")).thenReturn(userId);
        return jwt;
    }

    @Nested
    @DisplayName("gestión del producto (comercial)")
    class ProductManagement {

        @Test
        @DisplayName("prepareProductCreation: delega con el commercialId del JWT, responde 200")
        void prepareProductCreation_delegatesAndReturns200() {
            var metadata = new com.verygana2.dtos.FileUploadRequestDTO("img.png", "image/png", 100L, null, null);
            var expected = AssetUploadPermissionDTO.builder().assetId(1L).build();
            when(productService.prepareProductCreation(9L, metadata)).thenReturn(expected);

            var response = controller.prepareProductCreation(jwtWithUserId(9L), metadata);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isSameAs(expected);
        }

        @Test
        @DisplayName("confirmProductCreation: delega con el commercialId del JWT")
        void confirmProductCreation_delegates() {
            var request = ConfirmProductCreationRequestDTO.builder().build();
            var expected = new EntityCreatedResponseDTO(1L, "ok", java.time.Instant.now());
            when(productService.confirmProductCreation(9L, request)).thenReturn(expected);

            var response = controller.confirmProductCreation(jwtWithUserId(9L), request);

            assertThat(response.getBody()).isSameAs(expected);
        }

        @Test
        @DisplayName("deleteProduct: delega con el commercialId del JWT y responde 204")
        void deleteProduct_returns204() {
            ResponseEntity<Void> response = controller.deleteProduct(jwtWithUserId(9L), 1L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            verify(productService).delete(1L, 9L);
        }

        @Test
        @DisplayName("editProduct: delega el request de edición con el commercialId del JWT")
        void editProduct_delegates() {
            UpdateProductRequestDTO request = new UpdateProductRequestDTO();
            var expected = new EntityUpdatedResponseDTO(1L, "ok", java.time.Instant.now());
            when(productService.edit(1L, 9L, request)).thenReturn(expected);

            var response = controller.editProduct(jwtWithUserId(9L), 1L, request);

            assertThat(response.getBody()).isSameAs(expected);
        }

        @Test
        @DisplayName("getProductEditInfo: delega con el productId del path y el commercialId del JWT")
        void getProductEditInfo_delegates() {
            var expected = new com.verygana2.dtos.product.responses.ProductEditInfoResponseDTO();
            when(productService.getProductEditInfo(1L, 9L)).thenReturn(expected);

            var response = controller.getProductEditInfo(1L, jwtWithUserId(9L));

            assertThat(response.getBody()).isSameAs(expected);
        }

        @Test
        @DisplayName("markProductAsReward: delega y responde 204 sin body")
        void markProductAsReward_returns204() {
            ResponseEntity<Void> response = controller.markProductAsReward(jwtWithUserId(9L), 1L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            verify(productService).markProductAsReward(9L, 1L);
        }
    }

    @Nested
    @DisplayName("stock digital")
    class StockManagement {

        @Test
        @DisplayName("addStockItem: delega y responde 201 CREATED")
        void addStockItem_returns201() {
            ProductStockRequestDTO request = new ProductStockRequestDTO();
            ProductStockResponseDTO expected = new ProductStockResponseDTO();
            when(productStockService.addStockItem(1L, 9L, request)).thenReturn(expected);

            var response = controller.addStockItem(1L, request, jwtWithUserId(9L));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isSameAs(expected);
        }

        @Test
        @DisplayName("updateStockItem: delega con productId/stockId/commercialId")
        void updateStockItem_delegates() {
            ProductStockRequestDTO request = new ProductStockRequestDTO();
            ProductStockResponseDTO expected = new ProductStockResponseDTO();
            when(productStockService.updateStockItem(1L, 2L, 9L, request)).thenReturn(expected);

            var response = controller.updateStockItem(1L, 2L, request, jwtWithUserId(9L));

            assertThat(response.getBody()).isSameAs(expected);
        }

        @Test
        @DisplayName("deleteStockItem: delega y responde 204")
        void deleteStockItem_returns204() {
            var response = controller.deleteStockItem(1L, 2L, jwtWithUserId(9L));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            verify(productStockService).deleteStockItem(1L, 2L, 9L);
        }

        @Test
        @DisplayName("addBulkStockItems: delega y responde 201 CREATED")
        void addBulkStockItems_returns201() {
            List<ProductStockRequestDTO> requests = List.of(new ProductStockRequestDTO());
            var expected = new com.verygana2.dtos.product.responses.BulkStockResponseDTO();
            when(productStockService.addBulkStockItems(1L, 9L, requests)).thenReturn(expected);

            var response = controller.addBulkStockItems(1L, requests, jwtWithUserId(9L));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
    }

    @Nested
    @DisplayName("catálogo público")
    class Catalog {

        @Test
        @DisplayName("loadProducts: delega la página solicitada")
        void loadProducts_delegates() {
            var expected = PagedResponse.<ProductSummaryResponseDTO>builder().build();
            when(productService.getAllProducts(0)).thenReturn(expected);

            var response = controller.loadProducts(0);

            assertThat(response.getBody()).isSameAs(expected);
        }

        @Test
        @DisplayName("searchProducts: pasa todos los filtros de búsqueda al service")
        void searchProducts_passesAllFilters() {
            var expected = PagedResponse.<ProductSummaryResponseDTO>builder().build();
            when(productService.filterProducts("netflix", 3L, 4.0, BigDecimal.TEN, 0, "price", "ASC"))
                    .thenReturn(expected);

            var response = controller.searchProducts("netflix", 3L, 4.0, BigDecimal.TEN, 0, "price", "ASC");

            assertThat(response.getBody()).isSameAs(expected);
        }

        @Test
        @DisplayName("getProductDetail: delega en el service con el productId del path")
        void getProductDetail_delegates() {
            ProductResponseDTO expected = new ProductResponseDTO();
            when(productService.detailProduct(1L)).thenReturn(expected);

            assertThat(controller.getProductDetail(1L).getBody()).isSameAs(expected);
        }

        @Test
        @DisplayName("getTotalCommercialProducts: pasa el status recibido como query param")
        void getTotalCommercialProducts_delegates() {
            when(productService.getTotalCommercialProducts(9L, ProductStatus.ACTIVE)).thenReturn(5);

            var response = controller.getTotalCommercialProducts(jwtWithUserId(9L), ProductStatus.ACTIVE);

            assertThat(response.getBody()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("favoritos")
    class Favorites {

        @Test
        @DisplayName("addToFavorites: delega y responde 201 CREATED")
        void addToFavorites_returns201() {
            var response = controller.addToFavorites(jwtWithUserId(9L), 1L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            verify(productService).addFavorite(9L, 1L);
        }

        @Test
        @DisplayName("removeFromFavorites: delega y responde 204")
        void removeFromFavorites_returns204() {
            var response = controller.removeFromFavorites(jwtWithUserId(9L), 1L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            verify(productService).removeFavorite(9L, 1L);
        }

        @Test
        @DisplayName("countFavorites: delega en el service con el consumerId del JWT")
        void countFavorites_delegates() {
            when(productService.countFavoriteProductsByConsumerId(9L)).thenReturn(3L);

            assertThat(controller.countFavorites(jwtWithUserId(9L)).getBody()).isEqualTo(3L);
        }
    }

    @Nested
    @DisplayName("getPrivateProductImage (proxy de imagen privada)")
    class PrivateImageProxy {

        @Test
        @DisplayName("admin: hace streaming directo sin validar dueño del producto")
        void admin_streamsWithoutOwnershipCheck() throws Exception {
            SecurityContext context = mock(SecurityContext.class);
            Authentication authentication = mock(Authentication.class);
            GrantedAuthority adminAuthority = mock(GrantedAuthority.class);
            when(adminAuthority.getAuthority()).thenReturn("ROLE_ADMIN");
            org.mockito.Mockito.doReturn(List.of(adminAuthority)).when(authentication).getAuthorities();
            when(context.getAuthentication()).thenReturn(authentication);
            SecurityContextHolder.setContext(context);

            try {
                var response = mock(jakarta.servlet.http.HttpServletResponse.class);
                // El JWT no se stubea a propósito: si el controller fuera admin y de todas
                // formas leyera el claim, este mock lanzaría null y el test fallaría —
                // así se prueba en los hechos que el admin nunca necesita el userId.
                Jwt jwt = mock(Jwt.class);
                controller.getPrivateProductImage(1L, jwt, response);

                verify(productService, never()).getByIdAndCommercialId(any(Long.class), any(Long.class));
                verify(productService).streamPrivateProductImage(1L, response);
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }
}
