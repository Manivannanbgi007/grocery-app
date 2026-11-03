package com.grocery.mcpserver.config;

/**
 * Names of the short caches (1 minute, see application.yml). Only data that rarely changes is cached:
 * product searches always go to the gateway, because stock changes all the time.
 */
public final class CacheNames {

    public static final String CATEGORIES = "categories";
    public static final String STORES = "stores";
    public static final String SETTINGS = "platform-settings";
    public static final String COUPONS = "coupons";

    private CacheNames() {
    }
}
