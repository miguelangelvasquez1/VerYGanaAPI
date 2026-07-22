package com.verygana2.services.interfaces.marketplace;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.product.requests.ProductStockRequestDTO;
import com.verygana2.dtos.product.responses.BulkStockResponseDTO;
import com.verygana2.dtos.product.responses.ProductStockResponseDTO;
import com.verygana2.models.enums.marketplace.StockStatus;

public interface ProductStockService {
    PagedResponse<ProductStockResponseDTO> getProductStock (Long productId, Long commercialId, StockStatus status, LocalDate soldDate, Pageable pageable);
    void deleteStockItem (Long productId, Long stockId, Long commercialId);
    BulkStockResponseDTO addBulkStockItems (Long productId, Long commercialId, List<ProductStockRequestDTO> requests);
    String getStockCode (Long stockId, Long productId, Long commercialId);
}
