package com.grocery.product.service.impl;

import com.grocery.product.dto.response.ProductResponse;
import com.grocery.product.entity.Product;
import com.grocery.product.enums.ProductStatus;
import com.grocery.product.exception.ResourceNotFoundException;
import com.grocery.product.mapper.ProductMapper;
import com.grocery.product.repository.ProductRepository;
import com.grocery.product.service.AdminProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminProductServiceImpl implements AdminProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Override
    @Transactional
    public ProductResponse blockProduct(Long productId) {
        Product product = findProduct(productId);
        product.block();
        log.info("Product id={} blocked by an admin", productId);
        return productMapper.toResponse(product);
    }

    @Override
    @Transactional
    public ProductResponse unblockProduct(Long productId) {
        Product product = findProduct(productId);
        product.unblock();
        log.info("Product id={} unblocked by an admin", productId);
        return productMapper.toResponse(product);
    }

    private Product findProduct(Long productId) {
        return productRepository.findById(productId)
                .filter(product -> product.getStatus() != ProductStatus.DELETED)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
    }
}
