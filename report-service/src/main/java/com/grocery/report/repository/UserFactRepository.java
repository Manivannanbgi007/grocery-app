package com.grocery.report.repository;

import com.grocery.report.entity.UserFact;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserFactRepository extends JpaRepository<UserFact, Long> {

    long countByRole(String role);
}
