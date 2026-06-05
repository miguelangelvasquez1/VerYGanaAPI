package com.verygana2.services.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.hibernate.ObjectNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.product.requests.ProductStockRequestDTO;
import com.verygana2.dtos.product.responses.BulkStockResponseDTO;
import com.verygana2.dtos.product.responses.ProductStockResponseDTO;
import com.verygana2.exceptions.ProductStock.DuplicateResourceException;
import com.verygana2.mappers.marketplace.ProductStockMapper;
import com.verygana2.models.enums.marketplace.StockStatus;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductStock;
import com.verygana2.repositories.marketplace.ProductRepository;
import com.verygana2.repositories.marketplace.ProductStockRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductStockServiceImpl")
class ProductStockServiceImplTest {

    @Mock ProductStockRepository productStockRepository;
    @Mock ProductRepository productRepository;
    @Mock ProductStockMapper productStockMapper;

    @InjectMocks ProductStockServiceImpl service;

    private Product product(Long id, Long commercialId) {
        Product p = new Product();
        p.setId(id);
        return p;
    }

    private ProductStock stock(Long id, StockStatus status, String code) {
        return ProductStock.builder()
                .id(id)
                .status(status)
                .code(code)
                .build();
    }

    private ProductStockRequestDTO request(String code) {
        ProductStockRequestDTO dto = new ProductStockRequestDTO();
        dto.setCode(code);
        return dto;
    }

    // ─── getProductStock ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("getProductStock")
    class GetProductStock {

        @Test
        @DisplayName("throws ObjectNotFoundException when product not found for commercial")
        void throwsWhenProductNotFound() {
            when(productRepository.findByIdAndCommercialId(1L, 99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getProductStock(1L, 99L, null, null, PageRequest.of(0, 10)))
                    .isInstanceOf(ObjectNotFoundException.class);
        }

        @Test
        @DisplayName("queries all stock when no search or status filter")
        void queriesAllStockWhenNoFilter() {
            Pageable pageable = PageRequest.of(0, 10);
            Product p = product(1L, 1L);
            when(productRepository.findByIdAndCommercialId(1L, 1L)).thenReturn(Optional.of(p));
            when(productStockRepository.findByProductId(1L, pageable)).thenReturn(Page.empty());

            service.getProductStock(1L, 1L, null, null, pageable);

            verify(productStockRepository).findByProductId(1L, pageable);
        }

        @Test
        @DisplayName("queries by status when only status filter is provided")
        void queriesByStatusWhenOnlyStatus() {
            Pageable pageable = PageRequest.of(0, 10);
            Product p = product(1L, 1L);
            when(productRepository.findByIdAndCommercialId(1L, 1L)).thenReturn(Optional.of(p));
            when(productStockRepository.findByProductIdAndStatus(1L, StockStatus.AVAILABLE, pageable))
                    .thenReturn(Page.empty());

            service.getProductStock(1L, 1L, null, StockStatus.AVAILABLE, pageable);

            verify(productStockRepository).findByProductIdAndStatus(1L, StockStatus.AVAILABLE, pageable);
        }

        @Test
        @DisplayName("queries by search when only search filter is provided")
        void queriesBySearchWhenOnlySearch() {
            Pageable pageable = PageRequest.of(0, 10);
            Product p = product(1L, 1L);
            when(productRepository.findByIdAndCommercialId(1L, 1L)).thenReturn(Optional.of(p));
            when(productStockRepository.findByProductIdAndCodeContainingIgnoreCase(1L, "ABC", pageable))
                    .thenReturn(Page.empty());

            service.getProductStock(1L, 1L, "ABC", null, pageable);

            verify(productStockRepository).findByProductIdAndCodeContainingIgnoreCase(1L, "ABC", pageable);
        }

        @Test
        @DisplayName("queries by both search and status when both filters are provided")
        void queriesByBothFilters() {
            Pageable pageable = PageRequest.of(0, 10);
            Product p = product(1L, 1L);
            when(productRepository.findByIdAndCommercialId(1L, 1L)).thenReturn(Optional.of(p));
            when(productStockRepository.findByProductIdAndCodeContainingIgnoreCaseAndStatus(
                    1L, "ABC", StockStatus.AVAILABLE, pageable)).thenReturn(Page.empty());

            service.getProductStock(1L, 1L, "ABC", StockStatus.AVAILABLE, pageable);

            verify(productStockRepository).findByProductIdAndCodeContainingIgnoreCaseAndStatus(
                    1L, "ABC", StockStatus.AVAILABLE, pageable);
        }
    }

    // ─── addStockItem ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("addStockItem")
    class AddStockItem {

