package com.grocery.auth.repository;

import com.grocery.auth.entity.User;
import com.grocery.auth.enums.Role;
import com.grocery.auth.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

/**
 * Database access for users.
 * Note: MySQL's default collation compares text case-insensitively,
 * so findByEmail("Priya@Example.com") also finds "priya@example.com".
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    /**
     * Admin user search. Every filter is optional: pass null to ignore it.
     * The keyword matches part of the name or the email.
     */
    @Query("""
            SELECT u FROM User u
            WHERE (:role IS NULL OR u.role = :role)
              AND (:status IS NULL OR u.status = :status)
              AND (:keyword IS NULL
                   OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<User> search(Role role, UserStatus status, String keyword, Pageable pageable);
}
