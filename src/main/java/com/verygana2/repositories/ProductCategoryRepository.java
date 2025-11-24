package com.verygana2.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.verygana2.models.products.ProductCategory;

@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long>{

    @Query("SELECT p FROM ProductCategory p WHERE p.isActive = true")
    List<ProductCategory> findAvailableProductCategories();
}
