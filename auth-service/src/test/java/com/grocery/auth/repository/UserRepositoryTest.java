package com.grocery.auth.repository;

import com.grocery.auth.TestcontainersConfiguration;
import com.grocery.auth.config.JpaAuditingConfig;
import com.grocery.auth.entity.User;
import com.grocery.auth.enums.Role;
import com.grocery.auth.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against a real MySQL (Testcontainers) with the real Flyway migrations,
 * so it also proves that the entities match the database schema.
 * Each test runs in a transaction that is rolled back afterwards.
 */
@DataJpaTest
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.saveAll(List.of(
                user("Priya Sharma", "priya@example.com", "9000000101", Role.CUSTOMER, UserStatus.ACTIVE),
                user("Rahul Verma", "rahul@example.com", "9000000102", Role.CUSTOMER, UserStatus.DISABLED),
                user("Ravi Kumar", "ravi@fresh-mart.com", "9000000103", Role.VENDOR, UserStatus.ACTIVE)));
    }

    @Test
    void search_withoutFilters_returnsEveryUser() {
        Page<User> result = userRepository.search(null, null, null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(3);
    }

    @Test
    void search_filtersByRoleAndStatus() {
        Page<User> result = userRepository.search(Role.CUSTOMER, UserStatus.ACTIVE, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(User::getEmail).containsExactly("priya@example.com");
    }

    @Test
    void search_matchesKeywordInNameOrEmail_ignoringCase() {
        assertThat(userRepository.search(null, null, "fresh-mart", PageRequest.of(0, 10)).getContent())
                .extracting(User::getFullName)
                .containsExactly("Ravi Kumar");

        assertThat(userRepository.search(null, null, "RAHUL", PageRequest.of(0, 10)).getContent())
                .extracting(User::getEmail)
                .containsExactly("rahul@example.com");
    }

    @Test
    void findByEmail_ignoresLetterCase() {
        assertThat(userRepository.findByEmail("PRIYA@EXAMPLE.COM")).isPresent();
    }

    @Test
    void save_fillsAuditColumnsAndVersion() {
        User saved = userRepository.findByEmail("priya@example.com").orElseThrow();

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getVersion()).isZero();
    }

    private User user(String name, String email, String phone, Role role, UserStatus status) {
        User user = new User();
        user.setFullName(name);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash("$2a$10$hash");
        user.setRole(role);
        user.setStatus(status);
        return user;
    }
}
