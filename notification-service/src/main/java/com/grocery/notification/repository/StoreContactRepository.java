package com.grocery.notification.repository;

import com.grocery.notification.entity.StoreContact;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreContactRepository extends JpaRepository<StoreContact, Long> {
}
