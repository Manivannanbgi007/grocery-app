package com.grocery.cart.service.impl;

import com.grocery.cart.entity.PricingSettings;
import com.grocery.cart.repository.PricingSettingsRepository;
import com.grocery.cart.service.PricingSettingsService;
import com.grocery.cart.service.ProcessedEventService;
import com.grocery.events.platform.PlatformSettingsChanged;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class PricingSettingsServiceImpl implements PricingSettingsService {

    private final PricingSettingsRepository pricingSettingsRepository;
    private final ProcessedEventService processedEventService;

    @Override
    @Transactional
    public void applyPlatformSettings(PlatformSettingsChanged event) {
        if (!processedEventService.markAsProcessed(event.getEventId(), "PlatformSettingsChanged")) {
            return;
        }
        PricingSettings settings = pricingSettingsRepository.findById(PricingSettings.SINGLE_ROW_ID)
                .orElseThrow(() -> new IllegalStateException("The pricing_settings row is missing (see migration V4)"));
        settings.update(new BigDecimal(event.getDeliveryFee()), new BigDecimal(event.getFreeDeliveryFrom()),
                new BigDecimal(event.getMinimumOrderValue()));
        log.info("New pricing rules: delivery fee {}, free delivery from {}, minimum order {}",
                event.getDeliveryFee(), event.getFreeDeliveryFrom(), event.getMinimumOrderValue());
    }
}
