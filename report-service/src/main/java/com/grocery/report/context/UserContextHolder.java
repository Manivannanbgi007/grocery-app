package com.grocery.report.context;

import com.grocery.report.exception.ForbiddenException;
import com.grocery.report.exception.UnauthorizedException;

import java.util.Optional;

/**
 * Keeps the {@link UserContext} of the current request so any class can read it.
 *
 * Filled by UserContextFilter at the start of each request and cleared at the end.
 * Each request runs on its own thread, so a ThreadLocal keeps users apart.
 */
public final class UserContextHolder {

    private static final ThreadLocal<UserContext> CURRENT = new ThreadLocal<>();

    private UserContextHolder() {
    }

    public static void set(UserContext userContext) {
        CURRENT.set(userContext);
    }

    public static Optional<UserContext> get() {
        return Optional.ofNullable(CURRENT.get());
    }

    /**
     * Returns the logged-in user's id, or fails with 401 when the X-User-Id header is missing.
     */
    public static Long getRequiredUserId() {
        return get()
                .map(UserContext::userId)
                .orElseThrow(() -> new UnauthorizedException(
                        "Missing or invalid X-User-Id header. Call this endpoint through the API gateway with a valid token."));
    }

    /**
     * Returns the logged-in vendor's store id (X-Store-Id), or fails with 403.
     * The JWT only contains a storeId once the vendor's store has been approved.
     */
    public static Long getRequiredStoreId() {
        return get()
                .map(UserContext::storeId)
                .orElseThrow(() -> new ForbiddenException(
                        "No store found for this vendor. Register a store and wait for approval, then log in again."));
    }

    public static void clear() {
        CURRENT.remove();
    }
}
