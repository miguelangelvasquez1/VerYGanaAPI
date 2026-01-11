package com.verygana2.mappers.products;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.verygana2.dtos.product.requests.ProductStockRequestDTO;
import com.verygana2.dtos.product.responses.ProductStockResponseDTO;
import com.verygana2.models.products.ProductStock;

@Mapper(componentModel = "spring")
public interface ProductStockMapper {
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "purchaseItem", ignore = true)
    @Mapping(target = "soldAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProductStock toProductStock (ProductStockRequestDTO request);

    
    ProductStockResponseDTO toProductStockResponseDTO (ProductStock product);
}
