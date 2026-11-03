package com.grocery.mcpclient.context;

/**
 * Who is chatting, as far as the assistant is concerned: it decides which tools the model gets (GroceryToolsClient)
 * and which rules it follows (SystemPrompt). The super admin counts as an admin.
 */
public enum Audience {

    /** not logged in: only the shop's tools */
    GUEST,
    /** the shop's tools plus the customer's own cart and orders */
    CUSTOMER,
    /** the shop's tools plus the vendor's own store, orders, stock and sales */
    VENDOR,
    /** the shop's tools plus the partner's own status, deliveries and earnings */
    DELIVERY_PARTNER,
    /** the shop's tools plus the platform's numbers, waiting work, orders and sales */
    ADMIN;

    /** The panels (vendor, delivery partner, admin) get the panel rules; guests and customers the shop rules. */
    public boolean isPanel() {
        return this == VENDOR || this == DELIVERY_PARTNER || this == ADMIN;
    }
}
