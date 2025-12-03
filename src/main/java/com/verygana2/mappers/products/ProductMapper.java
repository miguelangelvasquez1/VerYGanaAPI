package com.verygana2.mappers.products;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.verygana2.dtos.product.requests.CreateOrEditProductRequest;
import com.verygana2.dtos.product.requests.ProductStockRequest;
import com.verygana2.dtos.product.responses.ProductResponseDTO;
import com.verygana2.dtos.product.responses.ProductSummaryResponseDTO;
import com.verygana2.models.products.Product;
import com.verygana2.models.products.ProductStock;

import java.math.BigDecimal;
import java.text.DecimalFormat;
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
    Product toProduct(CreateOrEditProductRequest request);

    List<ProductStock> toProductStockList (List<ProductStockRequest> stockRequests);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "status", constant = "AVAILABLE")
    @Mapping(target = "purchaseItem", ignore = true)
    @Mapping(target = "soldAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProductStock toProductStock (ProductStockRequest request);

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
    void updateProductFromRequest(CreateOrEditProductRequest request, @MappingTarget Product product);

    // ===== MAPPING to ProductResponse (completed) =====
    @Mapping(target = "categoryName", source = "productCategory.name")
    @Mapping(target = "shopName", source = "seller.shopName")
    @Mapping(target = "priceFormatted", expression = "java(formatPrice(product.getPrice()))")
    ProductResponseDTO toProductResponseDTO(Product product);

    
    ProductSummaryResponseDTO toProductSummaryResponseDTO(Product product);

    default String formatPrice(BigDecimal price) {
        if (price == null) {
            return "$0";
        }
        DecimalFormat formatter = new DecimalFormat("#,###");
        return "$" + formatter.format(price);
    }

}
