package com.grocery.cart.repository;

import com.grocery.cart.entity.PricingSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PricingSettingsRepository extends JpaRepository<PricingSettings, Long> {
}
