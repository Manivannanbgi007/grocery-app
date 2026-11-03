package com.grocery.auth.validator;

import com.grocery.auth.exception.DuplicateResourceException;
import com.grocery.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Business validations that need the database, shared by every flow that creates users.
 * (Simple field checks such as @NotBlank / @Email live on the request DTOs.)
 */
@Component
@RequiredArgsConstructor
public class UserValidator {

    private final UserRepository userRepository;

    public void validateEmailAndPhoneAreAvailable(String email, String phone) {
        if (userRepository.existsByEmail(email.trim())) {
            throw new DuplicateResourceException("Email is already registered: " + email);
        }
        if (userRepository.existsByPhone(phone)) {
            throw new DuplicateResourceException("Phone number is already registered: " + phone);
        }
    }
}
