package com.verygana2.repositories.marketplace;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.verygana2.models.marketplace.ProductCategoryImageAsset;

public interface ProductCategoryImageAssetRepository extends JpaRepository <ProductCategoryImageAsset, Long>{
    Optional<ProductCategoryImageAsset> findByProductCategoryId (Long productCategoryId);
}
