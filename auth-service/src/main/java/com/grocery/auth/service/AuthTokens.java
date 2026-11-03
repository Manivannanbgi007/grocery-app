package com.grocery.auth.service;

import com.grocery.auth.dto.response.AuthResponse;

/**
 * What login, register and refresh produce: the JSON answer (with the access token) and the refresh token, which
 * AuthController puts into the HttpOnly cookie - never into the JSON.
 *
 * @param refreshToken null: keep the cookie as it is (a second tab refreshed at the same moment)
 */
public record AuthTokens(AuthResponse response, SessionService.IssuedToken refreshToken) {
}
