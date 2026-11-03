package com.grocery.cart.service;

import com.grocery.events.platform.PlatformSettingsChanged;

public interface PricingSettingsService {

    /** Stores the new delivery fee, free delivery amount and minimum order (idempotent). */
    void applyPlatformSettings(PlatformSettingsChanged event);
}
