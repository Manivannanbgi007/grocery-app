package com.grocery.mcpclient.assistant;

import com.grocery.mcpclient.dto.response.ChatAction;
import com.grocery.mcpclient.tool.ToolCallRecorder;
import com.grocery.mcpclient.tool.ToolCallRecorder.ToolCall;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The card with a Confirm button under an answer, made in code from this question's tool results (never from the
 * model's text, so a model that invents an amount, an address or an order number cannot put it on the card): the newest
 * OK result of prepareMyOrder becomes a PLACE_ORDER card, of prepareMyOrderCancellation a CANCEL_ORDER card, of
 * prepareMyCartAdd an ADD_TO_CART card (with every product prepared for adding in this question), of
 * prepareMyCartRemoval a REMOVE_FROM_CART card (the same, for taking out), of prepareMyCartQuantity a SET_CART_QUANTITY
 * card and of prepareMyCartClear a CLEAR_CART card. No such result, no card.
 *
 * This card is the confirmation of every change the chat offers: nothing is changed before the customer's click, which
 * runs in their own browser with their own login (the services check whose cart or order it is, and every rule, again).
 * A card can be confirmed for 10 minutes, once (the website), and its button does exactly what the card shows.
 */
public final class ChatActions {

    public static final String PREPARE_ORDER_TOOL = "prepareMyOrder";
    public static final String PREPARE_ADD_TOOL = "prepareMyCartAdd";
    static final String PREPARE_CANCELLATION_TOOL = "prepareMyOrderCancellation";
    public static final String PREPARE_REMOVAL_TOOL = "prepareMyCartRemoval";
    static final String PREPARE_QUANTITY_TOOL = "prepareMyCartQuantity";
    public static final String PREPARE_CLEAR_TOOL = "prepareMyCartClear";

    private static final JsonMapper JSON = JsonMapper.shared();

    private ChatActions() {
    }

    /** @return the card, or null when no tool prepared one in this question */
    public static ChatAction of(ToolCallRecorder recorder) {
        List<ToolCall> calls = recorder.calls();
        for (int i = calls.size() - 1; i >= 0; i--) {
            ToolCall call = calls.get(i);
            if (!"OK".equals(call.status())) {
                continue;
            }
            if (PREPARE_ORDER_TOOL.equals(call.toolName())) {
                return orderCard(dataOf(call.result()));
            }
            if (PREPARE_CANCELLATION_TOOL.equals(call.toolName())) {
                return cancellationCard(dataOf(call.result()));
            }
            if (PREPARE_ADD_TOOL.equals(call.toolName())) {
                return productsCard(ChatAction.ADD_TO_CART, PREPARE_ADD_TOOL, calls);
            }
            if (PREPARE_REMOVAL_TOOL.equals(call.toolName())) {
                return productsCard(ChatAction.REMOVE_FROM_CART, PREPARE_REMOVAL_TOOL, calls);
            }
            if (PREPARE_QUANTITY_TOOL.equals(call.toolName())) {
                return quantityCard(dataOf(call.result()));
            }
            if (PREPARE_CLEAR_TOOL.equals(call.toolName())) {
                return clearCard(dataOf(call.result()));
            }
        }
        return null;
    }

    private static ChatAction orderCard(JsonNode data) {
        JsonNode confirm = data.path("confirm");
        if (!confirm.path("addressId").canConvertToLong() || text(confirm, "paymentMethod") == null
                || text(confirm, "checkoutFingerprint") == null) {
            return null;        // without these the button could not place the order
        }
        return new ChatAction(ChatAction.PLACE_ORDER, text(data, "store"), lines(data), text(data, "itemsTotal"),
                text(data, "couponDiscount"), text(data, "deliveryFee"), text(data, "payable"), text(data, "deliverTo"),
                text(data, "payment"), text(data, "recentOrder"), confirm.path("addressId").asLong(),
                text(confirm, "paymentMethod"), text(confirm, "checkoutFingerprint"), null, null, null, null, null);
    }

    private static ChatAction cancellationCard(JsonNode data) {
        JsonNode confirm = data.path("confirm");
        if (!confirm.path("orderId").canConvertToLong()) {
            return null;        // without it the button could not cancel the order
        }
        return new ChatAction(ChatAction.CANCEL_ORDER, text(data, "store"), lines(data), null, null, null,
                text(data, "total"), null, null, null, null, null, null, confirm.path("orderId").asLong(),
                text(data, "status"), text(data, "refund"), text(confirm, "reason"), null);
    }

