package com.grocery.platform.mapper;

import com.grocery.platform.dto.response.AuditEntryResponse;
import com.grocery.platform.dto.response.BannerResponse;
import com.grocery.platform.dto.response.FeatureFlagResponse;
import com.grocery.platform.dto.response.SettingsResponse;
import com.grocery.platform.entity.AuditEntry;
import com.grocery.platform.entity.Banner;
import com.grocery.platform.entity.FeatureFlag;
import com.grocery.platform.entity.PlatformSettings;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class PlatformMapper {

    public SettingsResponse toResponse(PlatformSettings settings) {
        return new SettingsResponse(settings.getCurrency(), settings.getDeliveryFee(), settings.getFreeDeliveryFrom(),
                settings.getMinimumOrderValue(), settings.getSupportEmail(), settings.getSupportPhone(), settings.getUpdatedAt());
    }

    public FeatureFlagResponse toResponse(FeatureFlag flag) {
        return new FeatureFlagResponse(flag.getKey(), flag.getDescription(), flag.isEnabled(), flag.getUpdatedByUserId(),
                flag.getUpdatedAt());
    }

    public BannerResponse toResponse(Banner banner, Instant now) {
        return new BannerResponse(banner.getId(), banner.getTitle(), banner.getSubtitle(), banner.getImageUrl(),
                banner.getLinkUrl(), banner.getPosition(), banner.getStartsAt(), banner.getEndsAt(), banner.isActive(),
                banner.isLiveAt(now));
    }

    public AuditEntryResponse toResponse(AuditEntry entry) {
        return new AuditEntryResponse(entry.getId(), entry.getEventId(), entry.getEventType(), entry.getSource(),
                entry.getAggregateId(), entry.getUserId(), entry.getStoreId(), entry.getDetails(), entry.getOccurredAt());
    }
}