        @Test
        @DisplayName("throws ObjectNotFoundException when product not found")
        void throwsWhenProductNotFound() {
            when(productRepository.findByIdAndCommercialId(1L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.addStockItem(1L, 1L, request("CODE-1")))
                    .isInstanceOf(ObjectNotFoundException.class);
        }

        @Test
        @DisplayName("throws DuplicateResourceException when code already exists for product")
        void throwsWhenCodeDuplicate() {
            Product p = product(1L, 1L);
            when(productRepository.findByIdAndCommercialId(1L, 1L)).thenReturn(Optional.of(p));
            when(productStockRepository.existsByProductIdAndCode(1L, "CODE-1")).thenReturn(true);

            assertThatThrownBy(() -> service.addStockItem(1L, 1L, request("CODE-1")))
                    .isInstanceOf(DuplicateResourceException.class);
        }

        @Test
        @DisplayName("saves stock item and returns DTO")
        void savesStockItemAndReturnsDTO() {
            Product p = product(1L, 1L);
            ProductStock mappedStock = stock(null, StockStatus.AVAILABLE, "CODE-1");
            ProductStock savedStock = stock(10L, StockStatus.AVAILABLE, "CODE-1");
            ProductStockResponseDTO dto = new ProductStockResponseDTO();

            when(productRepository.findByIdAndCommercialId(1L, 1L)).thenReturn(Optional.of(p));
            when(productStockRepository.existsByProductIdAndCode(1L, "CODE-1")).thenReturn(false);
            when(productStockMapper.toProductStock(any())).thenReturn(mappedStock);
            when(productStockRepository.save(mappedStock)).thenReturn(savedStock);
            when(productStockMapper.toProductStockResponseDTO(savedStock)).thenReturn(dto);

            ProductStockResponseDTO result = service.addStockItem(1L, 1L, request("CODE-1"));

            assertThat(result).isSameAs(dto);
            assertThat(mappedStock.getStatus()).isEqualTo(StockStatus.AVAILABLE);
            assertThat(mappedStock.getProduct()).isSameAs(p);
        }
    }

    // ─── updateStockItem ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateStockItem")
    class UpdateStockItem {

