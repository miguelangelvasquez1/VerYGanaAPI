package com.verygana2.services;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.ObjectNotFoundException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.product.requests.ProductStockRequestDTO;
import com.verygana2.dtos.product.responses.BulkStockResponseDTO;
import com.verygana2.dtos.product.responses.ProductStockResponseDTO;
import com.verygana2.exceptions.ProductStock.DuplicateResourceException;
import com.verygana2.mappers.products.ProductStockMapper;
import com.verygana2.models.enums.StockStatus;
import com.verygana2.models.products.Product;
import com.verygana2.models.products.ProductStock;
import com.verygana2.repositories.ProductRepository;
import com.verygana2.repositories.ProductStockRepository;
import com.verygana2.services.interfaces.ProductStockService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductStockServiceImpl implements ProductStockService {

    private final ProductStockRepository productStockRepository;
    private final ProductRepository productRepository;
    private final ProductStockMapper productStockMapper;

    @Override
    public PagedResponse<ProductStockResponseDTO> getProductStock(Long productId, Long sellerId, String search,
            StockStatus status, Pageable pageable) {

        productRepository.findByIdAndSellerId(productId, sellerId)
                .orElseThrow(() -> new ObjectNotFoundException(
                        "Product with id: " + productId + " and sellerId: " + sellerId + " not found",
                        Product.class));

        PagedResponse<ProductStock> stockPage;

        if (search != null && !search.isEmpty() && status != null) {
            stockPage = PagedResponse.from(productStockRepository.findByProductIdAndCodeContainingIgnoreCaseAndStatus(
                    productId, search, status, pageable));
        } else if (search != null && !search.isEmpty()) {
            stockPage = PagedResponse.from(productStockRepository.findByProductIdAndCodeContainingIgnoreCase(
                    productId, search, pageable));
        } else if (status != null) {
            stockPage = PagedResponse.from(productStockRepository.findByProductIdAndStatus(
                    productId, status, pageable));
        } else {
            stockPage = PagedResponse.from(productStockRepository.findByProductId(productId, pageable));
        }

        return stockPage.map(productStockMapper::toProductStockResponseDTO);
    }

    @Override
    public ProductStockResponseDTO addStockItem(Long productId, Long sellerId, ProductStockRequestDTO request) {
        Product product = productRepository.findByIdAndSellerId(productId, sellerId)
                .orElseThrow(() -> new ObjectNotFoundException(
                        "Product with id: " + productId + " and sellerId: " + sellerId + " not found",
                        Product.class));

        // Verificar que el código no exista ya
        if (productStockRepository.existsByProductIdAndCode(productId, request.getCode())) {
            throw new DuplicateResourceException("Stock code already exists for this product");
        }

        ProductStock stock = productStockMapper.toProductStock(request);
        stock.setProduct(product);
        stock.setStatus(StockStatus.AVAILABLE);
        stock.setCreatedAt(ZonedDateTime.now());

        ProductStock saved = productStockRepository.save(stock);
        return productStockMapper.toProductStockResponseDTO(saved);
    }

    @Override
    public ProductStockResponseDTO updateStockItem(Long productId, Long stockId, Long sellerId,
            ProductStockRequestDTO request) {

        ProductStock stock = productStockRepository
                .findByIdAndProductIdAndProductSellerId(stockId, productId, sellerId)
                .orElseThrow(() -> new ObjectNotFoundException(
                        "Stock item with id: " + stockId + " for product: " + productId +
                                " and seller: " + sellerId + " not found",
                        ProductStock.class));

        // No permitir editar si ya está vendido
        if (stock.getStatus() == StockStatus.SOLD) {
            throw new IllegalStateException("Cannot edit a sold stock item");
        }

        // Verificar duplicados si se cambia el código
        if (!stock.getCode().equals(request.getCode()) &&
                productStockRepository.existsByProductIdAndCode(productId, request.getCode())) {
            throw new DuplicateResourceException("Stock code already exists for this product");
        }

        stock.setCode(request.getCode());
        ProductStock updated = productStockRepository.save(stock);
        return productStockMapper.toProductStockResponseDTO(updated);
    }

    @Override
    public void deleteStockItem(Long productId, Long stockId, Long sellerId) {

        ProductStock stock = productStockRepository
            .findByIdAndProductIdAndProductSellerId(stockId, productId, sellerId)
            .orElseThrow(() -> new ObjectNotFoundException(
                "Stock item with id: " + stockId + " for product: " + productId + 
                " and seller: " + sellerId + " not found", 
                ProductStock.class
            ));

        // No permitir eliminar si ya está vendido
        if (stock.getStatus() == StockStatus.SOLD) {
            throw new IllegalStateException("Cannot delete a sold stock item");
        }

        productStockRepository.delete(stock);
    }

    @Override
    public BulkStockResponseDTO addBulkStockItems(Long productId, Long sellerId,
            List<ProductStockRequestDTO> requests) {
        
        Product product = productRepository.findByIdAndSellerId(productId, sellerId)
            .orElseThrow(() -> new ObjectNotFoundException(
                "Product with id: " + productId + " and sellerId: " + sellerId + " not found", 
                Product.class
            ));

        int successful = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        for (ProductStockRequestDTO request : requests) {
            try {
                if (productStockRepository.existsByProductIdAndCode(productId, request.getCode())) {
                    errors.add("Code '" + request.getCode() + "' already exists");
                    failed++;
                    continue;
                }

                ProductStock stock = productStockMapper.toProductStock(request);
                stock.setProduct(product);
                stock.setStatus(StockStatus.AVAILABLE);
                stock.setCreatedAt(ZonedDateTime.now());
                productStockRepository.save(stock);
                successful++;

            } catch (Exception e) {
                errors.add("Error adding code '" + request.getCode() + "': " + e.getMessage());
                failed++;
            }
        }

        return new BulkStockResponseDTO(
            requests.size(),
            successful,
            failed,
            errors,
            ZonedDateTime.now()
        );
    }
}


