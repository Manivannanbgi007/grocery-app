package com.grocery.platform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A switch for a feature, e.g. DELIVERY_SLOTS. The frontend reads the enabled flags and shows or hides the feature.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // for JPA only; use FeatureFlag.create(...)
@Entity
@Table(name = "feature_flags")
public class FeatureFlag extends BaseEntity {

    @Column(name = "flag_key", nullable = false, unique = true, length = 60, updatable = false)
    private String key;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;

    public static FeatureFlag create(String key, String description, boolean enabled, Long createdByUserId) {
        FeatureFlag flag = new FeatureFlag();
        flag.key = key;
        flag.description = description;
        flag.enabled = enabled;
        flag.updatedByUserId = createdByUserId;
        return flag;
    }

    public void switchTo(boolean enabled, Long changedByUserId) {
        this.enabled = enabled;
        this.updatedByUserId = changedByUserId;
    }
}
