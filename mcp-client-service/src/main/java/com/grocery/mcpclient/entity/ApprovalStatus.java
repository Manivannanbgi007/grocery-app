package com.grocery.mcpclient.entity;

/**
 * Where a card of the chat is in the customer's human approval (ChatApproval.status):
 *
 * <pre>
 * PENDING  ── Confirm ──────────► APPROVED   (the website placed the order, cancelled it or changed the cart)
 *    └──── Cancel / Keep it ────► REJECTED   (nothing was done)
 * </pre>
 *
 * A decision is final.
 */
public enum ApprovalStatus {

    /** The card is shown and waits for the customer's click */
    PENDING,

    /** The customer pressed Confirm */
    APPROVED,

    /** The customer pressed Cancel or Keep it */
    REJECTED
}
