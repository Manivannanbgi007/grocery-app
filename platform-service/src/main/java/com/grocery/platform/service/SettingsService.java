package com.grocery.platform.service;

import com.grocery.platform.dto.request.SettingsRequest;
import com.grocery.platform.dto.response.SettingsResponse;

/**
 * The platform settings: everybody can read them, the super admin changes them (PlatformSettingsChanged).
 */
public interface SettingsService {

    SettingsResponse getSettings();

    SettingsResponse updateSettings(Long superAdminUserId, SettingsRequest request);
}
