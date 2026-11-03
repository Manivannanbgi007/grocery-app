package com.grocery.mcpclient.context;

/**
 * Who is chatting, taken from the headers added by the API gateway.
 * A guest has no user id: the chat works without logging in.
 *
 * @param userId      logged-in user id   (X-User-Id), null for a guest
 * @param role        logged-in user role (X-User-Role), null for a guest
 * @param accessToken the user's token (from the Authorization header, already checked by the gateway). Only passed
 *                    on to mcp-server-service's tools for the user's own data, never to the model and never logged.
 */
public record UserContext(Long userId, String role, String accessToken) {

    public static final UserContext GUEST = new UserContext(null, null, null);

    /**
     * Which tools and rules this user gets. Without a complete login (id, role and token) it is a guest, also for an
     * unknown role.
     */
    public Audience audience() {
        if (userId == null || role == null || accessToken == null) {
            return Audience.GUEST;
        }
        return switch (role) {
            case "CUSTOMER" -> Audience.CUSTOMER;
            case "VENDOR" -> Audience.VENDOR;
            case "DELIVERY_PARTNER" -> Audience.DELIVERY_PARTNER;
            case "ADMIN", "SUPER_ADMIN" -> Audience.ADMIN;
            default -> Audience.GUEST;
        };
    }

    /** A logged-in user whose token the tools may use to read that user's own data. */
    public boolean isLoggedIn() {
        return audience() != Audience.GUEST;
    }

    /** Never print the token (records print all fields by default). */
    @Override
    public String toString() {
        return "UserContext[userId=" + userId + ", role=" + role + ", accessToken=" + (accessToken == null ? "none" : "***") + "]";
    }
}
