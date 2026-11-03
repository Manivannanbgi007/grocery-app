package com.grocery.platform.mapper;

import com.grocery.events.platform.PlatformSettingsChanged;
import com.grocery.platform.entity.PlatformSettings;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Component
public class PlatformEventMapper {

    public PlatformSettingsChanged toPlatformSettingsChanged(PlatformSettings settings) {
        return PlatformSettingsChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setCurrency(settings.getCurrency())
                .setDeliveryFee(text(settings.getDeliveryFee()))
                .setFreeDeliveryFrom(text(settings.getFreeDeliveryFrom()))
                .setMinimumOrderValue(text(settings.getMinimumOrderValue()))
                .setChangedByUserId(settings.getUpdatedByUserId())
                .build();
    }

    /** 30 -> "30.00" */
    private String text(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
