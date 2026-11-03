package com.grocery.mcpclient.tool;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Everything the tools returned while ONE question was answered.
 *
 * AnswerValidator checks the model's answer against these results: every price, percentage and coupon code in the
 * answer must appear here. A new recorder is made for every question (it is not shared between requests).
 *
 * For the tool that prepares adding to the cart (GroceryToolsClient.ADD_TOOL) it also knows whether the customer's
 * message asks for a change at all, and which additions were already prepared for this question: the same one is
 * prepared only once, even when the model asks for it twice or ChatService asks the model a second time.
 */
public class ToolCallRecorder {

    private static final JsonMapper JSON = JsonMapper.shared();

    private final List<ToolCall> calls = new ArrayList<>();
    /** the customer's message of this question only (null for no customer) */
    private final String customerMessage;
    /** the customer's message and the one before it (null for no customer, e.g. in a panel) */
    private final String customerWords;
    /** whether those words ask for a change of the cart (ChangeRequest.asked) */
    private final boolean changesAsked;
    /** change key (see RecordingToolCallback.changeKey) -> the result the add tool gave */
    private final Map<String, String> changes = new HashMap<>();
    /** the code prepares "one more" itself: the amount check's own questions no longer stop it (see settleAmountQuestions) */
    private boolean amountQuestionsSettled;

    /**
     * @param toolName  for example "searchProducts"
     * @param arguments the JSON arguments the model sent
     * @param result    the text the tool returned (a ToolResult JSON); null when the tool could not be reached
     */
    public record ToolCall(String toolName, String arguments, String result) {

        public boolean failed() {
            return result == null;
        }

        /** The result's status ("OK", "REFUSED" ...); null when the call failed or the text is not a tool result */
        public String status() {
            return field("status");
        }

        public String message() {
            return field("message");
        }

        /**
         * Whether the result asks the customer how much they want: the amount check's question (an amount they did not
         * say, see ChangeRequest.unsaidAmount) or prepareMyCartAdd's (they said none; its data names the product as
         * "amountFor")
         */
        public boolean asksHowMuch() {
            return "CHOOSE".equals(status())
                    && (ChangeRequest.isUnsaidAmountQuestion(message()) || data().path("amountFor").isObject());
        }

        /** The result's "data" (a missing node when there is none) */
        public JsonNode data() {
            if (failed()) {
                return JSON.missingNode();
            }
            try {
                return JSON.readTree(result).path("data");
            } catch (JacksonException ex) {
                return JSON.missingNode();
            }
        }

        /** One of the arguments the model sent as a text, e.g. argument("product") -> "tomatoes"; null without it */
        public String argument(String name) {
            try {
                JsonNode value = JSON.readTree(arguments == null ? "{}" : arguments).path(name);
                return value.isString() ? value.asString() : null;
            } catch (JacksonException ex) {
                return null;
            }
        }

        private String field(String name) {
            if (failed()) {
                return null;
            }
            try {
                JsonNode value = JSON.readTree(result).path(name);
                return value.isString() ? value.asString() : null;
            } catch (JacksonException ex) {
                return null;
            }
        }
    }

    /** A recorder for a question that asks for no change: the add tool then prepares nothing. */
    public ToolCallRecorder() {
        this(null, false);
    }

    /** A recorder for these words of a customer, which ask to change the cart */
    public ToolCallRecorder(String customerWords) {
        this(customerWords, true);
    }

    /**
     * @param customerWords the customer's message and the one before it, for the amounts and the payment of a tool call
     *                      (ChangeRequest); null when no customer is chatting
     * @param changesAsked  whether those words ask to change the cart (ChangeRequest.asked)
     */
    public ToolCallRecorder(String customerWords, boolean changesAsked) {
        this(customerWords, customerWords, changesAsked);
    }

    /**
     * @param customerMessage the customer's message of this question only: the products and amounts of a list are read
     *                        from it ("add 2 kg tomatoes" before "make it 3 kg" is no amount of this question)
     * @param customerWords   that message and the one before it
     * @param changesAsked    whether those words ask to change the cart (ChangeRequest.asked)
     */
    public ToolCallRecorder(String customerMessage, String customerWords, boolean changesAsked) {
        this.customerMessage = customerMessage;
        this.customerWords = customerWords;
        this.changesAsked = changesAsked;
    }

    public boolean changesAsked() {
        return changesAsked;
    }

    /** The customer's message of this question only; null when no customer is chatting */
    public String customerMessage() {
        return customerMessage;
    }

    /** The customer's own words, for the amounts and the payment of a tool call; null when no customer is chatting */
    public String customerWords() {
        return customerWords;
    }

    public void recordResult(String toolName, String arguments, String result) {
        calls.add(new ToolCall(toolName, arguments, result));
    }

    public void recordFailure(String toolName, String arguments) {
        calls.add(new ToolCall(toolName, arguments, null));
    }

    /** The result the model got for this change earlier in this question, if it was asked for before */
    public Optional<String> earlierChange(String changeKey) {
        return Optional.ofNullable(changes.get(changeKey));
    }

    public void rememberChange(String changeKey, String result) {
        changes.put(changeKey, result);
    }

    public List<ToolCall> calls() {
        return List.copyOf(calls);
    }

    /** The distinct tool names in call order, for the response and the logs. */
    public List<String> toolNames() {
        return List.copyOf(calls.stream().map(ToolCall::toolName).collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    public boolean hasResults() {
        return calls.stream().anyMatch(call -> !call.failed());
    }

    /** true when mcp-server-service could not be reached for at least one call. */
    public boolean hasFailures() {
        return calls.stream().anyMatch(ToolCall::failed);
    }

    /**
     * true when a cart tool (adding, or preparing a new quantity: GroceryToolsClient.AMOUNT_TOOLS) asked the customer
     * something for this question (CHOOSE): they must answer it first
     */
    public boolean waitsForTheCustomer() {
        return calls.stream().anyMatch(this::isOpenQuestion);
    }

    /**
     * The products of those open questions (their "product" argument): "add milk and 1 kg tomatoes" asked "which milk?",
     * so "milk" waits for the customer, while the tomatoes they also named can still be added
     */
    public List<String> productsAskedAbout() {
        return calls.stream().filter(this::isOpenQuestion).map(call -> call.argument("product")).filter(Objects::nonNull).toList();
    }

    private boolean isOpenQuestion(ToolCall call) {
        return GroceryToolsClient.AMOUNT_TOOLS.contains(call.toolName()) && "CHOOSE".equals(call.status())
                && !(amountQuestionsSettled && call.asksHowMuch());
    }

    /**
     * "Can you add one more item for X?": the model set X to 2 (right), the amount check asked "how much?" because "2"
     * is not in the words, and the code then prepares one pack itself (ChatServiceImpl). That question is settled by
     * it, so it no longer stops the card; any other question ("which milk?") still does. The same for the add tool's own
     * "how much?" (the model left the amount out): the code prepares what the customer said.
     */
    public void settleAmountQuestions() {
        amountQuestionsSettled = true;
    }

    /** All tool results as one text, to search for the facts of an answer. */
    public String allResults() {
        return calls.stream()
                .filter(call -> !call.failed())
                .map(ToolCall::result)
                .collect(Collectors.joining("\n"));
    }

    /** All arguments the model sent, e.g. {"category":"Fruits","maxPrice":100}: the customer's own limits. */
    public String allArguments() {
        return calls.stream()
                .map(ToolCall::arguments)
                .filter(arguments -> arguments != null)
                .collect(Collectors.joining("\n"));
    }
}
