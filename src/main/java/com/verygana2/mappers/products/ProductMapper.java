package com.verygana2.mappers.products;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.verygana2.dtos.product.requests.CreateOrEditProductRequest;
import com.verygana2.dtos.product.responses.ProductResponse;
import com.verygana2.dtos.product.responses.ProductSummaryResponse;
import com.verygana2.models.products.Product;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "category", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "averageRate", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "seller", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Product toProduct(CreateOrEditProductRequest request);

    @Mapping(target = "category", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "averageRate", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "seller", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateProductFromRequest(CreateOrEditProductRequest request, @MappingTarget Product product);

    // ===== MAPPING to ProductResponse (completed) =====
    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "shopName", source = "seller.shopName")
    @Mapping(target = "priceFormatted", expression = "java(formatPrice(product.getPrice()))")
    ProductResponse toProductResponse(Product product);

    @Mapping(target = "imagesUrl", expression = "java(getFirstImageUrl(product.getImagesUrls()))")
    ProductSummaryResponse toProductSummaryResponse(Product product);

    default String getFirstImageUrl(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {

            return "https://placeholder.com/default-product.jpg"; // Default URL
        }
        return imageUrls.get(0);
    }

    default String formatPrice(BigDecimal price) {
        if (price == null) {
            return "$0";
        }
        DecimalFormat formatter = new DecimalFormat("#,###");
        return "$" + formatter.format(price);
    }

}
