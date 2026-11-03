package com.grocery.platform.service.impl;

import com.grocery.platform.dto.request.FeatureFlagRequest;
import com.grocery.platform.dto.response.FeatureFlagResponse;
import com.grocery.platform.entity.FeatureFlag;
import com.grocery.platform.exception.DuplicateResourceException;
import com.grocery.platform.exception.ResourceNotFoundException;
import com.grocery.platform.mapper.PlatformMapper;
import com.grocery.platform.repository.FeatureFlagRepository;
import com.grocery.platform.service.AuditLogService;
import com.grocery.platform.service.FeatureFlagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureFlagServiceImpl implements FeatureFlagService {

    private final FeatureFlagRepository featureFlagRepository;
    private final PlatformMapper platformMapper;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public Map<String, Boolean> getFlagStates() {
        Map<String, Boolean> states = new LinkedHashMap<>();
        featureFlagRepository.findAllByOrderByKeyAsc().forEach(flag -> states.put(flag.getKey(), flag.isEnabled()));
        return states;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeatureFlagResponse> listFlags() {
        return featureFlagRepository.findAllByOrderByKeyAsc().stream().map(platformMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public FeatureFlagResponse createFlag(Long superAdminUserId, FeatureFlagRequest request) {
        if (featureFlagRepository.existsByKey(request.key())) {
            throw new DuplicateResourceException("A feature flag " + request.key() + " already exists");
        }
        FeatureFlag flag = featureFlagRepository.save(
                FeatureFlag.create(request.key(), request.description().trim(), request.enabled(), superAdminUserId));
        auditLogService.recordChange("FeatureFlagCreated", flag.getKey(), superAdminUserId, request.toString());
        return platformMapper.toResponse(flag);
    }

    @Override
    @Transactional
    public FeatureFlagResponse switchFlag(Long superAdminUserId, String key, boolean enabled) {
        FeatureFlag flag = featureFlagRepository.findByKey(key)
                .orElseThrow(() -> new ResourceNotFoundException("Feature flag " + key + " does not exist"));
        flag.switchTo(enabled, superAdminUserId);
        auditLogService.recordChange("FeatureFlagChanged", key, superAdminUserId, "{\"key\": \"" + key + "\", \"enabled\": " + enabled + "}");
        log.info("Feature flag {} switched {} by user id={}", key, enabled ? "ON" : "OFF", superAdminUserId);
        return platformMapper.toResponse(flag);
    }
}
