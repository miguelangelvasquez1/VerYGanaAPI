package com.verygana2.services;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.generic.EntityCreatedResponse;
import com.verygana2.dtos.product.requests.CreateProductReviewRequestDTO;
import com.verygana2.dtos.product.responses.ProductReviewResponseDTO;
import com.verygana2.dtos.purchase.responses.PurchaseItemToReviewResponseDTO;
import com.verygana2.exceptions.UnauthorizedActionException;
import com.verygana2.mappers.products.ProductReviewMapper;
import com.verygana2.models.products.Product;
import com.verygana2.models.products.ProductReview;
import com.verygana2.models.products.PurchaseItem;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.ProductRepository;
import com.verygana2.repositories.ProductReviewRepository;
import com.verygana2.services.interfaces.ProductReviewService;
import com.verygana2.services.interfaces.PurchaseItemService;
import com.verygana2.services.interfaces.details.ConsumerDetailsService;


import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductReviewServiceImpl implements ProductReviewService{

    private ProductReviewRepository productReviewRepository;
    private ProductReviewMapper productReviewMapper;
    private ConsumerDetailsService consumerDetailsService;
    private ProductRepository productRepository;
    private PurchaseItemService purchaseItemService;

    @Transactional(readOnly = true)
    @Override
    public Double getProductAvgRating(Long productId) {
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("Product id must be positive");
        }

        Double avg = productReviewRepository.productAvgRating(productId);
        
        return (avg != null) ?  avg : 0.0;
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
    public EntityCreatedResponse createProductReview(Long consumerId, CreateProductReviewRequestDTO request) {

        PurchaseItem purchaseItem = purchaseItemService.getByIdAndConsumerId(request.getPurchaseItemId(), consumerId);

        if (!purchaseItem.canBeReviewed()) {
            if (purchaseItem.hasReview()) {
                throw new UnauthorizedActionException("You already have done a review for this product");
            } else {
                throw new UnauthorizedActionException("This product has not been delivered yet");
            }
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

        return new EntityCreatedResponse(savedReview.getId(), "ProductReview created succesfully", Instant.now());
    }

    @Transactional(readOnly = true)
    @Override
    public PagedResponse<ProductReviewResponseDTO> getProductReviewList(Long productId, Integer pageIndex) {
        Pageable pageable = PageRequest.of(pageIndex, 20, Direction.DESC, "createdAt");
        return PagedResponse.from(productReviewRepository.getProductReviewByProductId(productId, pageable).map(productReviewMapper::toProductReviewResponseDTO));
    }  

    @Transactional(readOnly = true)
    public List<PurchaseItemToReviewResponseDTO> getPurchaseItemsToReview(Long consumerId) {
        List<PurchaseItem> items = purchaseItemService.getDeliveredItemsWithoutReview(consumerId);
        
        return items.stream()
            .map(item -> new PurchaseItemToReviewResponseDTO(
                item.getId(),
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getProduct().getImageUrl(),
                item.getDeliveredCode(),
                item.getDeliveredAt()
            ))
            .toList();
    }
}
