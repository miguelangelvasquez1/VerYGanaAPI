package com.verygana2.services.interfaces;

import java.util.List;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.product.requests.ProductStockRequestDTO;
import com.verygana2.dtos.product.responses.BulkStockResponseDTO;
import com.verygana2.dtos.product.responses.ProductStockResponseDTO;
import com.verygana2.models.enums.StockStatus;

public interface ProductStockService {
    PagedResponse<ProductStockResponseDTO> getProductStock (Long productId, Long commercialId, String search, StockStatus status, Pageable pageable);
    ProductStockResponseDTO addStockItem (Long productId, Long commercialId, ProductStockRequestDTO request);
    ProductStockResponseDTO updateStockItem (Long productId, Long stockId, Long commercialId, ProductStockRequestDTO request);
    void deleteStockItem (Long productId, Long stockId, Long commercialId);
    BulkStockResponseDTO addBulkStockItems (Long productId, Long commercialId, List<ProductStockRequestDTO> requests);
}
