package com.grocery.product.entity;

import com.grocery.product.enums.ReviewStatus;
import com.grocery.product.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductReviewTest {

    @Test
    void onlyTheFirstNameAndAnInitialAreShownPublicly() {
        assertThat(ProductReview.publicName("Priya Sharma")).isEqualTo("Priya S.");
        assertThat(ProductReview.publicName("Kavya  Mohan Rao")).isEqualTo("Kavya R.");
        assertThat(ProductReview.publicName("Arjun")).isEqualTo("Arjun");
        assertThat(ProductReview.publicName(" ")).isEqualTo("Customer");
    }

    @Test
    void starsMustBeOneToFive_andAHiddenReviewCanBeShownAgain() {
        Product product = new Product();
        product.setId(1L);
        product.setStoreId(1L);
        assertThatThrownBy(() -> ProductReview.write(product, 9L, 4L, "Priya S.", 6, null)).isInstanceOf(BusinessException.class);

        ProductReview review = ProductReview.write(product, 9L, 4L, "Priya S.", 4, "Fresh");
        review.hide("Spam");
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.HIDDEN);
        review.show();
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.VISIBLE);
        assertThat(review.getHiddenReason()).isNull();
    }

    @Test
    void productRating_isRoundedToOneDecimal() {
        Product product = new Product();
        product.updateRating(13.0 / 3, 3);
        assertThat(product.getRatingAverage()).isEqualByComparingTo("4.3");

        product.updateRating(null, 0);
        assertThat(product.getRatingAverage()).isNull();
        assertThat(product.getRatingCount()).isZero();
    }
}
