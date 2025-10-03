package com.VerYGana.services;

import java.util.Locale.Category;

import org.hibernate.ObjectNotFoundException;
import org.springframework.stereotype.Service;

import com.VerYGana.dtos.products.requests.CreateProductCategoryRequest;
import com.VerYGana.mappers.products.ProductCategoryMapper;
import com.VerYGana.models.products.ProductCategory;
import com.VerYGana.repositories.ProductCategoryRepository;
import com.VerYGana.services.interfaces.ProductCategoryService;

@Service
public class ProductCategoryServiceImpl implements ProductCategoryService{

    private final ProductCategoryRepository productCategoryRepository;

    private final ProductCategoryMapper productCategoryMapper;

    public ProductCategoryServiceImpl(ProductCategoryRepository productCategoryRepository,
            ProductCategoryMapper productCategoryMapper) {
        this.productCategoryRepository = productCategoryRepository;
        this.productCategoryMapper = productCategoryMapper;
    }

    @Override
    public void create(CreateProductCategoryRequest request) {
        ProductCategory productCategory = productCategoryMapper.toProductCategory(request);
        productCategoryRepository.save(productCategory);
    }

    @Override
    public void delete(Long categoryId) {
        if (!productCategoryRepository.existsById(categoryId)) {
            throw new ObjectNotFoundException("category not found with id: " + categoryId, Category.class);
        }

        productCategoryRepository.deleteById(categoryId);
    }

    @Override
    public ProductCategory getById(Long categoryId) {
        if (categoryId == null) {
            throw new IllegalArgumentException("The categoryId cannot be null");
        }
        if (categoryId <= 0) {
            throw new IllegalArgumentException("The categoryId must be positive");
        }
        
        ProductCategory productCategory = productCategoryRepository.findById(categoryId).orElseThrow(() -> new ObjectNotFoundException("ProductCategory not found for categoryId: " + categoryId, ProductCategory.class));
        
        return productCategory;
    }
    
}
