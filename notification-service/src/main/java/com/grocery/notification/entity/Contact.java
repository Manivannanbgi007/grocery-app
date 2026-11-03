package com.grocery.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The local CONTACT BOOK: who a user id is and where to email them.
 * Filled from UserRegistered events (and the seed users locally), so notification-service never has
 * to ask auth-service for an email address while handling an event.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "contacts")
public class Contact {

    /** The user id from auth-service (not generated here). */
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(length = 15)
    private String phone;

    @Column(nullable = false, length = 20)
    private String role;
}
