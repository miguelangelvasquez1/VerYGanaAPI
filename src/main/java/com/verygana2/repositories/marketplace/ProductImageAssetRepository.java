package com.verygana2.repositories.marketplace;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.marketplace.ProductImageAsset;

@Repository
public interface ProductImageAssetRepository extends JpaRepository<ProductImageAsset, Long> {
    Optional<ProductImageAsset> findByProductId (Long productId);
}
