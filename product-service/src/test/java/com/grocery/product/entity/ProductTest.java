package com.grocery.product.entity;

import com.grocery.product.enums.ProductStatus;
import com.grocery.product.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pricing, status and image rules of the Product entity (plain JUnit - no Spring, no database).
 */
class ProductTest {

    @Test
    void updatePricing_calculatesTheSellingPrice_roundedToPaise() {
        Product product = new Product();

        product.updatePricing(new BigDecimal("45.00"), new BigDecimal("5"));

        assertThat(product.getSellingPrice()).isEqualByComparingTo("42.75");
    }

    @Test
    void updatePricing_withoutDiscount_sellsAtThePrice() {
        Product product = new Product();

        product.updatePricing(new BigDecimal("35.00"), BigDecimal.ZERO);

        assertThat(product.getSellingPrice()).isEqualByComparingTo("35.00");
    }

    @Test
    void vendor_canSwitchBetweenActiveAndInactive() {
        Product product = productWithStatus(ProductStatus.ACTIVE);

        product.changeStatusByVendor(ProductStatus.INACTIVE);

        assertThat(product.getStatus()).isEqualTo(ProductStatus.INACTIVE);
    }

    @Test
    void vendor_cannotUndoAnAdminBlock() {
        Product product = productWithStatus(ProductStatus.BLOCKED);

        assertThatThrownBy(() -> product.changeStatusByVendor(ProductStatus.ACTIVE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("blocked by an admin");
    }

    @Test
    void vendor_cannotSetBlockedOrDeletedThroughTheStatusEndpoint() {
        Product product = productWithStatus(ProductStatus.ACTIVE);

        assertThatThrownBy(() -> product.changeStatusByVendor(ProductStatus.BLOCKED))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void unblock_makesTheProductActive_butOnlyWhenItIsBlocked() {
        Product blocked = productWithStatus(ProductStatus.BLOCKED);
        blocked.unblock();
        assertThat(blocked.getStatus()).isEqualTo(ProductStatus.ACTIVE);

        Product active = productWithStatus(ProductStatus.ACTIVE);
        assertThatThrownBy(active::unblock).isInstanceOf(BusinessException.class);
    }

    @Test
    void aProductHasAtMostFiveImages() {
        Product product = productWithStatus(ProductStatus.ACTIVE);
        for (int i = 1; i <= Product.MAX_IMAGES; i++) {
            product.addImage("/media/products/1/" + i + ".jpg");
        }

        assertThatThrownBy(() -> product.addImage("/media/products/1/6.jpg"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("A product can have at most 5 images");
    }

    private Product productWithStatus(ProductStatus status) {
        Product product = new Product();
        product.setStatus(status);
        return product;
    }
}
