package com.grocery.platform.service.impl;

import com.grocery.platform.constant.KafkaTopics;
import com.grocery.platform.dto.request.SettingsRequest;
import com.grocery.platform.dto.response.SettingsResponse;
import com.grocery.platform.entity.PlatformSettings;
import com.grocery.platform.exception.BusinessException;
import com.grocery.platform.mapper.PlatformEventMapper;
import com.grocery.platform.mapper.PlatformMapper;
import com.grocery.platform.messaging.OutboxEventPublisher;
import com.grocery.platform.repository.PlatformSettingsRepository;
import com.grocery.platform.service.AuditLogService;
import com.grocery.platform.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsServiceImpl implements SettingsService {

    private static final String SETTINGS_KEY = "settings";

    private final PlatformSettingsRepository settingsRepository;
    private final PlatformMapper platformMapper;
    private final PlatformEventMapper platformEventMapper;
    private final OutboxEventPublisher outboxEventPublisher;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public SettingsResponse getSettings() {
        return platformMapper.toResponse(currentSettings());
    }

    @Override
    @Transactional
    public SettingsResponse updateSettings(Long superAdminUserId, SettingsRequest request) {
        if (request.minimumOrderValue().compareTo(request.freeDeliveryFrom()) > 0) {
            throw new BusinessException("The minimum order value cannot be higher than the free delivery amount");
        }
        PlatformSettings settings = currentSettings();
        settings.update(request.currency(), request.deliveryFee(), request.freeDeliveryFrom(), request.minimumOrderValue(),
                request.supportEmail().trim(), request.supportPhone().trim(), superAdminUserId);

        outboxEventPublisher.publish(KafkaTopics.PLATFORM_AGGREGATE, SETTINGS_KEY, platformEventMapper.toPlatformSettingsChanged(settings));
        auditLogService.recordChange("PlatformSettingsUpdated", SETTINGS_KEY, superAdminUserId, request.toString());
        log.info("Platform settings changed by user id={}: delivery fee {}, free from {}, minimum {}", superAdminUserId,
                request.deliveryFee(), request.freeDeliveryFrom(), request.minimumOrderValue());
        return platformMapper.toResponse(settings);
    }

    private PlatformSettings currentSettings() {
        return settingsRepository.findById(PlatformSettings.SINGLE_ROW_ID)
                .orElseThrow(() -> new IllegalStateException("The platform_settings row is missing (see migration V1)"));
    }
}
