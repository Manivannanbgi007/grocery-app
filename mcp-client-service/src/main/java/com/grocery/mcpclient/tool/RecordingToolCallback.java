package com.grocery.mcpclient.tool;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * One MCP tool of mcp-server-service, as Spring AI sees it.
 *
 * - getToolDefinition(): name, description and parameters, sent to the model with every question.
 * - call(): runs when the model asks for the tool. It calls mcp-server-service, records the result in the
 *   question's {@link ToolCallRecorder} and gives the model the plain result text, for example
 *   {"status":"OK","message":"Found 1 product.","data":[...]}.
 *
 * It never throws: when mcp-server-service cannot be reached, the model gets a SERVICE_UNAVAILABLE result (and
 * ChatService answers "not available right now" instead of trusting the model).
 *
 * No tool changes data (the customer's click on a card does), but the card to add products (GroceryToolsClient.ADD_TOOL)
 * is not simply prepared because the model asked:
 * - only when the customer's own message asks for a change (ToolCallRecorder.changesAsked, see ChangeRequest), and only
 *   with an amount the customer said ("2k tomatoes" is no "2 kg": that amount is left out, and the add tool asks the
 *   customer how much; the new quantity of prepareMyCartQuantity is not sent at all, the customer is asked, CHOOSE; so
 *   no card ever shows an amount the customer did not say);
 * - not after a cart tool asked the customer a question (CHOOSE) for this message: the customer answers it, not the model
 *   (another product the customer named in the same message is still prepared: "add milk and 1 kg tomatoes");
 * - with several products, each amount only from its own product's part of the words ("Tomato 1 kg, Potato 3 kg");
 * - with the amount the customer said for that product in this message: "Organic Carrot 2 kg" sent as 2 packs, or as
 *   1 kg (the milk's "1L"), is sent as 2 kg;
 * - the same addition (same arguments) only once per question: a repeat gets the first result again.
 * Otherwise the model gets a REFUSED result that says why, and no card is made.
 *
 * Every call is logged with its duration and status; of the arguments only the values of LOGGED_ARGUMENTS (amounts,
 * ids, order numbers, statuses), never text the customer typed (a product, search words, an address, a reason): that
 * can hold anything, also a phone number.
 *
 * The order summary (prepareMyOrder) gets a payment method only when the customer named one ("cash", "UPI" ...): one the
 * model filled in by itself is left out, and the summary then asks the customer how they want to pay.
 */
@Slf4j
public class RecordingToolCallback implements ToolCallback {

    private static final JsonMapper JSON = JsonMapper.shared();
    private static final String ORDER_PREPARE_TOOL = "prepareMyOrder";
    /** The arguments whose values may be logged: no text the customer typed (a product, search words, an address, a reason) */
    private static final Set<String> LOGGED_ARGUMENTS = Set.of("productId", "quantity", "unit", "category", "maxPrice",
            "inStockOnly", "orderNumber", "paymentMethod", "status", "topic", "unreadOnly", "days");

    private final McpSyncClient mcpClient;
    private final McpSchema.Tool tool;
    private final ToolDefinition toolDefinition;
    private final ToolCallRecorder recorder;

    public RecordingToolCallback(McpSyncClient mcpClient, McpSchema.Tool tool, ToolCallRecorder recorder) {
        this.mcpClient = mcpClient;
        this.tool = tool;
        this.toolDefinition = McpToolUtils.createToolDefinition(tool.name(), tool);
        this.recorder = recorder;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public String call(String toolInput) {
        String toolName = tool.name();
        Map<String, Object> arguments;
        try {
            arguments = toolInput == null || toolInput.isBlank()
                    ? Map.of()
                    : JSON.readValue(toolInput, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (JacksonException ex) {
            String result = toolResult("INVALID_INPUT", "The arguments must be a JSON object. Call the tool again.");
            recorder.recordResult(toolName, toolInput, result);
            return result;
        }

        if (ORDER_PREPARE_TOOL.equals(toolName) && arguments.get("paymentMethod") != null
                && !ChangeRequest.paymentSaid(recorder.customerWords())) {
            // The model filled in a way to pay the customer never named: without it, the summary asks the customer
            log.info("Tool {}: the payment method is not in the customer's words and is left out", toolName);
            arguments = new LinkedHashMap<>(arguments);
            arguments.remove("paymentMethod");
        }

        if (GroceryToolsClient.AMOUNT_TOOLS.contains(toolName)) {
            // "... and Organic Carrot 2 kg": the model sent 2 packs (of 500 g, so 1 kg), or 1 kg (the milk's "1L"). The
            // amount the customer said for this product in this message is sent instead.
            ChangeRequest.ListedProduct said = ChangeRequest.amountSaidFor(recorder.customerMessage(), arguments.get("product"));
            if (said != null && !ChangeRequest.sameAmount(said, arguments.get("quantity"), arguments.get("unit"))) {
                log.info("Tool {}: the amount is the one the customer said for this product", toolName);
                arguments = new LinkedHashMap<>(arguments);
                arguments.put("quantity", said.quantity());
                arguments.put("unit", said.unit());
            }
        }

        boolean addRequest = GroceryToolsClient.ADD_TOOL.equals(toolName);
        if (addRequest) {
            String refusal = refusalOfChange(arguments.get("product"));
            if (refusal != null) {
                // Not recorded: it is no fact of the shop (no link, no "grounded" answer from it)
                log.info("Tool {} was not run: {}", toolName, summaryOf(refusal));
                return refusal;
            }
        }
        if (GroceryToolsClient.AMOUNT_TOOLS.contains(toolName)) {
            // "Add Tomato 1 kg, Onion 2 kg and Potato 3 kg": the amount of each product must be in its own part of the words
            String wordsAboutIt = ChangeRequest.wordsAbout(recorder.customerMessage(), recorder.customerWords(), arguments.get("product"));
            String unsaidAmount = ChangeRequest.unsaidAmount(wordsAboutIt, arguments.get("quantity"), arguments.get("unit"));
            if (unsaidAmount != null && addRequest) {
                // "add tomoto in my cart" sent as 1 kg of "tomato": the amount the customer did not say is left out. The
                // add tool then finds the product and asks how much of it, so the customer's answer adds that product.
                log.info("Tool {}: the amount is not in the customer's words and is left out", toolName);
                arguments = new LinkedHashMap<>(arguments);
                arguments.remove("quantity");
                arguments.remove("unit");
            } else if (unsaidAmount != null) {
                // A question for the customer: recorded, so no other change is made before they answer
                String question = toolResult("CHOOSE", unsaidAmount);
                log.info("Tool {} was not run: {}", toolName, summaryOf(question));
                recorder.recordResult(toolName, toolInput, question);
                return question;
            }
        }
        String changeKey = changeKey(toolName, arguments);
        if (addRequest) {
            Optional<String> earlier = recorder.earlierChange(changeKey);
            if (earlier.isPresent()) {
                log.info("Tool {}: the same addition again for this question; the first result is given instead", toolName);
                return earlier.get();
            }
        }

        long startedAt = System.nanoTime();
        McpSchema.CallToolResult callResult;
        try {
            callResult = mcpClient.callTool(new McpSchema.CallToolRequest(toolName, arguments));
        } catch (RuntimeException ex) {
            // mcp-server-service down, a timeout, a broken connection: nothing changed (no tool changes data)
            log.warn("Tool {} {} could not be called after {} ms: {}", toolName, loggable(arguments), millisSince(startedAt),
                    ex.toString());
            recorder.recordFailure(toolName, toolInput);
            return toolResult("SERVICE_UNAVAILABLE", "The shop's data is not available right now. Please try again later.");
        }

        String result = textOf(callResult);
        if (Boolean.TRUE.equals(callResult.isError())) {
            // For example a parameter with the wrong type: tell the model, so it can call the tool correctly
            result = toolResult("INVALID_INPUT", result);
        }
        // The result's status and message, never its "data": that has the customer's orders, addresses and phone
        // numbers, which do not belong in logs (and of the arguments only the values of LOGGED_ARGUMENTS)
        log.info("Tool {} {} -> {} in {} ms ({} characters)", toolName, loggable(arguments), summaryOf(result),
                millisSince(startedAt), result.length());
        recorder.recordResult(toolName, toolInput, result);
        if (addRequest) {
            recorder.rememberChange(changeKey, result);
        }
        return result;
    }

    /** {product=Banana Robusta, quantity=1.0, unit=dozen}, {paymentMethod=cash, address=(not logged)} */
    static String loggable(Map<String, Object> arguments) {
        Map<String, Object> shown = new LinkedHashMap<>();
        arguments.forEach((name, value) -> shown.put(name, LOGGED_ARGUMENTS.contains(name) ? value : "(not logged)"));
        return shown.toString();
    }

    private static long millisSince(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    /** Why the addition of this product must not be prepared now (a REFUSED result), or null when it may */
    private String refusalOfChange(Object product) {
        if (!recorder.changesAsked()) {
            return toolResult("REFUSED", "The customer's message does not ask to change the cart, so nothing was prepared. "
                    + "Answer their question; prepare a change of the cart only when they ask for it.");
        }
        if (recorder.waitsForTheCustomer() && !anotherProductTheCustomerNamed(product)) {
            return toolResult("REFUSED", "A cart tool has asked the customer a question for this message, so nothing more "
                    + "was prepared. Ask the customer that question and wait for their answer.");
        }
        // "Please place my order now" after "I want to buy 1 Harpic ..." and the click on its card: the model prepared the
        // Harpic a second time from the message before ("that makes 2 in your cart", live 2026-10-31). "I want to buy them
        // by online payment" names no product either.
        String message = recorder.customerMessage();
        if (ChangeRequest.checkoutAsked(message) && ChangeRequest.onlyAboutCheckout(message)) {
            return toolResult("REFUSED", "The customer asks to order the cart as it is, not to add a product, so nothing "
                    + "was prepared. Call prepareMyOrder.");
        }
        return null;
    }

    /**
     * After a question about one product ("add milk and 1 kg tomatoes": which milk?), the other products the customer
     * named are still prepared (the tomatoes). Not the product of the question, and not a product the customer never
     * named: that would be the model answering the question itself ("Amul Taaza Toned Milk", or "2 packs" for "2k").
     */
    private boolean anotherProductTheCustomerNamed(Object product) {
        // in this message: a product named before ("add 2 kg tomatoes", then "add milk") is no new request
        return ChangeRequest.productSaid(recorder.customerMessage(), product)
                && recorder.productsAskedAbout().stream().noneMatch(asked -> ChangeRequest.sameProduct(asked, product));
    }

    /**
     * The same change, however the model wrote it: {"product":"Tomatoes","quantity":2.0} and {"quantity":2,
     * "product":"tomatoes"} both give prepareMyCartAdd{product=tomatoes, quantity=2}.
     */
    static String changeKey(String toolName, Map<String, Object> arguments) {
        Map<String, String> sorted = new TreeMap<>();
        arguments.forEach((name, value) -> {
            if (value != null) {
                sorted.put(name, value instanceof Number number
                        ? new BigDecimal(number.toString()).stripTrailingZeros().toPlainString()
                        : value.toString().strip().toLowerCase(Locale.ROOT));
            }
        });
        return toolName + sorted;
    }

    /** {"status":"OK","message":"3 stores.","data":[...]} -> "OK: 3 stores." */
    static String summaryOf(String result) {
        try {
            Map<String, Object> fields = JSON.readValue(result, new TypeReference<LinkedHashMap<String, Object>>() { });
            return fields.get("status") + ": " + fields.get("message");
        } catch (JacksonException ex) {
            return "(not a tool result)";
        }
    }

    /** Our tools answer one text block with the ToolResult JSON. */
    private static String textOf(McpSchema.CallToolResult callResult) {
        return callResult.content().stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(content -> ((McpSchema.TextContent) content).text())
                .collect(Collectors.joining("\n"));
    }

    /** A result in the same shape as the tools' own results, so the system prompt's status rules apply. */
    private static String toolResult(String status, String message) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("message", message);
        return JSON.writeValueAsString(result);
    }
}
