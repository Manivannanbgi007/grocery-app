package com.grocery.report.repository;

import com.grocery.report.entity.PartnerFact;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartnerFactRepository extends JpaRepository<PartnerFact, Long> {

    long countByKycStatus(String kycStatus);
}
