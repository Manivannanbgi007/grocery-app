package com.grocery.mcpserver.tool;

import com.grocery.mcpserver.client.CustomerClient;
import com.grocery.mcpserver.client.ShopInfoClient;
import com.grocery.mcpserver.client.dto.AddressData;
import com.grocery.mcpserver.client.dto.CartData;
import com.grocery.mcpserver.client.dto.CheckoutData;
import com.grocery.mcpserver.client.dto.OrderData;
import com.grocery.mcpserver.client.dto.OrderSummaryData;
import com.grocery.mcpserver.exception.ExternalServiceException;
import com.grocery.mcpserver.tool.result.CancellationPreview;
import com.grocery.mcpserver.tool.result.CartSummary;
import com.grocery.mcpserver.tool.result.OrderPreview;
import com.grocery.mcpserver.tool.result.UnpaidOrder;
import io.modelcontextprotocol.common.McpTransportContext;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The tools that prepare an order of the logged-in customer's cart, or the cancellation of one of their orders. They
 * place and cancel NOTHING.
 *
 * Placing an order is the customer's decision: prepareMyOrder only reads (the cart checked against today's catalog, the
 * customer's addresses, their newest order) and returns the summary. mcp-client-service shows that summary under the
 * answer with a Confirm button, built in code from this result; when the customer clicks it, the browser places the
 * order with the customer's own login (POST /api/v1/orders, with an Idempotency-Key and the summary's checkout
 * fingerprint). The same for a cancellation: prepareMyOrderCancellation reads the customer's own order (order-service
 * answers 404 for anybody else's), and only the click calls POST /api/v1/orders/{id}/cancel, where order-service checks
 * the owner and the status again. No tool of this server places, pays or cancels an order.
 *
 * The names start with "prepareMy": mcp-client-service offers them to logged-in customers only.
 */
@Component
@RequiredArgsConstructor
public class OrderPrepareTools {

    /** An order placed this recently is pointed out: the customer may be about to order the same again */
    static final Duration RECENT_ORDER = Duration.ofMinutes(5);
    /** "Cancel my order" without a number: these newest orders are looked at */
    static final int CANCEL_CANDIDATES = 5;
    /** order-service takes a reason of at most 255 characters */
    private static final int MAX_REASON_LENGTH = 250;
    private static final String NOT_LOGGED_IN = "The customer is not logged in. Ask them to log in on the website to order.";

    private final CustomerClient customers;
    private final ShopInfoClient shopInfo;
    private final ToolRunner runner;
    private final Clock clock;

