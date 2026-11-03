package com.grocery.order.service;

import com.grocery.order.dto.response.InvoiceFile;
import com.grocery.order.entity.Order;

import java.util.function.Supplier;

/**
 * The invoice PDF of an order, for the customer, vendor and admin endpoints.
 *
 * Once the order is paid, the PDF is generated on the first request, stored as a file and from then on read from
 * that file. Before that (cash on delivery not collected yet) it is generated for every request and not stored.
 */
public interface InvoiceService {

    /**
     * @param findOrderWithAccessCheck loads the order and checks that the caller may see it (404 otherwise), for
     *                                 example {@code () -> findMyOrder(customerUserId, orderId)}
     * @throws com.grocery.order.exception.BusinessException           400: the order has no invoice (yet)
     * @throws com.grocery.order.exception.InvoiceUnavailableException 503: the PDF could not be generated; try again
     */
    InvoiceFile getInvoice(Supplier<Order> findOrderWithAccessCheck);
}
