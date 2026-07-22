package com.verygana2.services.marketplace;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.dtos.product.requests.ProductStockRequestDTO;
import com.verygana2.dtos.product.responses.BulkStockResponseDTO;
import com.verygana2.mappers.marketplace.ProductStockMapper;
import com.verygana2.models.enums.marketplace.StockStatus;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductStock;
import com.verygana2.repositories.marketplace.ProductRepository;
import com.verygana2.repositories.marketplace.ProductStockRepository;
import com.verygana2.security.ProductCodeEncryptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link ProductStockServiceImpl}: alta/edición/borrado de códigos
 * digitales de un producto, siempre cifrados, y la carga masiva que evita
 * tanto duplicados ya guardados en BD como duplicados dentro del mismo lote.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductStockServiceImpl")
class ProductStockServiceImplTest {

    @Mock private ProductStockRepository productStockRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductStockMapper productStockMapper;
    @Mock private ProductCodeEncryptor productCodeEncryptor;

    private ProductStockServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProductStockServiceImpl(productStockRepository, productRepository, productStockMapper,
                productCodeEncryptor);
    }

    private Product ownedProduct() {
        Product product = new Product();
        product.setId(1L);
        return product;
    }

    @Nested
    @DisplayName("deleteStockItem")
    class DeleteStockItem {

        @Test
        @DisplayName("stock disponible: se elimina")
        void availableStock_deletesIt() {
            ProductStock available = ProductStock.builder().status(StockStatus.AVAILABLE).build();
            when(productStockRepository.findByIdAndProductIdAndProductCommercialId(2L, 1L, 9L))
                    .thenReturn(Optional.of(available));

            service.deleteStockItem(1L, 2L, 9L);

            verify(productStockRepository).delete(available);
        }

        @Test
        @DisplayName("stock vendido: lanza IllegalStateException, no se puede borrar")
        void soldStock_throwsIllegalStateException() {
            ProductStock sold = ProductStock.builder().status(StockStatus.SOLD).build();
            when(productStockRepository.findByIdAndProductIdAndProductCommercialId(2L, 1L, 9L))
                    .thenReturn(Optional.of(sold));

            assertThatThrownBy(() -> service.deleteStockItem(1L, 2L, 9L))
                    .isInstanceOf(IllegalStateException.class);
            verify(productStockRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("addBulkStockItems")
    class AddBulkStockItems {

        @Test
        @DisplayName("detecta duplicados ya existentes en BD y duplicados dentro del mismo lote por separado")
        void detectsDbDuplicatesAndBatchDuplicatesSeparately() {
            ProductStockRequestDTO code1 = new ProductStockRequestDTO(); // ya existe en BD
            code1.setCode("CODE-1");
            ProductStockRequestDTO code2 = new ProductStockRequestDTO(); // nuevo
            code2.setCode("CODE-2");
            ProductStockRequestDTO code3 = new ProductStockRequestDTO(); // repetido dentro del lote (igual a code2)
            code3.setCode("CODE-2");

            when(productRepository.findByIdAndCommercialId(1L, 9L)).thenReturn(Optional.of(ownedProduct()));
            when(productCodeEncryptor.hash("CODE-1")).thenReturn("hash1");
            when(productCodeEncryptor.hash("CODE-2")).thenReturn("hash2");
            when(productStockRepository.findExistingCodeHashes(1L, List.of("hash1", "hash2", "hash2")))
                    .thenReturn(List.of("hash1"));
            when(productCodeEncryptor.encrypt("CODE-2")).thenReturn("cipher2");
            when(productStockMapper.toProductStock(code2)).thenReturn(new ProductStock());

            BulkStockResponseDTO response = service.addBulkStockItems(1L, 9L, List.of(code1, code2, code3));

            assertThat(response.getTotalProcessed()).isEqualTo(3);
            assertThat(response.getSuccessfullyAdded()).isEqualTo(1); // solo CODE-2 (primera aparición)
            assertThat(response.getFailed()).isEqualTo(2); // CODE-1 (ya en BD) + CODE-2 repetido
            verify(productStockRepository, org.mockito.Mockito.times(1)).save(any());
        }
    }

}
