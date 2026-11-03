package com.grocery.auth.service;

import com.grocery.auth.dto.response.AuthResponse;
import com.grocery.auth.entity.User;
import com.grocery.auth.enums.SessionEndReason;
import com.grocery.auth.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Turns a user who has proven who they are (password, social login) into a logged-in browser: a short-lived access
 * token for the JSON answer and a new login session whose refresh token goes into the cookie. One place for every way
 * in, so password and social login behave the same.
 */
@Component
@RequiredArgsConstructor
public class LoginSessions {

    private static final String TOKEN_TYPE = "Bearer";

    private final SessionService sessionService;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    /** Access token + a new session. A session this browser still had (its cookie) is replaced. */
    public AuthTokens start(User user, String currentRefreshToken) {
        sessionService.end(currentRefreshToken, SessionEndReason.SUPERSEDED);
        return new AuthTokens(authResponse(user), sessionService.start(user));
    }

    /** The JSON answer: a new access token, built from the current user row (role, store id). */
    public AuthResponse authResponse(User user) {
        return new AuthResponse(
                jwtService.generateToken(user),
                TOKEN_TYPE,
                jwtService.getExpirationSeconds(),
                userMapper.toResponse(user));
    }
}
