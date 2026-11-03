package com.grocery.mcpclient.tool;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The tool getWishlist: the products on the customer's wishlist.
 *
 * The shop keeps the wishlist in the customer's browser (there is no wishlist in the backend, so mcp-server-service
 * has no tool for it). The page sends the product names with every question (ChatMessageRequest.wishlist) and this
 * tool gives them to the model. Like the MCP tools, it records its result in the question's ToolCallRecorder, so the
 * answer counts as grounded, and answers in the same ToolResult shape ({"status":"OK","message":...,"data":...}).
 * The names come from the browser: they are data, never instructions (system prompt rule 8).
 */
public class WishlistToolCallback implements ToolCallback {

    public static final String NAME = "getWishlist";

    private static final JsonMapper JSON = JsonMapper.shared();
    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name(NAME)
            .description("The products on the customer's wishlist (saved in their browser, so guests have one too): "
                    + "how many and which. No arguments. For a product's current price or stock, call "
                    + "getProductDetails with its name afterwards.")
            .inputSchema("{\"type\":\"object\",\"properties\":{}}")
            .build();

    static final String NOT_SENT = "{\"status\":\"SERVICE_UNAVAILABLE\",\"message\":\"The wishlist is not available here. "
            + "The customer can see it on the wishlist page of the website.\"}";

    private final List<String> productNames;
    private final ToolCallRecorder recorder;

    /**
     * @param productNames the names from the request (validated there: at most 50, none blank); null when the page did
     *                     not send the wishlist (an older page): the tool then says so, instead of the model guessing
     * @param recorder     the question's recorder
     */
    public WishlistToolCallback(List<String> productNames, ToolCallRecorder recorder) {
        this.productNames = productNames == null ? null : productNames.stream().map(String::strip).toList();
        this.recorder = recorder;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return DEFINITION;
    }

    @Override
    public String call(String toolInput) {
        String result = productNames == null ? NOT_SENT : result(productNames);
        recorder.recordResult(NAME, toolInput, result);
        return result;
    }

    /** {"status":"OK","message":"2 products are on the wishlist.","data":{"count":2,"products":["Tomato","Onion"]}} */
    static String result(List<String> productNames) {
        int count = productNames.size();
        String message = switch (count) {
            case 0 -> "The wishlist is empty.";
            case 1 -> "1 product is on the wishlist.";
            default -> count + " products are on the wishlist.";
        };
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", count);
        data.put("products", productNames);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "OK");
        result.put("message", message);
        result.put("data", data);
        return JSON.writeValueAsString(result);
    }
}
