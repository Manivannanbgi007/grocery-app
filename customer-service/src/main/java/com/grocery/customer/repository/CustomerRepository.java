package com.grocery.customer.repository;

import com.grocery.customer.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    /**
     * Admin customer search. The keyword (optional, null = all) matches part of the name, email or phone.
     */
    @Query("""
            SELECT c FROM Customer c
            WHERE (:keyword IS NULL
                   OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(c.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR c.phone LIKE CONCAT('%', :keyword, '%'))
            """)
    Page<Customer> search(String keyword, Pageable pageable);
}
