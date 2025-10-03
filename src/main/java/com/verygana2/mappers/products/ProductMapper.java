package com.verygana2.mappers.products;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.verygana2.dtos.products.requests.CreateOrEditProductRequest;
import com.verygana2.dtos.products.responses.ProductResponse;
import com.verygana2.dtos.products.responses.ProductSummaryResponse;
import com.verygana2.models.products.Product;

import java.math.BigDecimal;
import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "category.id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "averageRate", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "sellerId", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Product toProduct(CreateOrEditProductRequest request);

    // ===== MAPPING to ProductResponse (completed) =====
    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "sellerName", expression = "java(buildSellerName(product.getSellerId()))")
    @Mapping(target = "priceFormatted", expression = "java(formatPrice(product.getPrice()))")
    @Mapping(target = "ratingStars", expression = "java(generateRatingStars(product.getAverageRate()))")
    @Mapping(target = "imagesUrls", ignore = true)
    ProductResponse toProductResponse(Product product);

    @Mapping(target = "imageUrl", expression = "java(getFirstImageUrl(product.getImageUrls()))")
    ProductSummaryResponse toProductSummaryResponse(Product product);

    default String getFirstImageUrl(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
        
            return "https://placeholder.com/default-product.jpg"; // Default URL
        }
        return imageUrls.get(0);
    }

    List<ProductResponse> toProductResponseList(List<Product> products);
    List<ProductSummaryResponse> tProductSummaryResponseList (List<Product> products);


    
    default String buildSellerName(Long sellerId) {
        if (sellerId == null) {
            return "unknown seller";
        }
        // TODO: Integrar con UserService para obtener nombre real del vendedor
        return "seller: " + sellerId;
    }

    default String formatPrice(BigDecimal price) {
        if (price == null) {
            return "$0.00";
        }
        return String.format("$%.2f", price);
    }

    default String generateRatingStars(Double rating) {
        if (rating == null || rating == 0.0) {
            return "☆☆☆☆☆";
        }

        int fullStars = rating.intValue();
        boolean hasHalfStar = (rating - fullStars) >= 0.5;
        StringBuilder stars = new StringBuilder();

        // Agregar estrellas completas
        for (int i = 0; i < fullStars && i < 5; i++) {
            stars.append("★");
        }

        // Agregar media estrella si aplica
        if (hasHalfStar && fullStars < 5) {
            stars.append("⭐");
            fullStars++;
        }

        // Completar con estrellas vacías
        for (int i = fullStars; i < 5; i++) {
            stars.append("☆");
        }

        return stars.toString();
    }
}
