package com.grocery.customer.dto.response;

import com.grocery.customer.enums.Gender;

import java.time.Instant;
import java.time.LocalDate;

public record CustomerResponse(
        Long id,
        Long userId,
        String fullName,
        String email,
        String phone,
        LocalDate dateOfBirth,
        Gender gender,
        int addressCount,
        Instant createdAt) {
}
