package com.grocery.auth.service;

import com.grocery.auth.entity.User;

/**
 * Creates signed JWT access tokens. Validation happens in the API gateway.
 */
public interface JwtService {

    String generateToken(User user);

    long getExpirationSeconds();
}
