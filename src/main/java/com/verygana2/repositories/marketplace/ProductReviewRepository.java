package com.verygana2.repositories.marketplace;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.marketplace.ProductReview;

@Repository
public interface ProductReviewRepository extends JpaRepository<ProductReview, Long>{

    @Query("SELECT AVG(pr.rating) FROM ProductReview pr WHERE pr.product.id = :productId AND pr.visible = true")
    Double productAvgRating (@Param("productId") Long productId);

    @Query("SELECT COUNT(pr) FROM ProductReview pr WHERE pr.product.id = :productId AND pr.visible = true")
    Integer productReviewCount (@Param("productId") Long productId);

    @Query("SELECT AVG(pr.rating) FROM ProductReview pr WHERE pr.product.commercial.id = :commercialId AND pr.visible = true")
    Double commercialAvgRating (@Param("commercialId") Long commercialId);

    @Query("SELECT COUNT(pr) FROM ProductReview pr WHERE pr.product.commercial.id = :commercialId AND pr.visible = true")
    Integer commercialReviewCount (@Param("commercialId") Long commercialId);

    @Query("SELECT pr FROM ProductReview pr WHERE pr.product.id = :productId")
    Page<ProductReview> getProductReviewByProductId (@Param ("productId") Long productId, Pageable pageable);

    boolean existsByConsumerIdAndProductId(Long consumerId, Long productId);
    
}
