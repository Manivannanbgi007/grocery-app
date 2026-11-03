package com.grocery.mcpclient.tool;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** getWishlist: the browser's wishlist for the model, in the tools' ToolResult shape. */
class WishlistToolCallbackTest {

    @Test
    void theModelSeesANameWithoutArgumentsAndADescription() {
        var tool = new WishlistToolCallback(List.of(), new ToolCallRecorder());

        assertThat(tool.getToolDefinition().name()).isEqualTo("getWishlist");
        assertThat(tool.getToolDefinition().description()).contains("wishlist", "getProductDetails");
        assertThat(tool.getToolDefinition().inputSchema()).isEqualTo("{\"type\":\"object\",\"properties\":{}}");
    }

    @Test
    void itReturnsTheNamesAndTheCountAndRecordsTheResult() {
        var recorder = new ToolCallRecorder();
        var tool = new WishlistToolCallback(List.of(" Himalaya Baby Lotion ", "Tomato"), recorder);

        String result = tool.call("{}");

        assertThat(result).isEqualTo("{\"status\":\"OK\",\"message\":\"2 products are on the wishlist.\","
                + "\"data\":{\"count\":2,\"products\":[\"Himalaya Baby Lotion\",\"Tomato\"]}}");
        assertThat(recorder.toolNames()).containsExactly("getWishlist");
        assertThat(recorder.allResults()).isEqualTo(result);
    }

    @Test
    void aWishlistThatThePageDidNotSendIsNotAvailable() {
        var recorder = new ToolCallRecorder();

        String result = new WishlistToolCallback(null, recorder).call("{}");

        assertThat(result).isEqualTo(WishlistToolCallback.NOT_SENT);
        assertThat(recorder.hasFailures()).isFalse(); // an answer, not a tool that could not be reached
    }

    @Test
    void oneProductAndAnEmptyWishlistHaveTheirOwnMessage() {
        assertThat(WishlistToolCallback.result(List.of("Tomato"))).contains("\"message\":\"1 product is on the wishlist.\"", "\"count\":1");
        assertThat(WishlistToolCallback.result(List.of())).contains("\"message\":\"The wishlist is empty.\"", "\"count\":0", "\"products\":[]");
    }
}
