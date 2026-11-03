package com.grocery.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // for JPA only
@Entity
@Table(name = "partner_facts")
public class PartnerFact {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "kyc_status", nullable = false, length = 20)
    private String kycStatus;

    public PartnerFact(Long userId, String fullName, String kycStatus) {
        this.userId = userId;
        this.fullName = fullName;
        this.kycStatus = kycStatus;
    }
}
