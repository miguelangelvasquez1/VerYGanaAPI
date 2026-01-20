package com.verygana2.mappers.products;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.verygana2.dtos.product.requests.CreateProductRequestDTO;
import com.verygana2.dtos.product.requests.ProductStockRequestDTO;
import com.verygana2.dtos.product.requests.UpdateProductRequestDTO;
import com.verygana2.dtos.product.responses.ProductEditInfoResponseDTO;
import com.verygana2.dtos.product.responses.ProductResponseDTO;
import com.verygana2.dtos.product.responses.ProductReviewResponseDTO;
import com.verygana2.dtos.product.responses.ProductSummaryResponseDTO;
import com.verygana2.models.products.FavoriteProduct;
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
    @Mapping(target = "favoritedBy", ignore = true)
    Product toProduct(CreateProductRequestDTO request);

    List<ProductStock> toProductStockList(List<ProductStockRequestDTO> stockRequests);
    List<ProductStockRequestDTO> toProductStockRequestDTOList(List<ProductStock> stock);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "status", constant = "AVAILABLE")
    @Mapping(target = "purchaseItem", ignore = true)
    @Mapping(target = "soldAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProductStock toProductStock(ProductStockRequestDTO request);

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
    @Mapping(target = "stockItems", ignore = true)
    @Mapping(target = "favoritedBy", ignore = true)
    void updateProductFromRequest(UpdateProductRequestDTO request, @MappingTarget Product product);

    // ===== MAPPING to ProductResponseDTO (completed) =====
    @Mapping(target = "categoryName", source = "productCategory.name")
    @Mapping(target = "shopName", source = "seller.shopName")
    @Mapping(target = "reviews", source = "reviews")
    ProductResponseDTO toProductResponseDTO(Product product);

    @Mapping(target = "consumerName", source = "consumer.name")
    ProductReviewResponseDTO toProductReviewResponseDTO(ProductReview review);

    @Mapping(target = "categoryName", source = "productCategory.name")
    ProductSummaryResponseDTO toProductSummaryResponseDTO(Product product);

    @Mapping(target = "id", source = "product.id")
    @Mapping(target = "name", source = "product.name")
    @Mapping(target = "imageUrl", source = "product.imageUrl")
    @Mapping(target = "price", source = "product.price")
    @Mapping(target = "averageRate", source = "product.averageRate")
    @Mapping(target = "categoryName", source = "product.productCategory.name")
    @Mapping(target = "stock", source = "product.stock")
    ProductSummaryResponseDTO toProductSummaryResponseDTO(FavoriteProduct favoriteProduct);

    @Mapping(target = "productCategoryId", source = "productCategory.id")
    @Mapping(target = "stockItems", source = "stockItems")
    CreateProductRequestDTO toCreateOrEditProductRequestDTO(Product product);

    @AfterMapping
    default void calculateStock(@MappingTarget ProductSummaryResponseDTO dto, Product product) {
        dto.setStock(product.getAvailableStock());
    }

    @AfterMapping
    default void calculateStock(@MappingTarget ProductResponseDTO dto, Product product) {
        dto.setStock(product.getAvailableStock());
    }

    @Mapping(target = "productCategoryId", source = "productCategory.id")
    @Mapping(target = "totalStockItems", source = "stock")
    @Mapping(target = "availableStockItems", expression = "java(getAvailableStock(product))")
    ProductEditInfoResponseDTO toProductEditInfoDTO (Product product);

    default Integer getAvailableStock (Product product) {
        return product.getAvailableStock();
    }

}
