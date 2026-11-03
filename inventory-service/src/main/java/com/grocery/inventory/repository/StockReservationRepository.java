package com.grocery.inventory.repository;

import com.grocery.inventory.entity.StockReservation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<StockReservation> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);
}
