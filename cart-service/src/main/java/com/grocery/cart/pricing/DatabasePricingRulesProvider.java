package com.grocery.cart.pricing;

import com.grocery.cart.entity.PricingSettings;
import com.grocery.cart.repository.PricingSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Reads the pricing rules from the pricing_settings row (kept up to date by PlatformSettingsChanged events).
 * One primary-key read per calculation: cheap, and a change is used at once.
 */
@Component
@RequiredArgsConstructor
public class DatabasePricingRulesProvider implements PricingRulesProvider {

    private final PricingSettingsRepository pricingSettingsRepository;

    @Override
    public PricingRules current() {
        PricingSettings settings = pricingSettingsRepository.findById(PricingSettings.SINGLE_ROW_ID)
                .orElseThrow(() -> new IllegalStateException("The pricing_settings row is missing (see migration V4)"));
        return new PricingRules(settings.getDeliveryFee(), settings.getFreeDeliveryFrom(), settings.getMinimumOrderValue());
    }
}
