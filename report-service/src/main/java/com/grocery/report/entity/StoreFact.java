package com.grocery.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A store's name and status (for "active stores" and the names in the reports).
 */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // for JPA only
@Entity
@Table(name = "store_facts")
public class StoreFact {

    @Id
    @Column(name = "store_id")
    private Long storeId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(nullable = false, length = 20)
    private String status;

    public StoreFact(Long storeId, String name, Long ownerUserId, String status) {
        this.storeId = storeId;
        this.name = name;
        this.ownerUserId = ownerUserId;
        this.status = status;
    }
}
