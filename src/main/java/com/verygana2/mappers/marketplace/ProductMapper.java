package com.verygana2.mappers.marketplace;

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
import com.verygana2.mappers.finance.MoneyMapper;
import com.verygana2.models.marketplace.FavoriteProduct;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductReview;
import com.verygana2.models.marketplace.ProductStock;

import java.util.List;

@Mapper(componentModel = "spring", uses = {MoneyMapper.class})
public interface ProductMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "productCategory", ignore = true)
    @Mapping(target = "commercial", ignore = true)
    @Mapping(target = "reviews", ignore = true)
    @Mapping(target = "reviewCount", ignore = true)
    @Mapping(target = "averageRate", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "stock", ignore = true)
    @Mapping(target = "stockItems", source = "stockItems")
    @Mapping(target = "favoritedBy", ignore = true)
    @Mapping(target = "imageAsset", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "approvedBy", ignore = true)
    @Mapping(target = "rejectedBy", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "approvedAt", ignore = true)
    @Mapping(target = "rejectedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "rejectedUntil", ignore = true)
    @Mapping(target = "resubmittedAt", ignore = true)
    @Mapping(target = "resubmissionCount", ignore = true)
    @Mapping(target = "rejectionReason", ignore = true)
    @Mapping(target = "deletionReason", ignore = true)
    @Mapping(target = "maxKeysPct", ignore = true)
    @Mapping(target = "priceCents", source = "price")
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
    @Mapping(target = "averageRate", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "commercial", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "reviews", ignore = true)
    @Mapping(target = "reviewCount", ignore = true)
    @Mapping(target = "stock", ignore = true)
    @Mapping(target = "stockItems", ignore = true)
    @Mapping(target = "favoritedBy", ignore = true)
    @Mapping(target = "imageAsset", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "approvedBy", ignore = true)
    @Mapping(target = "rejectedBy", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "approvedAt", ignore = true)
    @Mapping(target = "rejectedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "rejectedUntil", ignore = true)
    @Mapping(target = "resubmittedAt", ignore = true)
    @Mapping(target = "resubmissionCount", ignore = true)
    @Mapping(target = "rejectionReason", ignore = true)
    @Mapping(target = "deletionReason", ignore = true)
    @Mapping(target = "maxKeysPct", ignore = true)
    @Mapping(target = "priceCents", source = "price")
    void updateProductFromRequest(UpdateProductRequestDTO request, @MappingTarget Product product);

    // ===== MAPPING to ProductResponseDTO (completed) =====
    @Mapping(target = "categoryName", source = "productCategory.name")
    @Mapping(target = "companyName", source = "commercial.companyName")
    @Mapping(target = "reviews", source = "reviews")
    @Mapping(target = "imageUrl", expression = "java(product.getImageUrl())")
    @Mapping(target = "price", source = "priceCents")
    ProductResponseDTO toProductResponseDTO(Product product);

    @Mapping(target = "consumerName", source = "consumer.name")
    ProductReviewResponseDTO toProductReviewResponseDTO(ProductReview review);

    @Mapping(target = "categoryName", source = "productCategory.name")
    @Mapping(target = "stock", expression = "java(product.getAvailableStock())")
    @Mapping(target = "imageUrl", expression = "java(product.getImageUrl())")
    @Mapping(target = "companyName", source = "commercial.companyName")
    @Mapping(target = "price", source = "priceCents")
    ProductSummaryResponseDTO toProductSummaryResponseDTO(Product product);

    @Mapping(target = "id", source = "product.id")
    @Mapping(target = "name", source = "product.name")
    @Mapping(target = "imageUrl", expression = "java(favoriteProduct.getProduct().getImageUrl())")
    @Mapping(target = "price", source = "product.priceCents")
    @Mapping(target = "averageRate", source = "product.averageRate")
    @Mapping(target = "categoryName", source = "product.productCategory.name")
    @Mapping(target = "stock", expression = "java(favoriteProduct.getProduct().getAvailableStock())")
    @Mapping(target = "status", source = "product.status")
    @Mapping(target = "companyName", source = "product.commercial.companyName")
    ProductSummaryResponseDTO toProductSummaryResponseDTO(FavoriteProduct favoriteProduct);

    @AfterMapping
    default void calculateStock(@MappingTarget ProductResponseDTO dto, Product product) {
        dto.setStock(product.getAvailableStock());
    }

    @Mapping(target = "productCategoryId", source = "productCategory.id")
    @Mapping(target = "totalStockItems", ignore = true)
    @Mapping(target = "availableStockItems", expression = "java(product.getAvailableStock())")
    @Mapping(target = "imageUrl", expression = "java(product.getImageUrl())")
    @Mapping(target = "price", source = "priceCents")
    ProductEditInfoResponseDTO toProductEditInfoDTO (Product product);
}
