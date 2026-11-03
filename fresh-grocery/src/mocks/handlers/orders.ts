import { http, HttpResponse } from 'msw'
import type { DeliveryRatingRequest } from '../../api/deliveryRatings'
import type { PlaceOrderRequest } from '../../api/orders'
import type { OrderStatus } from '../../api/types'
import { mockInvoiceFileName, mockInvoicePdf } from '../mockInvoice'
import { cancelMyOrder, getOrder, getPayment, listOrders, orderWithInvoice, payWithMockGateway, placeOrder, withPaymentUrl } from '../mockOrders'
import { getDeliveryRating, rateDelivery } from '../mockRatings'
import { asCustomer } from './withRole'
import { intParam, paginate } from './respond'

/*
 * Mock of the customer endpoints of order-service, payment-service and delivery-service (delivery ratings).
 * The rules are in mockOrders.ts and mockRatings.ts.
 */

export const orderHandlers = [
  http.post(
    '*/api/v1/orders',
    asCustomer(async (customer, request) => {
      const body = (await request.json()) as Partial<PlaceOrderRequest>
      const idempotencyKey = request.headers.get('Idempotency-Key') ?? undefined
      return {
        data: withPaymentUrl(placeOrder(customer.id, body, idempotencyKey)),
        message: 'Order placed. We are reserving your items and confirming the payment.',
        status: 201,
      }
    }),
  ),

  http.get(
    '*/api/v1/orders',
    asCustomer(async (customer, request) => {
      const params = new URL(request.url).searchParams
      const status = (params.get('status') as OrderStatus | null) ?? null
      const page = paginate(listOrders(customer.id, status), intParam(params, 'page', 0), intParam(params, 'size', 20))
      return { data: page, message: 'Orders fetched successfully' }
    }),
  ),

  http.get(
    '*/api/v1/orders/:orderId',
    asCustomer(async (customer, _request, params) => ({ data: withPaymentUrl(getOrder(customer.id, Number(params.orderId))), message: 'Order fetched successfully' })),
  ),

  http.get(
    '*/api/v1/orders/:orderId/invoice',
    asCustomer(async (customer, _request, params) => {
      const order = orderWithInvoice(customer.id, Number(params.orderId))
      return new HttpResponse(mockInvoicePdf(order), {
        headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': `attachment; filename="${mockInvoiceFileName(order)}"` },
      })
    }),
  ),

  http.post(
    '*/api/v1/orders/:orderId/cancel',
    asCustomer(async (customer, request, params) => {
      const text = await request.text()
      const body = (text ? JSON.parse(text) : {}) as { reason?: string | null }
      return { data: withPaymentUrl(cancelMyOrder(customer.id, Number(params.orderId), body.reason)), message: 'Order cancelled' }
    }),
  ),

  http.get(
    '*/api/v1/payments/orders/:orderId',
    asCustomer(async (customer, _request, params) => ({ data: getPayment(customer.id, Number(params.orderId)), message: 'Payment fetched successfully' })),
  ),

  http.post(
    '*/api/v1/payments/orders/:orderId/mock-pay',
    asCustomer(async (customer, request, params) => {
      const { success } = (await request.json()) as { success?: boolean }
      const payment = payWithMockGateway(customer.id, Number(params.orderId), success === true)
      return { data: payment, message: success ? 'Payment successful' : 'Payment failed' }
    }),
  ),

  http.get(
    '*/api/v1/delivery-ratings/orders/:orderId',
    asCustomer(async (customer, _request, params) => ({ data: getDeliveryRating(customer.id, Number(params.orderId)), message: 'Rating fetched successfully' })),
  ),

  http.post(
    '*/api/v1/delivery-ratings',
    asCustomer(async (customer, request) => {
      const body = (await request.json()) as Partial<DeliveryRatingRequest>
      return { data: rateDelivery(customer.id, body), message: 'Thank you for rating the delivery', status: 201 }
    }),
  ),
]
