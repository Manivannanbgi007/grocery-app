package com.grocery.cart.entity;

import com.grocery.cart.exception.BusinessException;
import com.grocery.cart.exception.CartStoreConflictException;
import com.grocery.cart.mapper.CartMapper;
import org.junit.jupiter.api.Test;

import static com.grocery.cart.support.TestData.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The cart rules (plain JUnit - no Spring, no database).
 */
class CartTest {

    private final CartMapper cartMapper = new CartMapper();

    @Test
    void firstItem_decidesTheStoreOfTheCart() {
        Cart cart = Cart.newCart(4L);

        cart.addItem(1L, cartMapper.toCartItem(product(1, 1, "Tomato", "36.00", 50), 2));

        assertThat(cart.getStoreId()).isEqualTo(1L);
        assertThat(cart.getItems()).hasSize(1);
    }

    @Test
    void itemOfAnotherStore_isRejectedWith409() {
        Cart cart = Cart.newCart(4L);
        cart.addItem(1L, cartMapper.toCartItem(product(1, 1, "Tomato", "36.00", 50), 2));

        assertThatThrownBy(() -> cart.addItem(2L, cartMapper.toCartItem(product(11, 2, "Spinach", "30.00", 50), 1)))
                .isInstanceOf(CartStoreConflictException.class);
        assertThat(cart.getItems()).hasSize(1);
    }

    @Test
    void addingTheSameProductAgain_increasesTheQuantity_andTakesTheNewPrice() {
        Cart cart = Cart.newCart(4L);
        cart.addItem(1L, cartMapper.toCartItem(product(1, 1, "Tomato", "36.00", 50), 2));

        cart.addItem(1L, cartMapper.toCartItem(product(1, 1, "Tomato", "34.00", 50), 3));

        assertThat(cart.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getQuantity()).isEqualTo(5);
            assertThat(item.getSellingPrice()).isEqualByComparingTo("34.00");
        });
    }

    @Test
    void quantityAbove20_isRejected() {
        Cart cart = Cart.newCart(4L);
        cart.addItem(1L, cartMapper.toCartItem(product(1, 1, "Tomato", "36.00", 50), 15));

        assertThatThrownBy(() -> cart.addItem(1L, cartMapper.toCartItem(product(1, 1, "Tomato", "36.00", 50), 6)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Quantity must be between 1 and 20");
    }

    @Test
    void removingTheLastItem_resetsStoreAndCoupon() {
        Cart cart = Cart.newCart(4L);
        cart.addItem(1L, cartMapper.toCartItem(product(1, 1, "Tomato", "36.00", 50), 2));
        cart.applyCoupon("SAVE10");

        cart.removeItem(1L);

        assertThat(cart.isEmpty()).isTrue();
        assertThat(cart.getStoreId()).isNull();
        assertThat(cart.getCouponCode()).isNull();
        // ...so any store's products can be added again
        cart.addItem(2L, cartMapper.toCartItem(product(11, 2, "Spinach", "30.00", 50), 1));
        assertThat(cart.getStoreId()).isEqualTo(2L);
    }

    @Test
    void aCouponNeedsItemsInTheCart() {
        assertThatThrownBy(() -> Cart.newCart(4L).applyCoupon("SAVE10")).isInstanceOf(BusinessException.class);
    }
}
