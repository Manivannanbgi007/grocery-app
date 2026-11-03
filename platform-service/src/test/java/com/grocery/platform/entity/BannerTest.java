package com.grocery.platform.entity;

import com.grocery.platform.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BannerTest {

    private static final Instant START = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-11-15T00:00:00Z");

    @Test
    void aBannerIsLiveOnlyInsideItsTimeWindow_andWhileActive() {
        Banner banner = Banner.create();
        banner.update("Diwali sale", null, "http://img/diwali.jpg", null, 1, START, END, true);

        assertThat(banner.isLiveAt(START.minusSeconds(1))).isFalse();
        assertThat(banner.isLiveAt(START)).isTrue();
        assertThat(banner.isLiveAt(END.minusSeconds(1))).isTrue();
        assertThat(banner.isLiveAt(END)).isFalse();

        banner.update("Diwali sale", null, "http://img/diwali.jpg", null, 1, START, END, false);
        assertThat(banner.isLiveAt(START)).isFalse();
    }

    @Test
    void theWindowMustEndAfterItStarts() {
        assertThatThrownBy(() -> Banner.create().update("x", null, "http://img/x.jpg", null, 1, END, START, true))
                .isInstanceOf(BusinessException.class)
                .hasMessage("endsAt must be after startsAt");
    }
}
