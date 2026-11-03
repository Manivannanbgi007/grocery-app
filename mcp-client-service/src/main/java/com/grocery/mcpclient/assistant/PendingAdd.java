package com.grocery.mcpclient.assistant;

import com.grocery.mcpclient.dto.response.ChatAction;
import com.grocery.mcpclient.tool.GroceryToolsClient;
import com.grocery.mcpclient.tool.ToolCallRecorder;
import com.grocery.mcpclient.tool.ToolCallRecorder.ToolCall;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An addition to the cart that waits for the customer's amount:
 *
 * <pre>
 * Customer:  Add tomoto in my cart?
 * Assistant: How much Tomato would you like to add? One pack is 1 kg for ₹36. Nothing is added yet.   <- pending: Tomato (id 1)
 * Customer:  3kg                                                                                     <- the code adds 3 kg of product 1
 * </pre>
 *
 * prepareMyCartAdd found the product and asked how much (its data names the product as "amountFor"). The conversation
 * keeps its id and name (Conversation.pendingProductId), so the customer's short answer is never mistaken for a new
 * question: ChatServiceImpl adds exactly this product, without asking the model.
 *
 * @param productId   the product the add tool found ("tomoto" -> Tomato, id 1)
 * @param productName its name in the catalog, for the answers ("Tomato")
 */
public record PendingAdd(long productId, String productName) {

    /**
     * The one product that prepareMyCartAdd asked the amount of in this question, and that is not on the card. Null
     * when there is none, or when the question was about several products ("add milk and eggs" without amounts): an
     * answer like "2" would not say which one, so the model handles it.
     */
    public static PendingAdd askedIn(ToolCallRecorder recorder) {
        ChatAction card = ChatActions.of(recorder);
        Map<Long, String> asked = new LinkedHashMap<>();
        for (ToolCall call : recorder.calls()) {
            JsonNode product = call.data().path("amountFor");
            if (GroceryToolsClient.ADD_TOOL.equals(call.toolName()) && call.asksHowMuch() && product.path("productId").canConvertToLong()) {
                asked.put(product.path("productId").asLong(), product.path("name").asString(null));
            }
        }
        if (card != null) {
            card.items().forEach(line -> asked.remove(line.productId()));
        }
        if (asked.size() != 1) {
            return null;
        }
        Map.Entry<Long, String> product = asked.entrySet().iterator().next();
        return product.getValue() == null ? null : new PendingAdd(product.getKey(), product.getValue());
    }
}
