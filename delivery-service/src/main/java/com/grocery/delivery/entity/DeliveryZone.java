package com.grocery.delivery.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * An area served by a group of delivery partners, defined by PIN codes. A delivery belongs to the
 * zone of the store it is picked up from. partnerPayout is what a partner earns per delivery in this zone.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "delivery_zones")
public class DeliveryZone extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false, length = 60)
    private String city;

    @ElementCollection
    @CollectionTable(name = "delivery_zone_pincodes", joinColumns = @JoinColumn(name = "zone_id"))
    @Column(name = "pincode", nullable = false, length = 6)
    private Set<String> pincodes = new HashSet<>();

    @Column(name = "partner_payout", nullable = false, precision = 10, scale = 2)
    private BigDecimal partnerPayout;

    @Column(nullable = false)
    private boolean active;

    public boolean covers(String pincode) {
        return active && pincodes.contains(pincode);
    }

    public void replacePincodes(Set<String> newPincodes) {
        pincodes.clear();
        pincodes.addAll(newPincodes);
    }
}
