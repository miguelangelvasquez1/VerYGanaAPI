package com.verygana2.mappers.products;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.verygana2.dtos.product.requests.CreateOrEditProductRequestDTO;
import com.verygana2.dtos.product.requests.ProductStockRequestDTO;
import com.verygana2.dtos.product.responses.ProductResponseDTO;
import com.verygana2.dtos.product.responses.ProductReviewResponseDTO;
import com.verygana2.dtos.product.responses.ProductSummaryResponseDTO;
import com.verygana2.models.products.Product;
import com.verygana2.models.products.ProductReview;
import com.verygana2.models.products.ProductStock;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "productCategory", ignore = true)
    @Mapping(target = "seller", ignore = true)
    @Mapping(target = "reviews", ignore = true)
    @Mapping(target = "reviewCount", ignore = true)
    @Mapping(target = "averageRate", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "stock", ignore = true)
    @Mapping(target = "imageUrl", ignore = true)
    @Mapping(target = "stockItems", source = "stockItems")
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "instantDelivery", ignore = true)
    Product toProduct(CreateOrEditProductRequestDTO request);

    List<ProductStock> toProductStockList (List<ProductStockRequestDTO> stockRequests);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "status", constant = "AVAILABLE")
    @Mapping(target = "purchaseItem", ignore = true)
    @Mapping(target = "soldAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProductStock toProductStock (ProductStockRequestDTO request);

    @Mapping(target = "productCategory", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "averageRate", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "seller", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "reviews", ignore = true)
    @Mapping(target = "reviewCount", ignore = true)
    @Mapping(target = "stock", ignore = true)
    @Mapping(target = "imageUrl", ignore = true)
    @Mapping(target = "instantDelivery", ignore = true)
    void updateProductFromRequest(CreateOrEditProductRequestDTO request, @MappingTarget Product product);

    // ===== MAPPING to ProductResponseDTO (completed) =====
    @Mapping(target = "categoryName", source = "productCategory.name")
    @Mapping(target = "shopName", source = "seller.shopName")
    @Mapping(target = "reviews", source = "reviews")
    ProductResponseDTO toProductResponseDTO(Product product);

    @Mapping(target = "consumerName", source = "consumer.name")
    ProductReviewResponseDTO toProductReviewResponseDTO(ProductReview review);

    @Mapping(target = "categoryName", source = "productCategory.name")
    ProductSummaryResponseDTO toProductSummaryResponseDTO(Product product);

    @AfterMapping
    default void calculateStock(@MappingTarget ProductSummaryResponseDTO dto, Product product) {
        dto.setStock(product.getAvailableStock());
    }

    @AfterMapping
    default void calculateStock(@MappingTarget ProductResponseDTO dto, Product product) {
        dto.setStock(product.getAvailableStock());
    }

}
