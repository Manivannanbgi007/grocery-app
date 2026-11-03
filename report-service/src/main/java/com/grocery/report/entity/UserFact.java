package com.grocery.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // for JPA only
@Entity
@Table(name = "user_facts")
public class UserFact {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    public UserFact(Long userId, String role, Instant registeredAt) {
        this.userId = userId;
        this.role = role;
        this.registeredAt = registeredAt;
    }
}
