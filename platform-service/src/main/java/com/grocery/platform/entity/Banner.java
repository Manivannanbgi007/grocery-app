package com.grocery.platform.entity;

import com.grocery.platform.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A promotional banner on the home page. It is shown while it is active and inside its optional time window.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // for JPA only; use new Banner() via Banner.create(...)
@Entity
@Table(name = "banners")
public class Banner extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 200)
    private String subtitle;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "link_url", length = 500)
    private String linkUrl;

    @Column(nullable = false)
    private int position;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(nullable = false)
    private boolean active;

    public static Banner create() {
        return new Banner();
    }

    public void update(String title, String subtitle, String imageUrl, String linkUrl, int position,
                       Instant startsAt, Instant endsAt, boolean active) {
        if (startsAt != null && endsAt != null && !endsAt.isAfter(startsAt)) {
            throw new BusinessException("endsAt must be after startsAt");
        }
        this.title = title;
        this.subtitle = subtitle;
        this.imageUrl = imageUrl;
        this.linkUrl = linkUrl;
        this.position = position;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.active = active;
    }

    /** true when customers should see it at this moment. */
    public boolean isLiveAt(Instant now) {
        return active
                && (startsAt == null || !now.isBefore(startsAt))
                && (endsAt == null || now.isBefore(endsAt));
    }
}
