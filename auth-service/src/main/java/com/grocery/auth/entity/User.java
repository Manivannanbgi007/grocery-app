package com.grocery.auth.entity;

import com.grocery.auth.enums.Role;
import com.grocery.auth.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A person who can log in: customer, vendor, delivery partner, admin or super admin.
 * Profile details (addresses, store details, KYC) live in the service that owns them.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    /**
     * When the email was proven to be the account holder's (a completed password reset, or a provider that verified
     * it); null = never proven. Social login links to this account automatically only when it is set.
     */
    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(nullable = false, unique = true, length = 15)
    private String phone;

    /** BCrypt hash - the plain password is never stored. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    /** Store owned by this user. Only set for VENDOR users, once store-service approves their store. */
    @Column(name = "store_id")
    private Long storeId;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    // ----- business rules -----

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public void recordLogin(Instant loginTime) {
        this.lastLoginAt = loginTime;
    }

    /** The email address was proven to be the account holder's (the first time is kept). */
    public void markEmailVerified(Instant now) {
        if (emailVerifiedAt == null) {
            this.emailVerifiedAt = now;
        }
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    /** Admins and the super admin keep password login only: no social login for them. */
    public boolean isAdmin() {
        return role == Role.ADMIN || role == Role.SUPER_ADMIN;
    }

    /** Called when store-service approves this vendor's store. */
    public void assignStore(Long approvedStoreId) {
        this.storeId = approvedStoreId;
    }
}
