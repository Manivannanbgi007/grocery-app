package com.grocery.mcpclient.assistant;

import com.grocery.mcpclient.dto.response.ChatAction;
import com.grocery.mcpclient.tool.ChangeRequest;
import com.grocery.mcpclient.tool.GroceryToolsClient;
import com.grocery.mcpclient.tool.ToolCallRecorder;
import com.grocery.mcpclient.tool.ToolCallRecorder.ToolCall;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The answer about adding products to the cart, written here from the prepareMyCartAdd results, never from the model's
 * words:
 *
 * <pre>
 * Here are the products to add to your cart: 1 x Banana Robusta (1 dozen) for ₹60 and 2 x Tomato (1 kg) for ₹72 (that makes 3 in your cart). Please check them and press Confirm to add them; nothing is added before that.
 * The cart has products from Fresh Mart, and Organic Carrot is sold by Green Basket. A cart can hold the products of one store only: ...
 * Which milk do you mean: Amul Taaza Toned Milk (500 ml) or Akshayakalpa Organic Milk (1 L)? Nothing is added for it yet.
 * </pre>
 *
 * The products on the card come first, then the shop's own sentence for each product that could not be prepared (or
 * the question for it). ChatServiceImpl uses it when the code prepared the card itself (the model did not), when the
 * customer asked for two or more products (the model's answer cannot be trusted to say which of them are on the card),
 * and when the model's own answer failed the check. With one product that the model prepared and described well, its
 * answer stays.
 *
 * Nothing is added by this answer: the card's Confirm button adds the products.
 */
public final class AddReport {

    /** For "I want to buy X": the order summary comes after the click (prepareMyOrder reads the cart as it is) */
    static final String ORDER_AFTERWARDS = "After you confirm, ask me to place your order.";
    private static final JsonMapper JSON = JsonMapper.shared();

    private AddReport() {
    }

    /** Whether the answer is this report anyway: two or more prepareMyCartAdd calls, and no card of another kind */
    public static boolean needed(ToolCallRecorder recorder) {
        ChatAction card = ChatActions.of(recorder);
        boolean addCardOrNone = card == null || ChatAction.ADD_TO_CART.equals(card.kind());
        return addCardOrNone && addCalls(recorder).size() >= 2;
    }

    /** Whether prepareMyCartAdd was called for this question (by the model or the code) */
    public static boolean anyAddition(ToolCallRecorder recorder) {
        return !addCalls(recorder).isEmpty();
    }

    /**
     * The report: the products on the card, then one sentence for each product that is not on it. The same product
     * gets one sentence, also when it was asked for twice ("tomato" by the model and "tomoto" by the code: both found
     * Tomato).
     */
    public static String of(ToolCallRecorder recorder) {
        ChatAction card = ChatActions.of(recorder);
        boolean addCard = card != null && ChatAction.ADD_TO_CART.equals(card.kind());
        List<String> sentences = new ArrayList<>();
        if (addCard) {
            sentences.add(cardSentence(card.items()));
        }
        Set<Long> askedHowMuch = new HashSet<>();
        for (ToolCall call : addCalls(recorder)) {
            if ("OK".equals(call.status())) {
                // prepared, but left off the card: another store's product than the card's (ChatActions)
                if (addCard && !isOnTheCard(call, card)) {
                    sentences.add(otherStoreSentence(call, card));
                }
            } else if (!(addCard && isOnTheCard(call, card)) && !alreadyAsked(call, askedHowMuch)) {
                sentences.add(sentenceFor(call));
            }
        }
        if (addCard && ChangeRequest.orderAsked(recorder.customerMessage())) {
            sentences.add(ORDER_AFTERWARDS);
        }
        return String.join("\n", sentences);
    }

    /**
     * "Organic Carrot is sold by Green Basket, and the card's products by Fresh Mart. A cart holds the products of one
     * store only, so it is not on the card: please order it separately."
     */
    private static String otherStoreSentence(ToolCall call, ChatAction card) {
        JsonNode data = dataOf(call);
        String name = Objects.toString(text(data.path("items").path(0), "name"), productOf(call));
        return name + " is sold by " + Objects.toString(text(data, "store"), "another store") + ", and the card's products by "
                + Objects.toString(card.store(), "another store") + ". A cart holds the products of one store only, so it is "
                + "not on the card: please order it separately.";
    }

    private static List<ToolCall> addCalls(ToolCallRecorder recorder) {
        return recorder.calls().stream().filter(call -> GroceryToolsClient.ADD_TOOL.equals(call.toolName())).toList();
    }

    private static String cardSentence(List<ChatAction.Line> lines) {
        List<String> labels = lines.stream().map(AddReport::label).toList();
        if (labels.size() == 1) {
            return "Here is the product to add to your cart: " + labels.getFirst()
                    + ". Please check it and press Confirm to add it; nothing is added before that.";
        }
        return "Here are the products to add to your cart: " + String.join(", ", labels.subList(0, labels.size() - 1))
                + " and " + labels.getLast() + ". Please check them and press Confirm to add them; nothing is added before that.";
    }

    /** "1 x Banana Robusta (1 dozen) for ₹60", "1 x Tomato (1 kg) for ₹36 (that makes 4 in your cart)" */
    private static String label(ChatAction.Line line) {
        return line.quantity() + " x " + line.name() + (line.packSize() == null ? "" : " (" + line.packSize() + ")")
                + (line.lineTotal() == null ? "" : " for " + line.lineTotal())
                + (line.newQuantity() != null && line.newQuantity() > line.quantity()
                        ? " (that makes " + line.newQuantity() + " in your cart)" : "");
    }

    /**
     * What happened to one product that is not on the card, in words for the customer: the shop's own reason ("Only 3
     * packs of Amul Butter (100 g) are left.", another store, "The shop has no product called ..."), or the question for it
     */
    private static String sentenceFor(ToolCall call) {
        String product = productOf(call);
        if (call.failed() || "SERVICE_UNAVAILABLE".equals(call.status())) {
            return "I could not check " + product + " just now. Please ask me again in a few minutes.";
        }
        return switch (String.valueOf(call.status())) {
            case "REFUSED", "NOT_FOUND", "LOGIN_REQUIRED" -> call.message();
            case "CHOOSE" -> question(call, product);
            default -> "Please tell me the name and the amount of " + product + " again.";
        };
    }

    /**
     * The question for this product, nothing added for it yet:
     * - the customer said no amount, and the add tool found the product: "How much Tomato would you like to add? One
     *   pack is 1 kg for ₹36. Nothing is added yet." (the catalog's name, also for "tomoto");
     * - several products are called that: "Which milk do you mean: Amul Taaza Toned Milk (500 ml) or ...?";
     * - the amount is not in the customer's words ("2k tomatoes"): "How much tomatoes do you want ...?";
     * - otherwise PackCount's question, which is written for the customer already ("1.5 kg is not a whole number of
     *   packs of Tomato (1 kg a pack). 1 kg (1 pack) or 2 kg (2 packs)?").
     */
    private static String question(ToolCall call, String product) {
        JsonNode amountFor = dataOf(call).path("amountFor");
        if (amountFor.isObject()) {
            return howMuch(text(amountFor, "name"), text(amountFor, "packSize"), text(amountFor, "price"));
        }
        List<String> options = new ArrayList<>();
        dataOf(call).path("options").forEach(option -> {
            String name = text(option, "name");
            String packSize = text(option, "packSize");
            if (name != null) {
                options.add(packSize == null ? name : name + " (" + packSize + ")");
            }
        });
        if (options.size() == 1) {
            return "Do you mean " + options.getFirst() + " for " + product + "? Nothing is added for it yet.";
        }
        if (options.size() > 1) {
            return "Which " + product + " do you mean: " + String.join(", ", options.subList(0, options.size() - 1))
                    + " or " + options.getLast() + "? Nothing is added for it yet.";
        }
        if (ChangeRequest.isUnsaidAmountQuestion(call.message())) {
            return "How much " + product + " do you want (for example 2 kg or 2 packs)? Nothing is added for it yet.";
        }
        return Objects.toString(call.message(), "How much " + product + " do you want?");
    }

    /** "How much Tomato would you like to add? One pack is 1 kg for ₹36. Nothing is added yet." */
    public static String howMuch(String name, String packSize, String price) {
        String pack = packSize == null ? "" : " One pack is " + packSize + (price == null ? "." : " for " + price + ".");
        return "How much " + name + " would you like to add?" + pack + " Nothing is added yet.";
    }

    /**
     * A call that failed for words that a product on the card covers: the model asked for "tomato and potato"
     * (NOT_FOUND), then Tomato and Potato were prepared one by one. A result that names its product ("tomoto" found
     * Tomato) is compared by the product's id.
     */
    private static boolean isOnTheCard(ToolCall call, ChatAction card) {
        Long productId = productIdOf(call);
        if (productId != null) {
            return card.items().stream().anyMatch(line -> productId.equals(line.productId()));
        }
        return card.items().stream().anyMatch(line -> ChangeRequest.sameProduct(call.argument("product"), line.name()));
    }

    /** A second "how much?" for a product that was asked about already (remembers the first) */
    private static boolean alreadyAsked(ToolCall call, Set<Long> askedHowMuch) {
        Long productId = productIdOf(call);
        return call.asksHowMuch() && productId != null && !askedHowMuch.add(productId);
    }

    /** The product a result is about: the line prepared for the card (OK), or the product asked about (how much) */
    private static Long productIdOf(ToolCall call) {
        JsonNode data = dataOf(call);
        JsonNode productId = data.path("confirm").path("productId");
        if (!productId.canConvertToLong()) {
            productId = data.path("amountFor").path("productId");
        }
        return productId.canConvertToLong() ? productId.asLong() : null;
    }

    /** The product as the customer named it (the tool call's "product", without an amount the model put in), e.g. "Organic Carrot" */
    private static String productOf(ToolCall call) {
        String product = ChangeRequest.withoutAmount(call.argument("product"));
        return product == null || product.isBlank() ? "the product" : product;
    }

    private static JsonNode dataOf(ToolCall call) {
        if (call.failed()) {
            return JSON.missingNode();
        }
        try {
            return JSON.readTree(call.result()).path("data");
        } catch (JacksonException ex) {
            return JSON.missingNode();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() ? value.asString() : null;
    }
}
