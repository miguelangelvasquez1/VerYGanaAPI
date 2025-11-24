package com.verygana2.services;

import java.time.Instant;
import java.util.List;
import java.util.Locale.Category;

import org.hibernate.ObjectNotFoundException;
import org.springframework.stereotype.Service;

import com.verygana2.dtos.generic.EntityCreatedResponse;
import com.verygana2.dtos.product.requests.CreateProductCategoryRequest;
import com.verygana2.dtos.product.responses.ProductCategoryResponseDTO;
import com.verygana2.mappers.products.ProductCategoryMapper;
import com.verygana2.models.products.ProductCategory;
import com.verygana2.repositories.ProductCategoryRepository;
import com.verygana2.services.interfaces.ProductCategoryService;

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
    public EntityCreatedResponse create(CreateProductCategoryRequest request) {
        ProductCategory productCategory = productCategoryMapper.toProductCategory(request);
        productCategoryRepository.save(productCategory);
        return new EntityCreatedResponse("Product category created succesfully", Instant.now());
    }

    @Override
    public void delete(Long categoryId) {
        if (!productCategoryRepository.existsById(categoryId)) {
            throw new ObjectNotFoundException("Product category not found with id: " + categoryId, Category.class);
        }

        productCategoryRepository.deleteById(categoryId);
    }

    @Override
    public ProductCategory getById(Long categoryId) {
        if (categoryId == null) {
            throw new IllegalArgumentException("The product category id cannot be null");
        }
        if (categoryId <= 0) {
            throw new IllegalArgumentException("The product category id must be positive");
        }
        
        return productCategoryRepository.findById(categoryId).orElseThrow(() -> new ObjectNotFoundException("ProductCategory with id: " + categoryId + " not found", ProductCategory.class));
        
    }

    @Override
    public List<ProductCategoryResponseDTO> getProductCategories() {
        return productCategoryRepository.findAvailableProductCategories().stream().map(productCategoryMapper::toProductCategoryResponseDTO).toList();
    }




    //@Override
    //public ProductCategoryResponseDTO getById(Long categoryId) {
      //  if (categoryId == null) {
       //     throw new IllegalArgumentException("The categoryId cannot be null");
       // }
       // if (categoryId <= 0) {
          //  throw new IllegalArgumentException("The categoryId must be positive");
       // }
        
       // ProductCategory productCategory = productCategoryRepository.findById(categoryId).orElseThrow(() -> new ObjectNotFoundException("ProductCategory with id: " + categoryId + " not found", ProductCategory.class));
       // ProductCategoryResponseDTO response = productCategoryMapper.toProductCategoryResponseDTO(productCategory);
       // return response;
   // }
    
}