        @Test
        @DisplayName("throws ObjectNotFoundException when stock not found")
        void throwsWhenStockNotFound() {
            when(productStockRepository.findByIdAndProductIdAndProductCommercialId(5L, 1L, 1L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateStockItem(1L, 5L, 1L, request("NEW")))
                    .isInstanceOf(ObjectNotFoundException.class);
        }

        @Test
        @DisplayName("throws IllegalStateException when stock is SOLD")
        void throwsWhenSold() {
            ProductStock soldStock = stock(5L, StockStatus.SOLD, "CODE-1");
            when(productStockRepository.findByIdAndProductIdAndProductCommercialId(5L, 1L, 1L))
                    .thenReturn(Optional.of(soldStock));

            assertThatThrownBy(() -> service.updateStockItem(1L, 5L, 1L, request("NEW")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("sold");
        }

        @Test
        @DisplayName("throws DuplicateResourceException when new code already exists")
        void throwsWhenNewCodeDuplicate() {
            ProductStock existingStock = stock(5L, StockStatus.AVAILABLE, "OLD-CODE");
            when(productStockRepository.findByIdAndProductIdAndProductCommercialId(5L, 1L, 1L))
                    .thenReturn(Optional.of(existingStock));
            when(productStockRepository.existsByProductIdAndCode(1L, "NEW-CODE")).thenReturn(true);

            assertThatThrownBy(() -> service.updateStockItem(1L, 5L, 1L, request("NEW-CODE")))
                    .isInstanceOf(DuplicateResourceException.class);
        }

        @Test
        @DisplayName("updates code when same code is submitted (no duplicate check needed)")
        void updatesSameCodeWithoutDuplicateCheck() {
            ProductStock existingStock = stock(5L, StockStatus.AVAILABLE, "SAME-CODE");
            ProductStockResponseDTO dto = new ProductStockResponseDTO();

            when(productStockRepository.findByIdAndProductIdAndProductCommercialId(5L, 1L, 1L))
                    .thenReturn(Optional.of(existingStock));
            when(productStockRepository.save(existingStock)).thenReturn(existingStock);
            when(productStockMapper.toProductStockResponseDTO(existingStock)).thenReturn(dto);

            ProductStockResponseDTO result = service.updateStockItem(1L, 5L, 1L, request("SAME-CODE"));

            assertThat(result).isSameAs(dto);
            verify(productStockRepository, never()).existsByProductIdAndCode(any(), any());
        }

        @Test
        @DisplayName("updates successfully with new code when no duplicate")
        void updatesSuccessfullyWithNewCode() {
            ProductStock existingStock = stock(5L, StockStatus.AVAILABLE, "OLD");
            ProductStockResponseDTO dto = new ProductStockResponseDTO();

            when(productStockRepository.findByIdAndProductIdAndProductCommercialId(5L, 1L, 1L))
                    .thenReturn(Optional.of(existingStock));
            when(productStockRepository.existsByProductIdAndCode(1L, "NEW")).thenReturn(false);
            when(productStockRepository.save(existingStock)).thenReturn(existingStock);
            when(productStockMapper.toProductStockResponseDTO(existingStock)).thenReturn(dto);

            service.updateStockItem(1L, 5L, 1L, request("NEW"));

            assertThat(existingStock.getCode()).isEqualTo("NEW");
        }
    }

    // ─── deleteStockItem ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteStockItem")
    class DeleteStockItem {

        @Test
        @DisplayName("throws ObjectNotFoundException when stock not found")
        void throwsWhenStockNotFound() {
            when(productStockRepository.findByIdAndProductIdAndProductCommercialId(5L, 1L, 1L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteStockItem(1L, 5L, 1L))
                    .isInstanceOf(ObjectNotFoundException.class);
        }

        @Test
        @DisplayName("throws IllegalStateException when stock is SOLD")
        void throwsWhenSold() {
            ProductStock soldStock = stock(5L, StockStatus.SOLD, "CODE");
            when(productStockRepository.findByIdAndProductIdAndProductCommercialId(5L, 1L, 1L))
                    .thenReturn(Optional.of(soldStock));

            assertThatThrownBy(() -> service.deleteStockItem(1L, 5L, 1L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("sold");
        }

        @Test
        @DisplayName("deletes stock item when AVAILABLE")
        void deletesWhenAvailable() {
            ProductStock availableStock = stock(5L, StockStatus.AVAILABLE, "CODE");
            when(productStockRepository.findByIdAndProductIdAndProductCommercialId(5L, 1L, 1L))
                    .thenReturn(Optional.of(availableStock));

            service.deleteStockItem(1L, 5L, 1L);

            verify(productStockRepository).delete(availableStock);
        }
    }

    // ─── addBulkStockItems ────────────────────────────────────────────────────

    @Nested
    @DisplayName("addBulkStockItems")
    class AddBulkStockItems {

        @Test
        @DisplayName("throws ObjectNotFoundException when product not found")
        void throwsWhenProductNotFound() {
            when(productRepository.findByIdAndCommercialId(1L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.addBulkStockItems(1L, 1L, List.of(request("X"))))
                    .isInstanceOf(ObjectNotFoundException.class);
        }

        @Test
        @DisplayName("counts successful and failed items correctly")
        void countsSuccessAndFailure() {
            Product p = product(1L, 1L);
            ProductStock mappedStock = stock(null, StockStatus.AVAILABLE, "GOOD");

            when(productRepository.findByIdAndCommercialId(1L, 1L)).thenReturn(Optional.of(p));
            when(productStockRepository.existsByProductIdAndCode(1L, "GOOD")).thenReturn(false);
            when(productStockRepository.existsByProductIdAndCode(1L, "DUP")).thenReturn(true);
            when(productStockMapper.toProductStock(any())).thenReturn(mappedStock);
            when(productStockRepository.save(any())).thenReturn(mappedStock);

            BulkStockResponseDTO result = service.addBulkStockItems(1L, 1L,
                    List.of(request("GOOD"), request("DUP")));

            assertThat(result.getTotalProcessed()).isEqualTo(2);
            assertThat(result.getSuccessfullyAdded()).isEqualTo(1);
            assertThat(result.getFailed()).isEqualTo(1);
            assertThat(result.getErrors()).hasSize(1);
            assertThat(result.getErrors().get(0)).contains("DUP");
        }

        @Test
        @DisplayName("all items succeed when no duplicates")
        void allSucceedWhenNoDuplicates() {
            Product p = product(1L, 1L);
            ProductStock s1 = stock(null, StockStatus.AVAILABLE, "A");
            ProductStock s2 = stock(null, StockStatus.AVAILABLE, "B");

            when(productRepository.findByIdAndCommercialId(1L, 1L)).thenReturn(Optional.of(p));
            when(productStockRepository.existsByProductIdAndCode(any(), any())).thenReturn(false);
            when(productStockMapper.toProductStock(any())).thenReturn(s1).thenReturn(s2);
            when(productStockRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            BulkStockResponseDTO result = service.addBulkStockItems(1L, 1L,
                    List.of(request("A"), request("B")));

            assertThat(result.getSuccessfullyAdded()).isEqualTo(2);
            assertThat(result.getFailed()).isEqualTo(0);
            assertThat(result.getErrors()).isEmpty();
        }
    }
}
