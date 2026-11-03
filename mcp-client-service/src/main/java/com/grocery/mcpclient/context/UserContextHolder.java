package com.grocery.mcpclient.context;

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

    /** The caller, or {@link UserContext#GUEST} when nobody is logged in. */
    public static UserContext get() {
        UserContext userContext = CURRENT.get();
        return userContext != null ? userContext : UserContext.GUEST;
    }

    /** The logged-in user's id, or null for a guest. */
    public static Long currentUserId() {
        return get().userId();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
