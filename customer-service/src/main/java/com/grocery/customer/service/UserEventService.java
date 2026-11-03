package com.grocery.customer.service;

import com.grocery.events.user.UserRegistered;

/**
 * Reacts to events published by auth-service.
 */
public interface UserEventService {

    /** Creates the customer profile when someone registers as a CUSTOMER. Other roles are ignored. */
    void handleUserRegistered(UserRegistered event);
}
