package com.grocery.order.enums;

public enum PaymentMethod {
    /** Cash on delivery: the delivery partner collects the money. */
    COD,
    /** Paid online (mock gateway or Razorpay) before the store gets the order. */
    ONLINE
}
