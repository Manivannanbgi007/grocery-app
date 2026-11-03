package com.grocery.auth.repository;

import com.grocery.auth.entity.SocialLogin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SocialLoginRepository extends JpaRepository<SocialLogin, Long> {

    /** The user of a provider account (provider + the provider's id of the person). */
    Optional<SocialLogin> findByProviderAndProviderUserId(String provider, String providerUserId);

    /** Does the user already have an account of this provider linked (one per provider)? */
    boolean existsByUserIdAndProvider(Long userId, String provider);
}
