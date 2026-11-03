package com.grocery.platform.repository;

import com.grocery.platform.entity.FeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, Long> {

    Optional<FeatureFlag> findByKey(String key);

    boolean existsByKey(String key);

    List<FeatureFlag> findAllByOrderByKeyAsc();
}