    /**
     * One card for every product prepared in this question by this tool ("Add Tomato 1 kg and Onion 2 kg", "Remove Tomato
     * and Potato": the tool once per product), in the order they were prepared, each product once. The button adds or
     * takes out each line by its productId (adding: up to the line's newQuantity). A result without its product, with
     * more than one line, or (adding) without the new total is left out: the button must know exactly what it does.
     */
    private static ChatAction productsCard(String kind, String tool, List<ToolCall> calls) {
        Map<Long, ChatAction.Line> lines = new LinkedHashMap<>();
        JsonNode first = null;
        for (ToolCall call : calls) {
            if (!tool.equals(call.toolName()) || !"OK".equals(call.status())) {
                continue;
            }
            JsonNode data = dataOf(call.result());
            JsonNode productId = data.path("confirm").path("productId");
            JsonNode newQuantity = data.path("confirm").path("newQuantity");
            JsonNode items = data.path("items");
            boolean adding = ChatAction.ADD_TO_CART.equals(kind);
            if (!productId.canConvertToLong() || items.size() != 1 || (adding && !newQuantity.canConvertToInt())) {
                continue;
            }
            // "Add Tomato and Organic Carrot" with an empty cart: each one alone fits, but a cart holds one store's
            // products, so the card keeps the store of the first one (AddReport says why the others are not on it)
            if (adding && first != null && !Objects.equals(text(data, "store"), text(first, "store"))) {
                continue;
            }
            JsonNode item = items.get(0);
            lines.putIfAbsent(productId.asLong(), new ChatAction.Line(text(item, "name"), text(item, "packSize"),
                    item.path("quantity").asInt(), text(item, "lineTotal"), productId.asLong(),
                    adding ? newQuantity.asInt() : null));
            first = first == null ? data : first;
        }
        return lines.isEmpty() ? null : cartCard(kind, first, List.copyOf(lines.values()), null, null);
    }

    private static ChatAction quantityCard(JsonNode data) {
        JsonNode confirm = data.path("confirm");
        JsonNode items = data.path("items");
        if (!confirm.path("productId").canConvertToLong() || !confirm.path("quantity").canConvertToInt() || items.size() != 1) {
            return null;        // the button must know the one product and its new quantity
        }
        JsonNode item = items.get(0);
        ChatAction.Line line = new ChatAction.Line(text(item, "name"), text(item, "packSize"), item.path("quantity").asInt(),
                text(item, "lineTotal"), confirm.path("productId").asLong());
        return cartCard(ChatAction.SET_CART_QUANTITY, data, List.of(line), null, confirm.path("quantity").asInt());
    }

    /** The whole cart (getMyCart's data): the button empties it, so it needs nothing more than the lines to show */
    private static ChatAction clearCard(JsonNode data) {
        List<ChatAction.Line> lines = lines(data);
        return lines.isEmpty() ? null : cartCard(ChatAction.CLEAR_CART, data, lines, text(data, "payable"), null);
    }

    /** A card about the cart: the store, the lines, and for CLEAR_CART what the cart costs now, for SET_CART_QUANTITY the new packs */
    private static ChatAction cartCard(String kind, JsonNode data, List<ChatAction.Line> lines, String payable, Integer newQuantity) {
        return new ChatAction(kind, text(data, "store"), lines, null, null, null, payable, null, null, null, null, null, null,
                null, null, null, null, newQuantity);
    }

    private static List<ChatAction.Line> lines(JsonNode data) {
        List<ChatAction.Line> lines = new ArrayList<>();
        data.path("items").forEach(item -> lines.add(new ChatAction.Line(text(item, "name"), text(item, "packSize"),
                item.path("quantity").asInt(), text(item, "lineTotal"))));
        return lines;
    }

    /** The "data" of a tool result (a missing node when the text is no JSON) */
    private static JsonNode dataOf(String result) {
        try {
            return JSON.readTree(result).path("data");
        } catch (JacksonException ex) {
            return JSON.missingNode();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() ? value.asString() : null;
    }
}