    @McpTool(name = "prepareMyOrder",
            description = """
                    Prepare an order of the logged-in customer's cart when they want to order or pay for what is in it \
                    ("place my order", "checkout", "proceed to pay", "pay online", "buy everything in my cart", "cash on \
                    delivery"). It places NOTHING: it returns the summary (products, amounts, delivery address, \
                    payment), and the website shows it under your answer with a Confirm button. Only the customer's \
                    click on Confirm places the order; an online order is then paid with the Pay Now button. A way to \
                    pay ("online", "UPI", "cash on delivery") is never a product: pass it as paymentMethod.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ToolResult<Object> prepareMyOrder(
            @McpToolParam(description = "How the customer wants to pay, as they said it: \"cash\" (cash on delivery) or "
                    + "\"online\" (UPI or card). Leave it out if they did not say.", required = false) String paymentMethod,
            @McpToolParam(description = "The delivery address the customer named, e.g. \"home\" or \"work\". Leave it out "
                    + "for their default address.", required = false) String address,
            McpTransportContext context) {
        return runner.run("prepareMyOrder", () -> {
            String token = LoginToken.of(context, NOT_LOGGED_IN);
            CheckoutData checkout = customers.checkout(token);
            // "Proceed to pay" again after the order was placed: that order waits for its payment, nothing is ordered twice
            Optional<UnpaidOrder> unpaid = unpaidOnlineOrderOf(token, checkout);
            if (unpaid.isPresent()) {
                return new ToolResult<>(ToolResult.Status.REFUSED, "Nothing new to order: order #" + unpaid.get().orderNumber()
                        + " (" + unpaid.get().payable() + ") is placed and waits for the customer's online payment: they pay "
                        + "it with the Pay Now button.", unpaid.get());
            }
            if (!checkout.readyForCheckout()) {
                return ToolResult.refused("The cart cannot be ordered yet: " + checkout.problems().stream()
                        .map(Formats::withRupees).collect(Collectors.joining("; ")) + ".");
            }

            List<AddressData> addresses = customers.addresses(token);
            if (addresses.isEmpty()) {
                return ToolResult.notFound("The customer has no delivery address yet. They can add one on the Addresses "
                        + "page of the website.");
            }
            String namedAddress = ToolInputs.clean(address);
            List<AddressData> deliverTo = addressesNamed(namedAddress, addresses);
            if (deliverTo.size() != 1) {
                String all = addresses.stream().map(OrderPrepareTools::addressText).collect(Collectors.joining("; "));
                return deliverTo.isEmpty()
                        ? ToolResult.notFound("The customer has no address that matches \"" + namedAddress + "\". Their addresses: " + all + ".")
                        : ToolResult.choose("Several addresses match \"" + namedAddress + "\": " + all + ". Ask the customer which one.", null);
            }

            String method = paymentMethodOf(paymentMethod);
            if (method == null) {
                return ToolResult.choose(ToolInputs.clean(paymentMethod) == null
                        ? "Ask the customer how they want to pay: cash on delivery, or online (UPI or card) right after confirming."
                        : "Payment can be cash on delivery or online (UPI or card). Ask the customer which one.", null);
            }

            OrderPreview preview = preview(checkout, deliverTo.getFirst(), method, recentOrder(token));
            return ToolResult.ok("Order summary prepared; NOTHING is ordered yet. The website shows it under your answer "
                    + "with a Confirm button: tell the customer to check it and press Confirm to place the order.", preview);
        });
    }

    // No example order number in these texts: the small model copied such examples into its answers as if they were real
    @McpTool(name = "prepareMyOrderCancellation",
            description = """
                    Prepare the cancellation of one of the logged-in customer's own orders. Call it for EVERY request to \
                    cancel an order ("cancel my order", "cancel my last order", "cancel that order"), also right after an \
                    order summary: the customer may have pressed Confirm since your last answer, and your earlier messages \
                    do not show that. Without a number it finds the order itself. It cancels NOTHING: it returns the \
                    order's summary, and the website shows it under your answer with a Confirm button. Only the \
                    customer's click cancels the order.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ToolResult<CancellationPreview> prepareMyOrderCancellation(
            @McpToolParam(description = "The order number the customer said (digits only). Leave it out when they did not "
                    + "say which order.", required = false) Long orderNumber,
            @McpToolParam(description = "Why the customer cancels, in their words, if they said it", required = false) String reason,
            McpTransportContext context) {
        return runner.run("prepareMyOrderCancellation", () -> {
            String token = LoginToken.of(context, NOT_LOGGED_IN);
            if (orderNumber != null && orderNumber <= 0) {
                return ToolResult.invalidInput("orderNumber must be the number of one of the customer's orders, or left out.");
            }
            OrderData order;
            if (orderNumber == null) {
                // Which order? The customer's newest ones that order-service still lets them cancel
                List<OrderData> cancellable = customers.latestOrders(token, CANCEL_CANDIDATES).content().stream()
                        .map(summary -> customers.order(token, summary.id()))
                        .flatMap(Optional::stream)
                        .filter(OrderData::cancellable)
                        .toList();
                if (cancellable.isEmpty()) {
                    return ToolResult.notFound("The customer has no order that can still be cancelled.");
                }
                if (cancellable.size() > 1) {
                    return ToolResult.choose("Several orders can still be cancelled: " + cancellable.stream()
                            .map(candidate -> "#" + candidate.id() + " (" + Formats.rupees(candidate.amounts().totalAmount()) + ")")
                            .collect(Collectors.joining(", ")) + ". Ask the customer which one.", null);
                }
                order = cancellable.getFirst();
            } else {
                Optional<OrderData> found = customers.order(token, orderNumber);
                if (found.isEmpty()) {
                    return ToolResult.notFound("The customer has no order with the number " + orderNumber + ".");
                }
                order = found.get();
            }
            if (!order.cancellable()) {
                // "was cancelled earlier": the past, so the chat's check for false "is cancelled" claims lets it through
                return ToolResult.refused("CANCELLED".equals(order.status())
                        ? "Order #" + order.id() + " was cancelled earlier, so there is nothing to cancel."
                        : "Order #" + order.id() + " can no longer be cancelled: " + statusWord(order) + ".");
            }
            String why = ToolInputs.clean(reason);
            String cleanReason = why == null ? null : why.substring(0, Math.min(why.length(), MAX_REASON_LENGTH));
            // "at once": the model told a customer "The store will confirm the cancellation", which is not so
            return ToolResult.ok("Cancellation summary prepared; NOTHING is cancelled yet. The website shows it under your "
                    + "answer with a Confirm button: tell the customer to check it and press Confirm to cancel the order. "
                    + "Confirm cancels it at once.", CancellationPreview.from(order, cleanReason));
        });
    }

