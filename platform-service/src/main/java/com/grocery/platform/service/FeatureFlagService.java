package com.grocery.platform.service;

import com.grocery.platform.dto.request.FeatureFlagRequest;
import com.grocery.platform.dto.response.FeatureFlagResponse;

import java.util.List;
import java.util.Map;

public interface FeatureFlagService {

    /** Public: flag key -> enabled, e.g. {"DELIVERY_SLOTS": false}. */
    Map<String, Boolean> getFlagStates();

    List<FeatureFlagResponse> listFlags();

    FeatureFlagResponse createFlag(Long superAdminUserId, FeatureFlagRequest request);

    FeatureFlagResponse switchFlag(Long superAdminUserId, String key, boolean enabled);
}
