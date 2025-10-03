package com.verygana2.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.verygana2.models.products.ProductCategory;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long>{
    
}
