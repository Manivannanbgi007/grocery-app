package com.grocery.order.entity;

import com.grocery.order.enums.ChangedBy;
import com.grocery.order.enums.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One step in the life of an order. The list of steps is the customer's tracking timeline.
 * Rows are only added, never changed.
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "order_status_history")
public class OrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(length = 255)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "changed_by", nullable = false, length = 20)
    private ChangedBy changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    public OrderStatusHistory(Order order, OrderStatus status, String note, ChangedBy changedBy, Instant changedAt) {
        this.order = order;
        this.status = status;
        this.note = note;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
    }
}
