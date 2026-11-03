package com.grocery.product.service.impl;

import com.grocery.product.dto.request.ProductRequest;
import com.grocery.product.dto.request.UpdateProductStatusRequest;
import com.grocery.product.dto.response.ProductResponse;
import com.grocery.product.entity.Category;
import com.grocery.product.entity.Product;
import com.grocery.product.enums.ProductStatus;
import com.grocery.product.exception.BusinessException;
import com.grocery.product.exception.DuplicateResourceException;
import com.grocery.product.exception.ResourceNotFoundException;
import com.grocery.product.mapper.ProductMapper;
import com.grocery.product.repository.CategoryRepository;
import com.grocery.product.repository.ProductRepository;
import com.grocery.product.service.ImageStorageService;
import com.grocery.product.service.VendorProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class VendorProductServiceImpl implements VendorProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;
    private final ImageStorageService imageStorageService;

    @Override
    @Transactional
    public ProductResponse createProduct(Long storeId, ProductRequest request) {
        Category category = findActiveCategory(request.categoryId());
        if (productRepository.existsByStoreIdAndSkuIgnoreCaseAndStatusNot(storeId, request.sku(), ProductStatus.DELETED)) {
            throw new DuplicateResourceException("Your store already has a product with SKU " + request.sku());
        }

        Product product = new Product();
        product.setStoreId(storeId);
        product.setStatus(ProductStatus.ACTIVE);
        productMapper.copyDetails(request, category, product);
        Product savedProduct = productRepository.save(product);

        log.info("Store id={} created product id={} ({})", storeId, savedProduct.getId(), savedProduct.getName());
        return productMapper.toResponse(savedProduct);
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long storeId, Long productId, ProductRequest request) {
        Product product = findOwnProduct(storeId, productId);
        Category category = Objects.equals(product.getCategory().getId(), request.categoryId())
                ? product.getCategory()                        // keeping the current category is always fine
                : findActiveCategory(request.categoryId());
        if (productRepository.existsByStoreIdAndSkuIgnoreCaseAndStatusNotAndIdNot(
                storeId, request.sku(), ProductStatus.DELETED, productId)) {
            throw new DuplicateResourceException("Your store already has a product with SKU " + request.sku());
        }

        productMapper.copyDetails(request, category, product);
        return productMapper.toResponse(product);
    }

    @Override
    @Transactional
    public ProductResponse updateProductStatus(Long storeId, Long productId, UpdateProductStatusRequest request) {
        Product product = findOwnProduct(storeId, productId);
        product.changeStatusByVendor(request.status());
        log.info("Product id={} status changed to {} by its store", productId, request.status());
        return productMapper.toResponse(product);
    }

    @Override
    @Transactional
    public void deleteProduct(Long storeId, Long productId) {
        Product product = findOwnProduct(storeId, productId);
        product.markDeleted();
        log.info("Product id={} deleted by store id={}", productId, storeId);
    }

    @Override
    @Transactional
    public ProductResponse addImage(Long storeId, Long productId, MultipartFile image) {
        Product product = findOwnProduct(storeId, productId);
        if (product.getImageUrls().size() >= Product.MAX_IMAGES) {
            throw new BusinessException("A product can have at most " + Product.MAX_IMAGES + " images");
        }
        product.addImage(imageStorageService.store(productId, image));
        return productMapper.toResponse(product);
    }

    @Override
    @Transactional
    public ProductResponse removeImage(Long storeId, Long productId, String imageUrl) {
        Product product = findOwnProduct(storeId, productId);
        if (!product.removeImage(imageUrl)) {
            throw new ResourceNotFoundException("This product has no image " + imageUrl);
        }
        imageStorageService.delete(imageUrl);
        return productMapper.toResponse(product);
    }

    /** Multi-tenant rule: a store only ever finds its own (not deleted) products. */
    private Product findOwnProduct(Long storeId, Long productId) {
        return productRepository.findByIdAndStoreIdAndStatusNot(productId, storeId, ProductStatus.DELETED)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
    }

    private Category findActiveCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
        if (!category.isActive()) {
            throw new BusinessException("Category '" + category.getName() + "' is not active");
        }
        return category;
    }
}
