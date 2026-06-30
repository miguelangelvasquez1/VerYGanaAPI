package com.verygana2.services.marketplace;

import java.time.Instant;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.dtos.product.requests.CreateProductReviewRequestDTO;
import com.verygana2.dtos.product.responses.ProductReviewResponseDTO;
import com.verygana2.exceptions.InvalidContentException;
import com.verygana2.exceptions.UnauthorizedActionException;
import com.verygana2.mappers.marketplace.ProductReviewMapper;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductReview;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.marketplace.ProductRepository;
import com.verygana2.repositories.marketplace.ProductReviewRepository;
import com.verygana2.services.interfaces.details.ConsumerDetailsService;
import com.verygana2.services.interfaces.marketplace.ProductReviewService;
import com.verygana2.services.interfaces.marketplace.PurchaseItemService;
import com.verygana2.utils.ProfanityFilterService;

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
    private final ProfanityFilterService profanityFilterService;

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
    public Double getCommercialAvgRating(Long commercialId) {
        if (commercialId == null || commercialId <= 0) {
            throw new IllegalArgumentException("Commercial id must be positive");
        }

        Double avg = productReviewRepository.commercialAvgRating(commercialId);

        return (avg != null) ? avg : 0.0;
    }

    @Transactional(readOnly = true)
    @Override
    public Integer getCommercialReviewCount(Long commercialId) {
        if (commercialId == null || commercialId <= 0) {
            throw new IllegalArgumentException("Commercial id must be positive");
        }

        Integer count = productReviewRepository.commercialReviewCount(commercialId);

        return (count != null) ? count : 0;
    }

    @Override
    public EntityCreatedResponseDTO createProductReview(Long consumerId, CreateProductReviewRequestDTO request) {

        PurchaseItem purchaseItem = purchaseItemService.getByIdAndConsumerId(request.getPurchaseItemId(), consumerId);

        boolean alreadyReviewed = productReviewRepository.existsByConsumerIdAndProductId(consumerId,
                purchaseItem.getProduct().getId());

        if (alreadyReviewed) {
            throw new UnauthorizedActionException("You already reviewed this product");
        }

        if (profanityFilterService.containsProfanity(request.getComment())) {
        throw new InvalidContentException("Review contains not allowed lenguage.");
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

    @Transactional(readOnly = true)
    @Override
    public boolean canBeReviewed (Long productId, Long consumerId){
        
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("Product id must be positive");
        }

        if (consumerId == null || consumerId <= 0) {
            throw new IllegalArgumentException("Consumer id must be positive");
        }

        if (!productReviewRepository.existsByConsumerIdAndProductId(consumerId, productId)) {
            return true;
        }
        return false;
    }

}
