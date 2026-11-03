package com.grocery.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Which vendor owns a store. Order and stock events only carry the storeId; this tells us whom to notify.
 * Filled from StoreRegistered / StoreApproved events (and the seed stores locally).
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "store_contacts")
public class StoreContact {

    /** The store id from store-service (not generated here). */
    @Id
    @Column(name = "store_id")
    private Long storeId;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "store_name", nullable = false, length = 100)
    private String storeName;
}
