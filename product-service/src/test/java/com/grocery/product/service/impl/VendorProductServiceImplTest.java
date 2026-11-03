package com.grocery.product.service.impl;

import com.grocery.product.dto.request.ProductRequest;
import com.grocery.product.dto.response.ProductResponse;
import com.grocery.product.entity.Category;
import com.grocery.product.entity.Product;
import com.grocery.product.enums.ProductStatus;
import com.grocery.product.enums.Unit;
import com.grocery.product.exception.BusinessException;
import com.grocery.product.exception.DuplicateResourceException;
import com.grocery.product.exception.ResourceNotFoundException;
import com.grocery.product.mapper.ProductMapper;
import com.grocery.product.repository.CategoryRepository;
import com.grocery.product.repository.ProductRepository;
import com.grocery.product.service.ImageStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VendorProductServiceImplTest {

    private static final long STORE_ID = 1L;

    @Mock
    private ProductRepository productRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ImageStorageService imageStorageService;

    private VendorProductServiceImpl vendorProductService;

    @BeforeEach
    void setUp() {
        vendorProductService = new VendorProductServiceImpl(productRepository, categoryRepository, new ProductMapper(), imageStorageService);
    }

    @Test
    void createProduct_savesAnActiveProductOfTheVendorsStore_withCalculatedSellingPrice() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category(1L, true)));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            product.setId(100L);
            return product;
        });

        ProductResponse response = vendorProductService.createProduct(STORE_ID, request("fm-veg-020"));

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.storeId()).isEqualTo(STORE_ID);
        assertThat(response.status()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(response.sku()).isEqualTo("FM-VEG-020");
        assertThat(response.sellingPrice()).isEqualByComparingTo("40.50");   // 45.00 - 10 %
        assertThat(response.categoryName()).isEqualTo("Vegetables");
    }

    @Test
    void createProduct_fails_forAnInactiveCategory() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category(1L, false)));

        assertThatThrownBy(() -> vendorProductService.createProduct(STORE_ID, request("FM-VEG-020")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not active");
        verify(productRepository, never()).save(any());
    }

    @Test
    void createProduct_fails_whenTheSkuIsAlreadyUsedInTheStore() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category(1L, true)));
        when(productRepository.existsByStoreIdAndSkuIgnoreCaseAndStatusNot(STORE_ID, "FM-VEG-001", ProductStatus.DELETED))
                .thenReturn(true);

        assertThatThrownBy(() -> vendorProductService.createProduct(STORE_ID, request("FM-VEG-001")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void updateProduct_ofAnotherStore_isReportedAsNotFound() {
        when(productRepository.findByIdAndStoreIdAndStatusNot(11L, STORE_ID, ProductStatus.DELETED)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vendorProductService.updateProduct(STORE_ID, 11L, request("GB-VEG-001")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product not found with id: 11");
    }

    private ProductRequest request(String sku) {
        return new ProductRequest(1L, "Cherry Tomato", "Sweet cherry tomatoes", null, sku, Unit.G,
                new BigDecimal("250"), new BigDecimal("45.00"), new BigDecimal("10"), BigDecimal.ZERO);
    }

    private Category category(Long id, boolean active) {
        Category category = new Category();
        category.setId(id);
        category.setName("Vegetables");
        category.setActive(active);
        return category;
    }
}
