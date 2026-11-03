package com.grocery.order.exception;

/**
 * The invoice PDF could not be generated (an unexpected error in the PDF library). Nothing was stored, so the next
 * request simply tries again. Returned as HTTP 503.
 */
public class InvoiceUnavailableException extends RuntimeException {

    public InvoiceUnavailableException(Throwable cause) {
        super("The invoice could not be created right now. Please try again in a moment.", cause);
    }
}
