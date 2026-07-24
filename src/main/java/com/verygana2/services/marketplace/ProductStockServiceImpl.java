package com.verygana2.services.marketplace;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.ObjectNotFoundException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.product.requests.ProductStockRequestDTO;
import com.verygana2.dtos.product.responses.BulkStockResponseDTO;
import com.verygana2.dtos.product.responses.ProductStockResponseDTO;
import com.verygana2.mappers.marketplace.ProductStockMapper;
import com.verygana2.models.enums.marketplace.StockStatus;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductStock;
import com.verygana2.repositories.marketplace.ProductRepository;
import com.verygana2.repositories.marketplace.ProductStockRepository;
import com.verygana2.security.ProductCodeEncryptor;
import com.verygana2.services.interfaces.marketplace.ProductStockService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductStockServiceImpl implements ProductStockService {

    private final ProductStockRepository productStockRepository;
    private final ProductRepository productRepository;
    private final ProductStockMapper productStockMapper;
    private final ProductCodeEncryptor productCodeEncryptor;

    @Override
    public PagedResponse<ProductStockResponseDTO> getProductStock(Long productId, Long commercialId,
            StockStatus status, LocalDate soldDate, Pageable pageable) {

        productRepository.findByIdAndCommercialId(productId, commercialId)
                .orElseThrow(() -> new ObjectNotFoundException(
                        "Product with id: " + productId + " and commercialId: " + commercialId + " not found",
                        Product.class));

        return PagedResponse.from(productStockRepository.findByProductIdWithFilters(productId, status, soldDate, pageable).map(productStockMapper::toProductStockResponseDTO));

    }

    @Override
    public void deleteStockItem(Long productId, Long stockId, Long commercialId) {

        ProductStock stock = productStockRepository
            .findByIdAndProductIdAndProductCommercialId(stockId, productId, commercialId)
            .orElseThrow(() -> new ObjectNotFoundException(
                "Stock item with id: " + stockId + " for product: " + productId +
                " and commercial: " + commercialId + " not found",
                ProductStock.class
            ));

        // No permitir eliminar si ya está vendido
        if (stock.getStatus() == StockStatus.SOLD) {
            throw new IllegalStateException("Cannot delete a sold stock item");
        }

        productStockRepository.delete(stock);
    }

    @Override
    public BulkStockResponseDTO addBulkStockItems(Long productId, Long commercialId,
            List<ProductStockRequestDTO> requests) {

        Product product = productRepository.findByIdAndCommercialId(productId, commercialId)
            .orElseThrow(() -> new ObjectNotFoundException(
                "Product with id: " + productId + " and commercialId: " + commercialId + " not found",
                Product.class
            ));

        // Un solo hash por request, calculado una vez y reutilizado tanto para
        // el chequeo de duplicados dentro del lote como contra la BD.
        List<String> hashes = requests.stream()
                .map(r -> productCodeEncryptor.hash(r.getCode()))
                .toList();

        // Una sola query para saber qué hashes ya existen en BD para este producto,
        // en vez de una consulta por cada uno de los códigos entrantes.
        Set<String> existingInDb = new HashSet<>(
                productStockRepository.findExistingCodeHashes(productId, hashes));
        Set<String> seenInBatch = new HashSet<>();

        int successful = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < requests.size(); i++) {
            ProductStockRequestDTO request = requests.get(i);
            String codeHash = hashes.get(i);
            try {
                if (existingInDb.contains(codeHash)) {
                    errors.add("Code '" + request.getCode() + "' already exists");
                    failed++;
                    continue;
                }

                if (!seenInBatch.add(codeHash)) {
                    errors.add("Code '" + request.getCode() + "' is duplicated in this batch");
                    failed++;
                    continue;
                }

                ProductStock stock = productStockMapper.toProductStock(request);
                stock.setProduct(product);
                stock.setStatus(StockStatus.AVAILABLE);
                stock.setCreatedAt(ZonedDateTime.now());
                stock.setCode(productCodeEncryptor.encrypt(request.getCode()));
                stock.setCodeHash(codeHash);
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

    @Override
    public String getStockCode(Long stockId, Long productId, Long commercialId) {
       ProductStock stock = productStockRepository.findByIdAndProductIdAndProductCommercialId(stockId, productId, commercialId).orElseThrow(() -> new EntityNotFoundException("Stock with id: " + stockId + ", productId: " + productId + ", commercialId: " + commercialId + " not found"));
       return productCodeEncryptor.decrypt(stock.getCode());
    }
}
