package com.verygana2.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.products.ProductReview;

@Repository
public interface ProductReviewRepository extends JpaRepository<ProductReview, Long>{

    @Query("SELECT avg(pr.rating) FROM ProductReview pr WHERE pr.product.id = :productId")
    Double productAvgRating (@Param("productId") Long productId);

    @Query("SELECT avg(pr.rating) FROM ProductReview pr WHERE pr.product.seller.id = :sellerId")
    Double sellerAvgRating (@Param("sellerId") Long sellerId);

    @Query("SELECT pr FROM ProductReview pr WHERE pr.product.id = :productId")
    Page<ProductReview> getProductReviewByProductId (@Param ("productId") Long productId, Pageable pageable);
    
}
