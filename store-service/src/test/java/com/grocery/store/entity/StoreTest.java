package com.grocery.store.entity;

import com.grocery.store.enums.StoreStatus;
import com.grocery.store.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The store's status rules and opening-hours logic (plain JUnit - no Spring, no database).
 */
class StoreTest {

    @Test
    void approve_movesAPendingStoreToActive() {
        Store store = storeWithStatus(StoreStatus.PENDING_APPROVAL);

        store.approve(2L, Instant.parse("2026-10-26T10:00:00Z"));

        assertThat(store.getStatus()).isEqualTo(StoreStatus.ACTIVE);
        assertThat(store.getApprovedByUserId()).isEqualTo(2L);
        assertThat(store.getApprovedAt()).isNotNull();
    }

    @Test
    void approve_isNotAllowed_forAStoreThatIsAlreadyActive() {
        Store store = storeWithStatus(StoreStatus.ACTIVE);

        assertThatThrownBy(() -> store.approve(2L, Instant.now()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Only a PENDING_APPROVAL store can be approved (current status: ACTIVE)");
    }

    @Test
    void reject_keepsTheReason_andEditingTheStoreSendsItBackForApproval() {
        Store store = storeWithStatus(StoreStatus.PENDING_APPROVAL);

        store.reject("Address could not be verified");
        assertThat(store.getStatus()).isEqualTo(StoreStatus.REJECTED);
        assertThat(store.getStatusReason()).isEqualTo("Address could not be verified");

        store.resubmitIfRejected();
        assertThat(store.getStatus()).isEqualTo(StoreStatus.PENDING_APPROVAL);
        assertThat(store.getStatusReason()).isNull();
    }

    @Test
    void disableAndEnable_switchBetweenActiveAndDisabled() {
        Store store = storeWithStatus(StoreStatus.ACTIVE);

        store.disable("Too many complaints");
        assertThat(store.getStatus()).isEqualTo(StoreStatus.DISABLED);

        store.enable();
        assertThat(store.getStatus()).isEqualTo(StoreStatus.ACTIVE);
        assertThat(store.getStatusReason()).isNull();
    }

    @Test
    void enable_isNotAllowed_forAPendingStore() {
        Store store = storeWithStatus(StoreStatus.PENDING_APPROVAL);

        assertThatThrownBy(store::enable).isInstanceOf(BusinessException.class);
    }

    @Test
    void isOpenAt_followsTheOpeningHoursOfThatDay() {
        Store store = storeWithStatus(StoreStatus.ACTIVE);
        store.replaceOpeningHours(List.of(
                OpeningHours.open(DayOfWeek.MONDAY, LocalTime.of(7, 0), LocalTime.of(22, 0)),
                OpeningHours.closedAllDay(DayOfWeek.SUNDAY)));

        LocalDateTime mondayNoon = LocalDateTime.of(2026, 10, 26, 12, 0);       // a Monday
        LocalDateTime mondayLateNight = LocalDateTime.of(2026, 10, 26, 22, 0);  // closing time = closed
        LocalDateTime sundayNoon = LocalDateTime.of(2026, 10, 25, 12, 0);       // closed all day

        assertThat(store.isOpenAt(mondayNoon)).isTrue();
        assertThat(store.isOpenAt(mondayLateNight)).isFalse();
        assertThat(store.isOpenAt(sundayNoon)).isFalse();
    }

    @Test
    void deliversTo_onlyTheConfiguredPincodes() {
        Store store = storeWithStatus(StoreStatus.ACTIVE);
        store.replaceDeliveryPincodes(Set.of("560038", "560008"));

        assertThat(store.deliversTo("560008")).isTrue();
        assertThat(store.deliversTo("110001")).isFalse();
    }

    private Store storeWithStatus(StoreStatus status) {
        Store store = new Store();
        store.setName("Daily Needs");
        store.setStatus(status);
        return store;
    }
}
