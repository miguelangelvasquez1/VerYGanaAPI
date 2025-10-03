package com.VerYGana.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.VerYGana.models.products.ProductCategory;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long>{
    
}
