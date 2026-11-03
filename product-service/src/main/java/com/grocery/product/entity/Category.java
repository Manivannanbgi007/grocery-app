package com.grocery.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A global product category (Vegetables, Fruits, Dairy ...), managed by admins.
 * Vendors choose one of these categories for each product.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "categories")
public class Category extends BaseEntity {

    @Column(nullable = false, unique = true, length = 60)
    private String name;

    /** URL-friendly name, e.g. "dairy-eggs" for "Dairy & Eggs". */
    @Column(nullable = false, unique = true, length = 70)
    private String slug;

    @Column(length = 255)
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /** Position on the home page (lower = first). */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /** Inactive categories are hidden, and no new products can be added to them. */
    @Column(nullable = false)
    private boolean active;
}