    /** "it is out for delivery" for a REFUSED message */
    private static String statusWord(OrderData order) {
        return switch (order.status()) {
            case "OUT_FOR_DELIVERY" -> "it is on the way";
            case "DELIVERED" -> "it is delivered";
            case "DELIVERY_FAILED" -> "its delivery failed";
            case "REJECTED" -> "the store rejected it";
            default -> "it is " + order.status().toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }

    private OrderPreview preview(CheckoutData checkout, AddressData address, String paymentMethod, String recentOrder) {
        CartData.Totals totals = checkout.totals();
        String storeName = checkout.storeId() == null ? null : StoreNames.of(shopInfo).get(checkout.storeId());
        return new OrderPreview(
                storeName,
                checkout.items().stream().map(CartSummary::line).toList(),
                Formats.rupees(totals.itemsTotal()),
                checkout.couponCode(),
                isZero(totals.couponDiscount()) ? null : Formats.rupees(totals.couponDiscount()),
                isZero(totals.deliveryFee()) ? "Free" : Formats.rupees(totals.deliveryFee()),
                Formats.rupees(totals.payableAmount()),
                addressText(address),
                "COD".equals(paymentMethod) ? "Cash on delivery" : "Online (UPI or card), right after confirming",
                recentOrder,
                new OrderPreview.Confirm(address.id(), paymentMethod, checkout.checkoutFingerprint()));
    }

    /** "cash", "COD", "cash on delivery" -> COD; "online", "UPI", "card" -> ONLINE; nothing or anything else -> null */
    static String paymentMethodOf(String said) {
        String words = ToolInputs.clean(said) == null ? "" : said.toLowerCase(Locale.ROOT);
        if (words.contains("cash") || words.matches(".*\\bcod\\b.*")) {
            return "COD";
        }
        if (words.contains("online") || words.contains("upi") || words.contains("card") || words.contains("net banking")
                || words.contains("netbanking") || words.contains("prepaid")) {
            return "ONLINE";
        }
        return null;
    }

    /** The address the customer named ("home", "work address", or words of it such as "indiranagar"); else the default one */
    private static List<AddressData> addressesNamed(String named, List<AddressData> addresses) {
        if (named == null) {
            return List.of(addresses.stream().filter(AddressData::defaultAddress).findFirst().orElse(addresses.getFirst()));
        }
        String words = named.toLowerCase(Locale.ROOT).replaceAll("\\s*address$", "").strip();
        String label = switch (words) {
            case "home", "house" -> "HOME";
            case "work", "office" -> "WORK";
            case "other" -> "OTHER";
            default -> null;
        };
        if (label != null) {
            return addresses.stream().filter(candidate -> label.equals(candidate.label())).toList();
        }
        return addresses.stream().filter(candidate -> addressText(candidate).toLowerCase(Locale.ROOT).contains(words)).toList();
    }

    /** "Home: Priya Sharma, 12, 5th Cross, Indiranagar, Bengaluru 560038" */
    static String addressText(AddressData address) {
        String label = address.label() == null ? "Address" : address.label().charAt(0) + address.label().substring(1).toLowerCase(Locale.ROOT);
        String lines = Stream.of(address.contactName(), address.addressLine1(), address.addressLine2(),
                        address.city() + " " + address.pincode())
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(", "));
        return label + ": " + lines;
    }

    /**
     * The customer's newest order, when it is an online order that still waits for its payment (with order-service's own
     * payment address) and the cart is that order: empty, or still holding just its products (order-service empties
     * the cart a moment after the order, in the background; live, "Proceed to pay" right after Confirm got a second
     * summary of the same products). Empty otherwise (a new cart is a new order), also when order-service cannot be
     * reached: then the summary or the cart's own reason is given.
     */
    private Optional<UnpaidOrder> unpaidOnlineOrderOf(String token, CheckoutData checkout) {
        try {
            return customers.latestOrders(token, 1).content().stream()
                    .findFirst()
                    .flatMap(summary -> customers.order(token, summary.id()))
                    .filter(order -> "ONLINE".equals(order.paymentMethod()) && "PENDING".equals(order.paymentStatus())
                            && order.paymentUrl() != null)
                    .filter(order -> checkout.items().isEmpty() || sameProducts(order, checkout))
                    .map(order -> new UnpaidOrder(order.id(), Formats.rupees(order.amounts().totalAmount()), order.paymentUrl()));
        } catch (ExternalServiceException ex) {
            return Optional.empty();
        }
    }

    /** Whether the cart holds just the order's products, as many packs of each */
    private static boolean sameProducts(OrderData order, CheckoutData checkout) {
        Map<String, Integer> ordered = order.items().stream()
                .collect(Collectors.toMap(OrderData.Item::productName, OrderData.Item::quantity, Integer::sum));
        Map<String, Integer> inCart = checkout.items().stream()
                .collect(Collectors.toMap(CartData.Item::productName, CartData.Item::quantity, Integer::sum));
        return ordered.equals(inCart);
    }

    /** "Order #196 was placed 3 minutes ago." when the newest order is that recent; only a hint, so no error stops the summary */
    private String recentOrder(String token) {
        try {
            return customers.latestOrders(token, 1).content().stream()
                    .findFirst()
                    .filter(order -> order.placedAt() != null && order.placedAt().isAfter(clock.instant().minus(RECENT_ORDER)))
                    .map(this::placedAgo)
                    .orElse(null);
        } catch (ExternalServiceException ex) {
            return null;
        }
    }

    private String placedAgo(OrderSummaryData order) {
        long minutes = Duration.between(order.placedAt(), clock.instant()).toMinutes();
        String ago = minutes < 1 ? "less than a minute ago" : minutes == 1 ? "1 minute ago" : minutes + " minutes ago";
        return "Order #" + order.id() + " was placed " + ago + ".";
    }

    private static boolean isZero(BigDecimal amount) {
        return amount == null || amount.signum() == 0;
    }
}
