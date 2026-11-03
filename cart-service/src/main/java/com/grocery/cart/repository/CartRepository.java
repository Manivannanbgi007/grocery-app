package com.grocery.cart.repository;

import com.grocery.cart.entity.Cart;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    /** Loads the cart together with its items in one query. */
    @EntityGraph(attributePaths = "items")
    Optional<Cart> findByCustomerUserId(Long customerUserId);
}
