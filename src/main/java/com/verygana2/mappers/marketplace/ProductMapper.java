package com.verygana2.mappers.marketplace;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.verygana2.storage.service.PublicUrlBuilder;
import com.verygana2.dtos.product.requests.CreateProductRequestDTO;
import com.verygana2.dtos.product.requests.ProductStockRequestDTO;
import com.verygana2.dtos.product.requests.UpdateProductRequestDTO;
import com.verygana2.dtos.product.responses.ProductEditInfoResponseDTO;
import com.verygana2.dtos.product.responses.ProductResponseDTO;
import com.verygana2.dtos.product.responses.ProductReviewResponseDTO;
import com.verygana2.dtos.product.responses.ProductSummaryResponseDTO;
import com.verygana2.mappers.TargetAudienceMapper;
import com.verygana2.mappers.finance.MoneyMapper;
import com.verygana2.models.marketplace.FavoriteProduct;
import com.verygana2.models.marketplace.Product;
import com.verygana2.repositories.marketplace.ProductListingFacts;
import com.verygana2.models.marketplace.ProductReview;
import com.verygana2.models.marketplace.ProductStock;

import java.util.List;

@Mapper(componentModel = "spring", uses = {MoneyMapper.class, TargetAudienceMapper.class, PublicUrlBuilder.class})
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
    @Mapping(target = "isGameReward", ignore = true)
    @Mapping(target = "targetAudience", ignore = true)
    @Mapping(target = "gameRewardAutoDisabled", ignore = true)
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
    @Mapping(target = "codeHash", ignore = true)
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
    @Mapping(target = "isGameReward", ignore = true)
    @Mapping(target = "targetAudience", ignore = true)
    @Mapping(target = "gameRewardAutoDisabled", ignore = true)
    @Mapping (target = "productType", ignore = true)
    void updateProductFromRequest(UpdateProductRequestDTO request, @MappingTarget Product product);

    // ===== MAPPING to ProductResponseDTO (completed) =====
    @Mapping(target = "categoryName", source = "productCategory.name")
    @Mapping(target = "companyName", source = "commercial.companyName")
    @Mapping(target = "commercialPublicId", source = "commercial.user.publicId")
    @Mapping(target = "reviews", source = "reviews")
    @Mapping(target = "imageUrl", ignore = true)
    @Mapping(target = "price", source = "priceCents")
    @Mapping(target = "maxKeysAllowed", expression = "java(product.getMaxKeysAllowed())")
    @Mapping(target = "minCashCents", expression = "java(product.getMinCashCents())")
    @Mapping(target = "specialKeyPromotion", ignore = true) // lo asigna ProductKeyPromotionResolver, por página
    ProductResponseDTO toProductResponseDTO(Product product);

    @Mapping(target = "consumerName", source = "consumer.name")
    ProductReviewResponseDTO toProductReviewResponseDTO(ProductReview review);

    @Mapping(target = "categoryName", source = "product.productCategory.name")
    @Mapping(target = "stock", expression = "java(facts.availableStockAsInt())")
    @Mapping(target = "imageUrl", ignore = true)
    @Mapping(target = "companyName", source = "facts.companyName")
    @Mapping(target = "price", source = "product.priceCents")
    @Mapping(target = "maxKeysAllowed", expression = "java(product.getMaxKeysAllowed())")
    @Mapping(target = "minCashCents", expression = "java(product.getMinCashCents())")
    @Mapping(target = "commercialPublicId", source = "facts.commercialPublicId")
    @Mapping(target = "specialKeyPromotion", ignore = true)
    ProductSummaryResponseDTO toProductSummaryResponseDTO(Product product, ProductListingFacts facts);

    @Mapping(target = "id", source = "favoriteProduct.product.id")
    @Mapping(target = "name", source = "favoriteProduct.product.name")
    @Mapping(target = "imageUrl", source = "favoriteProduct.product.imageAsset.objectKey", qualifiedByName = "publicUrl")
    @Mapping(target = "price", source = "favoriteProduct.product.priceCents")
    @Mapping(target = "averageRate", source = "favoriteProduct.product.averageRate")
    @Mapping(target = "categoryName", source = "favoriteProduct.product.productCategory.name")
    @Mapping(target = "stock", expression = "java(facts.availableStockAsInt())")
    @Mapping(target = "status", source = "favoriteProduct.product.status")
    @Mapping(target = "companyName", source = "facts.companyName")
    @Mapping(target = "maxKeysAllowed", expression = "java(favoriteProduct.getProduct().getMaxKeysAllowed())")
    @Mapping(target = "minCashCents", expression = "java(favoriteProduct.getProduct().getMinCashCents())")
    @Mapping(target = "reviewCount", ignore = true)
    @Mapping(target = "maxKeysPct", ignore = true)
    @Mapping(target = "isGameReward", ignore = true)
    @Mapping(target = "commercialPublicId", source = "facts.commercialPublicId")
    @Mapping(target = "specialKeyPromotion", ignore = true)
    ProductSummaryResponseDTO toProductSummaryResponseDTO(FavoriteProduct favoriteProduct, ProductListingFacts facts);

    @AfterMapping
    default void calculateStock(@MappingTarget ProductResponseDTO dto, Product product) {
        dto.setStock(product.getAvailableStock());
    }

    @Mapping(target = "productCategoryId", source = "productCategory.id")
    @Mapping(target = "totalStockItems", ignore = true)
    @Mapping(target = "availableStockItems", expression = "java(product.getAvailableStock())")
    @Mapping(target = "imageUrl", ignore = true)
    @Mapping(target = "price", source = "priceCents")
    @Mapping(target = "targeting", source = "targetAudience")
    ProductEditInfoResponseDTO toProductEditInfoDTO (Product product);
}
