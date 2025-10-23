package com.verygana2.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.hibernate.ObjectNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.generic.EntityCreatedResponse;
import com.verygana2.dtos.products.requests.CreateOrEditProductRequest;
import com.verygana2.dtos.products.responses.ProductResponse;
import com.verygana2.dtos.products.responses.ProductSummaryResponse;
import com.verygana2.exceptions.FavoriteProductException;
import com.verygana2.mappers.products.ProductMapper;
import com.verygana2.models.products.Product;
import com.verygana2.models.products.ProductCategory;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.models.userDetails.SellerDetails;
import com.verygana2.repositories.ProductRepository;
import com.verygana2.repositories.details.ConsumerDetailsRepository;
import com.verygana2.services.interfaces.ProductCategoryService;
import com.verygana2.services.interfaces.ProductService;
import com.verygana2.services.interfaces.details.ConsumerDetailsService;
import com.verygana2.services.interfaces.details.SellerDetailsService;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    private final ProductCategoryService productCategoryService;

    private final ProductMapper productMapper;

    private final SellerDetailsService sellerDetailsService;

    private final ConsumerDetailsService consumerDetailsService;

    private final ConsumerDetailsRepository consumerDetailsRepository;

    @Override
    public EntityCreatedResponse create(CreateOrEditProductRequest request, Long sellerId) {
        SellerDetails seller = sellerDetailsService.getSellerById(sellerId);
        Product product = productMapper.toProduct(request);
        ProductCategory category = productCategoryService.getById(request.getCategoryId());
        product.setSeller(seller);
        product.setCategory(category);
        productRepository.save(product);
        return new EntityCreatedResponse("Product created succesfully", Instant.now());
    }

    @Override
    public Product getById(Long productId) {
        return productRepository.findById(productId).orElseThrow(
                () -> new ObjectNotFoundException("Product with id: " + productId + " not found", Product.class));
    }

    @Override
    public void delete(Long productId, Long sellerId) {
        if (!productRepository.existsByIdAndSellerId(productId, sellerId)) {
            throw new ObjectNotFoundException(
                    "Product with id: " + productId + " and sellerId: " + sellerId + " not found", Product.class);
        }
        Product product = getById(productId);
        product.setActive(false);
        productRepository.save(product);
    }

    @Override
    public void deleteForAdmin(Long productId) {
        // send a notification to product's owner with the reason of product elimination
        Product product = getById(productId);
        product.setActive(false);
        productRepository.save(product);
    }

    @Override
    public void edit(Long productId, Long sellerId, CreateOrEditProductRequest createOrEditProductRequest) {
        if (!productRepository.existsByIdAndSellerId(productId, sellerId)) {
            throw new ObjectNotFoundException(
                    "Product with id: " + productId + " and sellerId: " + sellerId + " not found", Product.class);
        }
        Product product = getById(productId);
        productMapper.updateProductFromRequest(createOrEditProductRequest, product);

        ProductCategory category = productCategoryService.getById(createOrEditProductRequest.getCategoryId());
        product.setCategory(category);
        productRepository.save(product);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> getAllProducts(Integer page) {
        Pageable pageable = PageRequest.of(page, 20, Direction.DESC, "createdAt");
        Page<Product> activeProducts = productRepository.findAllActiveProducts(pageable);
        return activeProducts.map(productMapper::toProductSummaryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> filterProducts(String searchQuery, Long categoryId, Double minRating,
            BigDecimal maxPrice, Integer page,
            String sortBy, String sortDirection) {

        String sortField = validateAndGetSortField(sortBy);
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, sortField);

        int indexPage = (page != null && page >= 0) ? page : 0;
        Pageable pageable = PageRequest.of(indexPage, 20, sort);

        Page<Product> productPage = productRepository.searchProducts(searchQuery, categoryId, minRating, maxPrice,
                pageable);

        return productPage.map(productMapper::toProductSummaryResponse);

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
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("the product id must be positive");
        }
        Product product = productRepository.findById(productId).orElseThrow(
                () -> new ObjectNotFoundException("the product with id: " + productId + " not found", Product.class));
        ProductResponse response = productMapper.toProductResponse(product);
        return response;
    }

    @Override
    public Page<ProductSummaryResponse> getSellerProducts(Long sellerId, Integer page) {

        if (!sellerDetailsService.existsSellerById(sellerId)) {
            throw new ObjectNotFoundException("Seller with id:" + sellerId + " not found", SellerDetails.class);
        }

        Integer pageIndex = (page != null && page >= 0) ? page : 0;
        Pageable pageable = PageRequest.of(pageIndex, 20, Sort.Direction.DESC, "createdAt");
        Page<Product> products = productRepository.findBySellerId(sellerId, pageable);
        return products.map(productMapper::toProductSummaryResponse);
    }

    @Override
    public void getProductStats(Long productId, Long userId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getProductStats'");
    }

    @Override
    public List<String> getBestSellers() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getBestSellers'");
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> getFavorites(Long userId, Integer page) {
        Pageable pageable = PageRequest.of(page, 20, Sort.Direction.DESC, "createdAt");
        Page<Product> favorites = productRepository.findFavoriteProductsByUserId(userId, pageable);
        return favorites.map(productMapper::toProductSummaryResponse);
    }

    @Override
    public void addFavorite(Long userId, Long productId) {
        ConsumerDetails consumer = consumerDetailsService.getConsumerById(userId);
        Product product = getById(productId);
        List<Product> favoriteList = consumer.getFavoriteProducts();

        if (favoriteList == null) {
            favoriteList = new ArrayList<>();
            consumer.setFavoriteProducts(favoriteList);
        }
        if (favoriteList.contains(product)) {
            throw new FavoriteProductException("This product already exists in your favorites list");
        }

        favoriteList.add(product);
        consumerDetailsRepository.save(consumer);
    }

    @Override
    public void removeFavorite(Long userId, Long productId) {
        ConsumerDetails consumer = consumerDetailsService.getConsumerById(userId);
        Product product = getById(productId);
        List<Product> favoriteList = consumer.getFavoriteProducts();

        if (favoriteList == null || favoriteList.isEmpty()) {
            throw new FavoriteProductException("Your favorites list is empty");
        }

        if (!favoriteList.contains(product)) {
            throw new FavoriteProductException("This product does not exists in your favorites list");
        }

        favoriteList.remove(product);
        consumerDetailsRepository.save(consumer);

    }

}
