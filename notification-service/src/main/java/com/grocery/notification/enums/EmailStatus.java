package com.grocery.notification.enums;

/**
 * What happened to the email copy of a notification.
 */
public enum EmailStatus {
    SENT,
    /** The mail server did not accept it (see the log); the in-app notification is still there. */
    FAILED,
    /** No email address known for the user. */
    NOT_SENT
}
