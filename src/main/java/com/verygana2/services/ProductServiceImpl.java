package com.verygana2.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import org.hibernate.ObjectNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.generic.EntityCreatedResponse;
import com.verygana2.dtos.products.requests.CreateOrEditProductRequest;
import com.verygana2.dtos.products.responses.ProductResponse;
import com.verygana2.dtos.products.responses.ProductSummaryResponse;
import com.verygana2.mappers.products.ProductMapper;
import com.verygana2.models.products.Product;
import com.verygana2.models.products.ProductCategory;
import com.verygana2.models.userDetails.SellerDetails;
import com.verygana2.repositories.ProductRepository;
import com.verygana2.services.interfaces.ProductCategoryService;
import com.verygana2.services.interfaces.ProductService;
import com.verygana2.services.interfaces.details.SellerDetailsService;



@Service
@Transactional
public class ProductServiceImpl implements ProductService {

    
    private final ProductRepository productRepository;
    
    private final ProductCategoryService productCategoryService;

    private final ProductMapper productMapper;

    private final SellerDetailsService sellerDetailsService;

    public ProductServiceImpl(ProductRepository productRepository, ProductCategoryService productCategoryService, ProductMapper productMapper, SellerDetailsService sellerDetailsService){
        this.productRepository = productRepository;
        this.productCategoryService = productCategoryService;
        this.productMapper = productMapper;
        this.sellerDetailsService = sellerDetailsService;
    }

    @Override
    public EntityCreatedResponse create(CreateOrEditProductRequest request, Long sellerId) {
        SellerDetails seller = sellerDetailsService.getSellerById(sellerId);
        Product product = productMapper.toProduct(request);
        ProductCategory category = productCategoryService.getById(sellerId);
        product.setSeller(seller);
        product.setCategory(category);
        productRepository.save(product);
        EntityCreatedResponse response = new EntityCreatedResponse("Product created succesfully", Instant.now());

        return response;
    }

    @Override
    public void delete (Long productId, Long sellerId){
        if (!productRepository.existsByIdAndSellerId(productId, sellerId)) {
            throw new ObjectNotFoundException("Product with id: " + productId + " and sellerId: " + sellerId + " not found", Product.class);
        }
        productRepository.deleteById(productId);
    }

    @Override
    public void edit(Long productId, Long sellerId, CreateOrEditProductRequest createOrEditProductRequest) {
        if (!productRepository.existsByIdAndSellerId(productId, sellerId)) {
            throw new ObjectNotFoundException("Product with id: " + productId + " and sellerId: " + sellerId + " not found", Product.class);
        }
        Product product = productMapper.toProduct(createOrEditProductRequest);
        product.setId(productId);
        productRepository.save(product);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> searchProducts(String searchQuery, Long categoryId, Double minRating,
            BigDecimal maxPrice, Integer page,
            String sortBy, String sortDirection) {

        String sortField = validateAndGetSortField(sortBy);
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, sortField);

        int indexPage = (page != null && page >= 0) ? page : 0;
        Pageable pageable = PageRequest.of(indexPage, 20, sort);

        Page<Product> productPage = productRepository.searchProducts(searchQuery, categoryId, minRating, maxPrice,
                pageable);
        Page<ProductSummaryResponse> productSummaryResponsePage = productPage
                .map(productMapper::toProductSummaryResponse);

        return productSummaryResponsePage;

    }

    private String validateAndGetSortField(String sortBy) {
        Set<String> allowedFields = Set.of(
                "price",
                "averageRate",
                "createdAt");
                
        if (sortBy != null && allowedFields.contains(sortBy)) {
            return sortBy;
        }

        return "createdAt";
    }

    @Override
    public ProductResponse detailProduct(Long productId) {
        if(productId == null || productId <= 0){
            throw new IllegalArgumentException("the product id must be positive");
        }
        Product product = productRepository.findById(productId).orElseThrow(() -> new ObjectNotFoundException("the product with id: " + productId + " not found", Product.class));
        ProductResponse response = productMapper.toProductResponse(product);
        return response;
    }


}
