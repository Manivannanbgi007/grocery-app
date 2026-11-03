package com.grocery.platform.context;

/**
 * Who is calling the current request, taken from the headers added by the API gateway.
 * Any value can be null (for example on public endpoints such as login).
 *
 * @param userId  logged-in user id      (X-User-Id)
 * @param role    logged-in user role    (X-User-Role)
 * @param storeId vendor's store id      (X-Store-Id, vendors only)
 */
public record UserContext(Long userId, String role, Long storeId) {
}
