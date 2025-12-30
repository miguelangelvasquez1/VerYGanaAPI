package com.verygana2.services;

import java.time.Instant;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.dtos.product.requests.CreateProductReviewRequestDTO;
import com.verygana2.dtos.product.responses.ProductReviewResponseDTO;
import com.verygana2.dtos.productReviews.ReviewableProductResponseDTO;
import com.verygana2.exceptions.UnauthorizedActionException;
import com.verygana2.mappers.products.ProductReviewMapper;
import com.verygana2.models.products.Product;
import com.verygana2.models.products.ProductReview;
import com.verygana2.models.products.Purchase;
import com.verygana2.models.products.PurchaseItem;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.ProductRepository;
import com.verygana2.repositories.ProductReviewRepository;
import com.verygana2.services.interfaces.ProductReviewService;
import com.verygana2.services.interfaces.PurchaseItemService;
import com.verygana2.services.interfaces.PurchaseService;
import com.verygana2.services.interfaces.details.ConsumerDetailsService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductReviewServiceImpl implements ProductReviewService {

    private final ProductReviewRepository productReviewRepository;
    private final ProductReviewMapper productReviewMapper;
    private final ConsumerDetailsService consumerDetailsService;
    private final ProductRepository productRepository;
    private final PurchaseItemService purchaseItemService;
    private final PurchaseService purchaseService;

    @Transactional(readOnly = true)
    @Override
    public Double getProductAvgRating(Long productId) {
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("Product id must be positive");
        }

        Double avg = productReviewRepository.productAvgRating(productId);

        return (avg != null) ? avg : 0.0;
    }

    @Transactional(readOnly = true)
    @Override
    public Double getSellerAvgRating(Long sellerId) {
        if (sellerId == null || sellerId <= 0) {
            throw new IllegalArgumentException("Seller id must be positive");
        }

        Double avg = productReviewRepository.sellerAvgRating(sellerId);

        return (avg != null) ? avg : 0.0;
    }

    @Override
    public EntityCreatedResponseDTO createProductReview(Long consumerId, CreateProductReviewRequestDTO request) {

        PurchaseItem purchaseItem = purchaseItemService.getByIdAndConsumerId(request.getPurchaseItemId(), consumerId);

        boolean alreadyReviewed = productReviewRepository.existsByConsumerIdAndProductId(consumerId,
                purchaseItem.getProduct().getId());

        if (alreadyReviewed) {
            throw new UnauthorizedActionException("You already reviewed this product");
        }

        ConsumerDetails consumer = consumerDetailsService.getConsumerById(consumerId);
        Product product = purchaseItem.getProduct();

        ProductReview productReview = productReviewMapper.toProductReview(request);
        productReview.setConsumer(consumer);
        productReview.setProduct(product);
        productReview.setPurchaseItem(purchaseItem);

        ProductReview savedReview = productReviewRepository.save(productReview);

        product.updateAverageRating();
        productRepository.save(product);

        return new EntityCreatedResponseDTO(savedReview.getId(), "ProductReview created succesfully", Instant.now());
    }

    @Transactional(readOnly = true)
    @Override
    public PagedResponse<ProductReviewResponseDTO> getProductReviewList(Long productId, Pageable pageable) {
        return PagedResponse.from(productReviewRepository.getProductReviewByProductId(productId, pageable)
                .map(productReviewMapper::toProductReviewResponseDTO));
    }

    @Override
    public List<ReviewableProductResponseDTO> getPurchaseItemsToReview(Long purchaseId, Long consumerId) {
        Purchase purchase = purchaseService.getByIdAndConsumerId(purchaseId, consumerId);
        List<PurchaseItem> deliveredItems = purchase.getItems().stream().filter(PurchaseItem::isDelivered).toList();
        Map<Long, PurchaseItem> uniqueByProduct = new HashMap<>();
        for (PurchaseItem item : deliveredItems) {
            uniqueByProduct.putIfAbsent(item.getProduct().getId(), item);
        }

        List<Long> productIds = uniqueByProduct.keySet().stream().toList();

        Set<Long> reviewedProductIds = productReviewRepository
                .findReviewedProductIdsByConsumer(consumerId, productIds);

        return uniqueByProduct.values().stream().filter(item -> !reviewedProductIds.contains(item.getProduct().getId())).map(item -> new ReviewableProductResponseDTO(
            item.getProduct().getId(),
            item.getId(),
            item.getProduct().getName(),
            item.getProduct().getImageUrl()
        ))
        .toList();

    }

}
