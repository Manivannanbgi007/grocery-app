package com.grocery.mcpclient.tool;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class RecordingToolCallbackTest {

    private static final McpSchema.Tool SEARCH_PRODUCTS = McpSchema.Tool.builder()
            .name("searchProducts")
            .description("Search the products of the shop")
            .inputSchema(new McpSchema.JsonSchema("object",
                    Map.of("query", Map.of("type", "string")), List.of(), false, null, null))
            .build();

    private static final McpSchema.Tool ADD_TO_MY_CART = McpSchema.Tool.builder()
            .name("prepareMyCartAdd")
            .description("Add a product to the logged-in customer's own cart")
            .inputSchema(new McpSchema.JsonSchema("object",
                    Map.of("product", Map.of("type", "string")), List.of("product"), false, null, null))
            .build();
    private static final McpSchema.Tool PREPARE_MY_CART_QUANTITY = McpSchema.Tool.builder()
            .name("prepareMyCartQuantity")
            .description("Prepare changing how much of a product is in the logged-in customer's own cart")
            .inputSchema(new McpSchema.JsonSchema("object",
                    Map.of("product", Map.of("type", "string"), "quantity", Map.of("type", "number")),
                    List.of("product", "quantity"), false, null, null))
            .build();
    private static final String ADDED = "{\"status\":\"OK\",\"message\":\"Added 2 x Tomato (1 kg) to the cart.\"}";
    /** prepareMyCartAdd without an amount: it found Tomato and asks how much */
    static final String HOW_MUCH_TOMATO = "{\"status\":\"CHOOSE\",\"message\":\"How much Tomato (1 kg) does the customer "
            + "want? Ask them, and prepare nothing until they answer.\",\"data\":{\"amountFor\":{\"productId\":1,\"name\":"
            + "\"Tomato\",\"packSize\":\"1 kg\",\"price\":\"₹36\",\"store\":\"Fresh Mart\"}}}";

    @Mock
    private McpSyncClient mcpClient;

    private final ToolCallRecorder recorder = new ToolCallRecorder();

    @Test
    void definitionUsesTheNameDescriptionAndParametersOfTheMcpTool() {
        var callback = new RecordingToolCallback(mcpClient, SEARCH_PRODUCTS, recorder);

        assertThat(callback.getToolDefinition().name()).isEqualTo("searchProducts");
        assertThat(callback.getToolDefinition().description()).isEqualTo("Search the products of the shop");
        assertThat(callback.getToolDefinition().inputSchema()).contains("\"query\"");
    }

    @Test
    void callReturnsTheToolTextAndRecordsIt() {
        String toolText = "{\"status\":\"OK\",\"message\":\"Found 1 product.\",\"data\":[{\"price\":\"₹56\"}]}";
        when(mcpClient.callTool(any())).thenReturn(McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(toolText))).isError(false).build());

        String result = new RecordingToolCallback(mcpClient, SEARCH_PRODUCTS, recorder).call("{\"query\":\"amul butter\"}");

        assertThat(result).isEqualTo(toolText);
        verify(mcpClient).callTool(new McpSchema.CallToolRequest("searchProducts", Map.of("query", "amul butter")));
        assertThat(recorder.toolNames()).containsExactly("searchProducts");
        assertThat(recorder.allResults()).contains("₹56");
        assertThat(recorder.hasFailures()).isFalse();
    }

    @Test
    void anErrorOfTheToolIsGivenToTheModelAsInvalidInput() {
        when(mcpClient.callTool(any())).thenReturn(McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent("maxPrice must be a number"))).isError(true).build());

        String result = new RecordingToolCallback(mcpClient, SEARCH_PRODUCTS, recorder).call("{\"maxPrice\":\"cheap\"}");

        assertThat(result).isEqualTo("{\"status\":\"INVALID_INPUT\",\"message\":\"maxPrice must be a number\"}");
    }

    @Test
    void anUnreachableServerGivesServiceUnavailableAndIsRecordedAsAFailure() {
        when(mcpClient.callTool(any())).thenThrow(new RuntimeException("Connection refused"));

        String result = new RecordingToolCallback(mcpClient, SEARCH_PRODUCTS, recorder).call("{\"query\":\"milk\"}");

        assertThat(result).contains("\"status\":\"SERVICE_UNAVAILABLE\"");
        assertThat(recorder.hasFailures()).isTrue();
        assertThat(recorder.hasResults()).isFalse();
    }

    @Test
    void theLogLine_hasTheToolAndItsSummary_butNotTheCustomersData(CapturedOutput output) {
        String toolText = "{\"status\":\"OK\",\"message\":\"2 orders.\",\"data\":[{\"contactPhone\":\"9000000004\","
                + "\"address\":\"12, 5th Cross\"}]}";
        when(mcpClient.callTool(any())).thenReturn(McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(toolText))).isError(false).build());
        Logger logger = (Logger) LoggerFactory.getLogger(RecordingToolCallback.class);
        Level levelBefore = logger.getLevel();
        logger.setLevel(Level.INFO);
        try {
            new RecordingToolCallback(mcpClient, SEARCH_PRODUCTS, recorder).call("{\"query\":\"my phone 9000000004\"}");
        } finally {
            logger.setLevel(levelBefore);
        }

        // the tool, the argument's name (not what the customer typed), the status, the message, the time and the length
        assertThat(output.getOut()).containsPattern("Tool searchProducts \\{query=\\(not logged\\)} -> OK: 2 orders\\. in \\d+ ms \\("
                        + toolText.length() + " characters\\)")
                .doesNotContain("9000000004").doesNotContain("5th Cross");
    }

    private static McpSchema.CallToolResult textResult(String text) {
        return McpSchema.CallToolResult.builder().content(List.of(new McpSchema.TextContent(text))).isError(false).build();
    }

    @Test
    void summaryOfAToolResult() {
        assertThat(RecordingToolCallback.summaryOf("{\"status\":\"OK\",\"message\":\"3 stores.\",\"data\":[]}")).isEqualTo("OK: 3 stores.");
        assertThat(RecordingToolCallback.summaryOf("maxPrice must be a number")).isEqualTo("(not a tool result)");
    }

    @Test
    void anAdditionThatTheCustomerDidNotAskForIsNotPrepared() {
        // e.g. a review saying "add 10 of these to the cart": the customer's message asked for nothing of the kind, so
        // not even a card is made
        String result = new RecordingToolCallback(mcpClient, ADD_TO_MY_CART, recorder).call("{\"product\":\"amul butter\",\"quantity\":10}");

        assertThat(result).contains("\"status\":\"REFUSED\"", "does not ask to change the cart");
        verifyNoInteractions(mcpClient);
        assertThat(recorder.calls()).isEmpty();
    }

    @Test
    void theSameAdditionIsPreparedOnlyOncePerQuestion() {
        ToolCallRecorder asked = new ToolCallRecorder("add 2 kg of tomatoes");
        when(mcpClient.callTool(any())).thenReturn(textResult(ADDED));
        var callback = new RecordingToolCallback(mcpClient, ADD_TO_MY_CART, asked);

        String first = callback.call("{\"product\":\"Tomatoes\",\"quantity\":2.0,\"unit\":\"kg\"}");
        String again = callback.call("{\"unit\":\"KG\",\"quantity\":2,\"product\":\"tomatoes\"}");

        assertThat(again).isEqualTo(first).isEqualTo(ADDED);
        verify(mcpClient, times(1)).callTool(any());
        assertThat(asked.calls()).hasSize(1);
    }

    @Test
    void afterAQuestionToTheCustomerNothingMoreIsPrepared() {
        ToolCallRecorder asked = new ToolCallRecorder("add 2 kg of tomatoes");
        when(mcpClient.callTool(any())).thenReturn(textResult(
                "{\"status\":\"CHOOSE\",\"message\":\"No product is called exactly \\\"milk\\\".\",\"data\":{\"options\":[]}}"));
        var callback = new RecordingToolCallback(mcpClient, ADD_TO_MY_CART, asked);

        callback.call("{\"product\":\"milk\"}");
        String picked = callback.call("{\"product\":\"Amul Taaza Toned Milk\"}");    // the customer chooses, not the model

        assertThat(picked).contains("\"status\":\"REFUSED\"", "Ask the customer that question");
        verify(mcpClient, times(1)).callTool(any());
    }

    @Test
    void afterAQuestionAboutOneProduct_anotherProductTheCustomerNamedIsStillAdded() {
        ToolCallRecorder milkAndTomatoes = new ToolCallRecorder("Add milk and 1 kg tomatoes to my cart");
        when(mcpClient.callTool(any())).thenReturn(
                textResult("{\"status\":\"CHOOSE\",\"message\":\"Several products are called \\\"milk\\\".\",\"data\":{\"options\":[]}}"),
                textResult("{\"status\":\"OK\",\"message\":\"Added 1 x Tomato (1 kg) to the cart.\"}"));
        var callback = new RecordingToolCallback(mcpClient, ADD_TO_MY_CART, milkAndTomatoes);

        callback.call("{\"product\":\"milk\"}");
        String tomatoes = callback.call("{\"product\":\"tomatoes\",\"quantity\":1,\"unit\":\"kg\"}");
        String picked = callback.call("{\"product\":\"Amul Taaza Toned Milk\"}");       // the customer chooses the milk

        assertThat(tomatoes).contains("\"status\":\"OK\"");
        assertThat(picked).contains("\"status\":\"REFUSED\"");
        assertThat(milkAndTomatoes.productsAskedAbout()).containsExactly("milk");
        verify(mcpClient, times(2)).callTool(any());
    }

    @Test
    void theAmountOfOneProductCannotBeTakenFromAnotherOfTheList() {
        // a mix-up: 3 kg is what the customer said for the potatoes
        ToolCallRecorder threeProducts = new ToolCallRecorder("Add Tomato 1 kg, Onion 2 kg and Potato 3 kg to my cart");
        when(mcpClient.callTool(any())).thenReturn(textResult("{\"status\":\"OK\",\"message\":\"Added 3 x Potato (1 kg) to the cart.\"}"));
        var callback = new RecordingToolCallback(mcpClient, ADD_TO_MY_CART, threeProducts);

        callback.call("{\"product\":\"Tomato\",\"quantity\":3,\"unit\":\"kg\"}");
        callback.call("{\"product\":\"Potato\",\"quantity\":3,\"unit\":\"kg\"}");

        // the tomatoes get the 1 kg the customer said for them; the potatoes keep their 3 kg
        verify(mcpClient).callTool(new McpSchema.CallToolRequest("prepareMyCartAdd",
                Map.of("product", "Tomato", "quantity", new BigDecimal("1"), "unit", "kg")));
        verify(mcpClient).callTool(new McpSchema.CallToolRequest("prepareMyCartAdd", Map.of("product", "Potato", "quantity", 3, "unit", "kg")));

        // an amount said for no product of the list is left out: 3 kg of onions in "Add onions and 2 kg tomatoes" (the
        // add tool then asks how much onion)
        new RecordingToolCallback(mcpClient, ADD_TO_MY_CART, new ToolCallRecorder("Add onions and 2 kg tomatoes"))
                .call("{\"product\":\"onions\",\"quantity\":3,\"unit\":\"kg\"}");
        verify(mcpClient).callTool(new McpSchema.CallToolRequest("prepareMyCartAdd", Map.of("product", "onions")));
    }

    @Test
    void theAmountTheCustomerSaidForAProduct_isSent_notTheModelsOwn() {
        // live, 2026-10-30: "Organic Carrot 2 kg" was sent once as 2 packs and once as 1 kg: 2 packs of 500 g were added
        String message = "can you add Akshayakalpa Organic Milk 1L and Organic Carrot 2 kg in my cart?";
        when(mcpClient.callTool(any())).thenReturn(textResult("{\"status\":\"OK\",\"message\":\"Added 4 x Organic Carrot (500 g) to the cart.\"}"));

        new RecordingToolCallback(mcpClient, ADD_TO_MY_CART, new ToolCallRecorder(message))
                .call("{\"product\":\"Organic Carrot\",\"quantity\":2,\"unit\":\"packs\"}");
        new RecordingToolCallback(mcpClient, ADD_TO_MY_CART, new ToolCallRecorder(message))
                .call("{\"product\":\"Organic Carrot\",\"quantity\":1,\"unit\":\"kg\"}");

        verify(mcpClient, times(2)).callTool(new McpSchema.CallToolRequest("prepareMyCartAdd",
                Map.of("product", "Organic Carrot", "quantity", new BigDecimal("2"), "unit", "kg")));
    }

    @Test
    void anAmountOfTheMessageBefore_isNoAmountOfThisMessage() {
        // "Add 2 kg tomatoes", then "make it 3 kg": 3 kg is what the customer wants now
        when(mcpClient.callTool(any())).thenReturn(textResult("{\"status\":\"OK\",\"message\":\"Change prepared.\"}"));
        ToolCallRecorder newAmount = new ToolCallRecorder("make it 3 kg", "make it 3 kg\nAdd 2 kg tomatoes", true);

        String result = new RecordingToolCallback(mcpClient, PREPARE_MY_CART_QUANTITY, newAmount)
                .call("{\"product\":\"tomatoes\",\"quantity\":3,\"unit\":\"kg\"}");

        assertThat(result).contains("\"status\":\"OK\"");
        verify(mcpClient).callTool(new McpSchema.CallToolRequest("prepareMyCartQuantity",
                Map.of("product", "tomatoes", "quantity", 3, "unit", "kg")));
    }

    @Test
    void anAmountTheCustomerDidNotSayIsLeftOut_andTheAddToolAsksHowMuch() {
        // the model read "2k tomatoes" as 2 kg
        when(mcpClient.callTool(any())).thenReturn(textResult(HOW_MUCH_TOMATO));
        ToolCallRecorder twoK = new ToolCallRecorder("Add 2k tomatoes to my cart");
        var callback = new RecordingToolCallback(mcpClient, ADD_TO_MY_CART, twoK);

        String result = callback.call("{\"product\":\"tomatoes\",\"quantity\":2,\"unit\":\"kg\"}");
        String again = callback.call("{\"product\":\"tomatoes\",\"quantity\":2}");

        verify(mcpClient).callTool(new McpSchema.CallToolRequest("prepareMyCartAdd", Map.of("product", "tomatoes")));
        assertThat(result).isEqualTo(HOW_MUCH_TOMATO);
        assertThat(twoK.calls().getFirst().asksHowMuch()).isTrue();
        assertThat(again).contains("\"status\":\"REFUSED\"");      // the customer answers first
    }

    @Test
    void askingToPlaceTheOrder_addsNothingAgain() {
        // live: "Please place my order now" after "I want to buy 1 Harpic ..." and its card: the model added Harpic again
        ToolCallRecorder placeOrder = new ToolCallRecorder("Please place my order now",
                "Please place my order now\nI want to buy 1 Harpic Toilet Cleaner. Please order it for me, cash on delivery.", true);

        String result = new RecordingToolCallback(mcpClient, ADD_TO_MY_CART, placeOrder)
                .call("{\"product\":\"Harpic Toilet Cleaner\",\"quantity\":1}");

        assertThat(result).contains("\"status\":\"REFUSED\"", "Call prepareMyOrder");
        assertThat(placeOrder.toolNames()).isEmpty();       // not recorded: the code's own order step can still run
        verifyNoInteractions(mcpClient);

        // adding and ordering in one message is still an addition
        when(mcpClient.callTool(any())).thenReturn(textResult(ADDED));
        assertThat(new RecordingToolCallback(mcpClient, ADD_TO_MY_CART, new ToolCallRecorder("add 2 kg tomatoes and place my order"))
                .call("{\"product\":\"tomatoes\",\"quantity\":2,\"unit\":\"kg\"}")).isEqualTo(ADDED);
    }

    @Test
    void addingOnePackNeedsTheCustomersOne() {
        when(mcpClient.callTool(any())).thenReturn(textResult(HOW_MUCH_TOMATO));

        // "add tomato" says no amount: the model's 1 is left out, so the tool asks
        new RecordingToolCallback(mcpClient, ADD_TO_MY_CART, new ToolCallRecorder("add tomato to my cart"))
                .call("{\"product\":\"tomato\",\"quantity\":1}");
        // "add a tomato" says one
        new RecordingToolCallback(mcpClient, ADD_TO_MY_CART, new ToolCallRecorder("add a tomato to my cart"))
                .call("{\"product\":\"tomato\",\"quantity\":1}");

        verify(mcpClient).callTool(new McpSchema.CallToolRequest("prepareMyCartAdd", Map.of("product", "tomato")));
        verify(mcpClient).callTool(new McpSchema.CallToolRequest("prepareMyCartAdd", Map.of("product", "tomato", "quantity", 1)));
    }

    @Test
    void oneMore_theCodesOnePackSettlesTheAmountQuestion_otherQuestionsStillStopChanges() {
        // "one more": the model prepared the product as 2 (not in the words); the code then adds one pack itself
        ToolCallRecorder oneMore = new ToolCallRecorder("can you add one more item for Aashirvaad Whole Wheat Atta in my cart ?");
        when(mcpClient.callTool(any())).thenReturn(textResult("{\"status\":\"OK\",\"message\":\"Added 1 x Aashirvaad Whole "
                + "Wheat Atta (5 kg) to the cart.\"}"));
        new RecordingToolCallback(mcpClient, PREPARE_MY_CART_QUANTITY, oneMore)
                .call("{\"product\":\"Aashirvaad Whole Wheat Atta\",\"quantity\":2}");
        var add = new RecordingToolCallback(mcpClient, ADD_TO_MY_CART, oneMore);
        assertThat(add.call("{\"product\":\"Aashirvaad Whole Wheat Atta\"}")).contains("\"status\":\"REFUSED\"");

        oneMore.settleAmountQuestions();
        assertThat(add.call("{\"product\":\"Aashirvaad Whole Wheat Atta\"}")).contains("\"status\":\"OK\"");
        verify(mcpClient, times(1)).callTool(any());

        // a real question to the customer ("which milk?") still stops every change
        ToolCallRecorder milk = new ToolCallRecorder("add milk and one more Tomato");
        milk.recordResult("prepareMyCartAdd", "{\"product\":\"milk\"}", "{\"status\":\"CHOOSE\",\"message\":\"Which milk?\"}");
        milk.settleAmountQuestions();
        assertThat(milk.waitsForTheCustomer()).isTrue();
    }

    @Test
    void aNewQuantityOnACardMustBeTheCustomersOwnAmountToo() {
        // "2k tomatoes": the card would show 2 kg, which the customer never said
        when(mcpClient.callTool(any())).thenReturn(textResult("{\"status\":\"OK\",\"message\":\"Change prepared; NOTHING is changed yet.\"}"));
        ToolCallRecorder unclear = new ToolCallRecorder("make it 2k tomatoes");
        String question = new RecordingToolCallback(mcpClient, PREPARE_MY_CART_QUANTITY, unclear)
                .call("{\"product\":\"tomatoes\",\"quantity\":2,\"unit\":\"kg\"}");

        assertThat(question).contains("\"status\":\"CHOOSE\"", "The amount \\\"2 kg\\\" is not in the customer's words");
        assertThat(unclear.waitsForTheCustomer()).isTrue();         // no other change before the customer answers
        verifyNoInteractions(mcpClient);

        // the customer's own amount: the server prepares the card (it only reads, so it is no "change" of this turn)
        ToolCallRecorder said = new ToolCallRecorder("make it 3 kg of tomatoes");
        assertThat(new RecordingToolCallback(mcpClient, PREPARE_MY_CART_QUANTITY, said)
                .call("{\"product\":\"tomatoes\",\"quantity\":3,\"unit\":\"kg\"}")).contains("Change prepared");
    }

    @Test
    void theOrderSummaryGetsOnlyAPaymentMethodTheCustomerNamed() {
        McpSchema.Tool prepareMyOrder = McpSchema.Tool.builder().name("prepareMyOrder").description("Prepare an order")
                .inputSchema(new McpSchema.JsonSchema("object", Map.of("paymentMethod", Map.of("type", "string")), List.of(), false, null, null))
                .build();
        when(mcpClient.callTool(any())).thenReturn(textResult("{\"status\":\"CHOOSE\",\"message\":\"Ask the customer how they want to pay.\"}"));

        new RecordingToolCallback(mcpClient, prepareMyOrder, new ToolCallRecorder("Place my order\n", true))
                .call("{\"paymentMethod\":\"cash\"}");
        new RecordingToolCallback(mcpClient, prepareMyOrder, new ToolCallRecorder("Place my order, cash on delivery\n", true))
                .call("{\"paymentMethod\":\"cash\"}");

        verify(mcpClient).callTool(new McpSchema.CallToolRequest("prepareMyOrder", Map.of()));                         // left out
        verify(mcpClient).callTool(new McpSchema.CallToolRequest("prepareMyOrder", Map.of("paymentMethod", "cash")));  // said
    }

    @Test
    void anAdditionWithoutAnAnswer_isAFailure_andNoCard() {
        // no tool changes data, so a timeout changed nothing: the answer says the shop cannot be reached
        ToolCallRecorder asked = new ToolCallRecorder("add 2 kg of tomatoes");
        when(mcpClient.callTool(any())).thenThrow(new RuntimeException("Read timed out"));

        String result = new RecordingToolCallback(mcpClient, ADD_TO_MY_CART, asked).call("{\"product\":\"tomatoes\",\"quantity\":2}");

        assertThat(result).contains("\"status\":\"SERVICE_UNAVAILABLE\"");
        assertThat(asked.hasFailures()).isTrue();
        assertThat(com.grocery.mcpclient.assistant.ChatActions.of(asked)).isNull();
    }

    @Test
    void argumentsThatAreNotJsonAreRejectedWithoutCallingTheServer() {
        String result = new RecordingToolCallback(mcpClient, SEARCH_PRODUCTS, recorder).call("query=milk");

        assertThat(result).contains("\"status\":\"INVALID_INPUT\"");
        verifyNoInteractions(mcpClient);
    }
}
