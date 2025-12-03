package com.verygana2.mappers.products;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.verygana2.dtos.product.requests.CreateProductReviewRequestDTO;
import com.verygana2.dtos.product.responses.ProductReviewResponseDTO;
import com.verygana2.models.products.ProductReview;

@Mapper(componentModel = "spring")
public interface ProductReviewMapper {
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "consumer", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "purchaseItem", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "visible", ignore = true)
    ProductReview toProductReview (CreateProductReviewRequestDTO request);

    @Mapping(target = "consumerName", source = "consumer.name")
    ProductReviewResponseDTO toProductReviewResponseDTO (ProductReview productReview);
}
