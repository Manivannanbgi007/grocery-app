package com.grocery.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Columns every table has: id, audit columns (when and who) and an optimistic-locking version.
 * Every entity extends this class.
 *
 * The audit columns are filled automatically by Spring Data auditing (see JpaAuditingConfig):
 * createdAt / updatedAt with the time, createdBy / updatedBy with who did it.
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Who added the row: the role and id of the logged-in user (for example "VENDOR:12"),
     * or "SYSTEM" (see UserContextHolder#currentAuditor). Empty for rows older than this column.
     */
    @CreatedBy
    @Column(name = "created_by", length = 50, updatable = false)
    private String createdBy;

    /** Who changed the row last, in the same form as createdBy. */
    @LastModifiedBy
    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    /**
     * Increased by Hibernate on every update. If two requests update the same row at the
     * same time, the second one fails instead of silently overwriting the first.
     */
    @Version
    @Column(nullable = false)
    private Long version;
}
