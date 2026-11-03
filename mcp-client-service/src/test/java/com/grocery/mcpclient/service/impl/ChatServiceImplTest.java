package com.grocery.mcpclient.service.impl;

import com.grocery.mcpclient.assistant.AnswerValidator;
import com.grocery.mcpclient.assistant.ChatActionsTest;
import com.grocery.mcpclient.assistant.ShoppingAssistant;
import com.grocery.mcpclient.assistant.SystemPrompt;
import com.grocery.mcpclient.config.ChatProperties;
import com.grocery.mcpclient.context.Audience;
import com.grocery.mcpclient.context.UserContext;
import com.grocery.mcpclient.dto.request.ChatMessageRequest;
import com.grocery.mcpclient.dto.response.ChatAction;
import com.grocery.mcpclient.dto.response.ChatApprovalResponse;
import com.grocery.mcpclient.dto.response.ChatLink;
import com.grocery.mcpclient.dto.response.ChatReplyResponse;
import com.grocery.mcpclient.entity.CheckoutStep;
import com.grocery.mcpclient.entity.Conversation;
import com.grocery.mcpclient.exception.AssistantBusyException;
import com.grocery.mcpclient.exception.AssistantUnavailableException;
import com.grocery.mcpclient.service.ApprovalService;
import com.grocery.mcpclient.service.ConversationService;
import com.grocery.mcpclient.tool.ChangeRequest;
import com.grocery.mcpclient.tool.GroceryToolsClient;
import com.grocery.mcpclient.tool.ToolCallRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.core.io.ByteArrayResource;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The steps of one question with a fake model: the answer check, the second attempt, the fixed answers.
 * The tool results are written into the question's ToolCallRecorder, as the real tools do.
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    private static final UserContext CUSTOMER = new UserContext(4L, "CUSTOMER", "customer-jwt");
    private static final String BUTTER_RESULT = "{\"status\":\"OK\",\"data\":[{\"name\":\"Amul Butter\",\"price\":\"₹56\"}]}";
    /** prepareMyCartAdd's OK for 2 x Tomato (1 kg), when the cart has none yet */
    private static final String TOMATO_ADD = addPrepared(1, "Tomato", "1 kg", 2, "₹72", 2);
    /** getMyCart of the customer who asked "Can you remove Harpic Toilet Cleaner from my cart?" */
    private static final String CART_WITH_HARPIC = "{\"status\":\"OK\",\"message\":\"The cart has 2 products.\",\"data\":{"
            + "\"items\":[{\"productId\":119,\"name\":\"Harpic Toilet Cleaner\",\"packSize\":\"500 ml\",\"quantity\":1,"
            + "\"lineTotal\":\"₹99.75\"},{\"productId\":42,\"name\":\"Aashirvaad Whole Wheat Atta\",\"packSize\":\"5 kg\","
            + "\"quantity\":2,\"lineTotal\":\"₹541.50\"}],\"payable\":\"₹641.25\"}}";
    /** getMyCart of customer1, who wrote "i do not want tomato in my cart. can you remove it please" (2026-10-30) */
    private static final String TOMATO_CART = "{\"status\":\"OK\",\"message\":\"The cart has 3 products.\",\"data\":{"
            + "\"store\":\"Fresh Mart\",\"items\":["
            + "{\"productId\":1,\"name\":\"Tomato\",\"packSize\":\"1 kg\",\"quantity\":2,\"lineTotal\":\"₹72\"},"
            + "{\"productId\":2,\"name\":\"Onion\",\"packSize\":\"1 kg\",\"quantity\":1,\"lineTotal\":\"₹40\"},"
            + "{\"productId\":3,\"name\":\"Potato\",\"packSize\":\"1 kg\",\"quantity\":3,\"lineTotal\":\"₹85.50\"}]}}";
    private static final String MANGO_NOT_IN_THE_CART = "{\"status\":\"NOT_FOUND\",\"message\":\"The cart has no product "
            + "called \\\"mango\\\". The cart has: Tomato (1 kg), Onion (1 kg), Potato (1 kg).\"}";

    @Mock
    private ApprovalService approvalService;
    @Mock
    private ConversationService conversationService;
    @Mock
    private ChatMemory chatMemory;
    @Mock
    private GroceryToolsClient groceryTools;
    @Mock
    private ShoppingAssistant assistant;

    private final Conversation conversation = Conversation.start(4L, Instant.parse("2026-10-26T10:00:00Z"));
    private final List<ToolCallRecorder> recorders = new ArrayList<>();
    private ChatServiceImpl chatService;

    @BeforeEach
    void setUp() {
        chatService = service(4, Duration.ofSeconds(30));
        lenient().when(conversationService.startOrContinue(null, 4L)).thenReturn(conversation);
        lenient().when(chatMemory.get(conversation.getId())).thenReturn(List.of());
        lenient().when(groceryTools.toolsFor(any(), any())).thenAnswer(call -> {
            recorders.add(call.getArgument(0));
            return List.of();
        });
    }

    @Test
    void anAnswerThatPassesTheCheckIsSavedAndReturned() {
        when(assistant.answer(anyList(), eq("How much is Amul butter?"), anyList(), any(), isNull()))
                .thenAnswer(call -> toolResultAndAnswer(BUTTER_RESULT, "Amul Butter costs ₹56."));

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "  How much is Amul butter? "), CUSTOMER);

        assertThat(reply.answer()).isEqualTo("Amul Butter costs ₹56.");
        assertThat(reply.grounded()).isTrue();
        assertThat(reply.toolsUsed()).containsExactly("searchProducts");
        assertThat(reply.conversationId()).isEqualTo(UUID.fromString(conversation.getId()));
        assertThat(savedMessages()).containsExactly("How much is Amul butter?", "Amul Butter costs ₹56.");
        verify(conversationService).messageAdded(conversation);
    }

    @Test
    void aRejectedAnswerIsAskedAgainWithTheReason() {
        // 1st attempt (no correction): an invented price; 2nd attempt (with the correction): the right one
        when(assistant.answer(anyList(), anyString(), anyList(), any(), any())).thenAnswer(call -> call.getArgument(4) == null
                ? toolResultAndAnswer(BUTTER_RESULT, "Amul Butter costs ₹60.")
                : "Amul Butter costs ₹56.");

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "Price of Amul butter?"), CUSTOMER);

        assertThat(reply.answer()).isEqualTo("Amul Butter costs ₹56.");
        assertThat(reply.grounded()).isTrue();
        ArgumentCaptor<String> correction = ArgumentCaptor.forClass(String.class);
        verify(assistant, times(2)).answer(anyList(), anyString(), anyList(), any(), correction.capture());
        assertThat(correction.getAllValues().get(0)).isNull();
        assertThat(correction.getAllValues().get(1)).contains("the amount ₹60 was not returned by any tool");
    }

    @Test
    void anAnswerRejectedTwiceIsReplacedByTheFallback() {
        when(assistant.answer(anyList(), anyString(), anyList(), any(), any()))
                .thenAnswer(call -> toolResultAndAnswer(BUTTER_RESULT, "Amul Butter costs ₹60."));

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "Price of Amul butter?"), CUSTOMER);

        assertThat(reply.answer()).isEqualTo(ChatServiceImpl.FALLBACK_ANSWER);
        assertThat(reply.grounded()).isFalse();
        verify(assistant, times(2)).answer(anyList(), anyString(), anyList(), any(), any());
        assertThat(savedMessages()).containsExactly("Price of Amul butter?", ChatServiceImpl.FALLBACK_ANSWER);
    }

    @Test
    void noSecondAttemptWhenTheFirstOneWasTooSlow() {
        chatService = service(4, Duration.ZERO);
        when(assistant.answer(anyList(), anyString(), anyList(), any(), isNull()))
                .thenAnswer(call -> toolResultAndAnswer(BUTTER_RESULT, "Amul Butter costs ₹60."));

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "Price of Amul butter?"), CUSTOMER);

        assertThat(reply.answer()).isEqualTo(ChatServiceImpl.FALLBACK_ANSWER);
        verify(assistant, never()).answer(anyList(), anyString(), anyList(), any(), notNull());
    }

    @Test
    void whenAToolCouldNotBeReachedTheModelIsNotTrusted() {
        when(assistant.answer(anyList(), anyString(), anyList(), any(), isNull())).thenAnswer(call -> {
            recorders.getLast().recordFailure("searchProducts", "{\"query\":\"milk\"}");
            return "Milk is out of stock.";
        });

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "Do you have milk?"), CUSTOMER);

        assertThat(reply.answer()).isEqualTo(ChatServiceImpl.TOOLS_DOWN_ANSWER);
        assertThat(reply.grounded()).isFalse();
    }

    @Test
    void aGreetingWithoutToolsIsAnsweredButNotGrounded() {
        when(assistant.answer(anyList(), anyString(), anyList(), any(), isNull())).thenReturn("Hello! How can I help you with your shopping?");

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "Hi"), CUSTOMER);

        assertThat(reply.answer()).isEqualTo("Hello! How can I help you with your shopping?");
        assertThat(reply.grounded()).isFalse();
        assertThat(reply.toolsUsed()).isEmpty();
    }

    @Test
    void aQuestionIsTurnedAwayWhenAllAnswerSlotsStayBusy() throws Exception {
        chatService = service(1, Duration.ofSeconds(30));
        CountDownLatch firstQuestionIsBeingAnswered = new CountDownLatch(1);
        CountDownLatch releaseFirstQuestion = new CountDownLatch(1);
        when(assistant.answer(anyList(), anyString(), anyList(), any(), isNull())).thenAnswer(call -> {
            firstQuestionIsBeingAnswered.countDown();
            releaseFirstQuestion.await(5, TimeUnit.SECONDS);
            return "Hello!";
        });

        CompletableFuture<ChatReplyResponse> first = CompletableFuture.supplyAsync(
                () -> chatService.sendMessage(new ChatMessageRequest(null, "Hi"), CUSTOMER));
        assertThat(firstQuestionIsBeingAnswered.await(5, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> chatService.sendMessage(new ChatMessageRequest(null, "Hi again"), CUSTOMER))
                .isInstanceOf(AssistantBusyException.class);

        releaseFirstQuestion.countDown();
        assertThat(first.get(5, TimeUnit.SECONDS).answer()).isEqualTo("Hello!");
    }

    @Test
    void theCardsTheCustomerConfirmedBecomeLinesOfTheConversationBeforeTheQuestion() {
        when(assistant.answer(anyList(), eq("Cancel my last order"), anyList(), any(), isNull())).thenReturn("Which one?");
        var confirmed = List.of(new ChatMessageRequest.ConfirmedCard("PLACE_ORDER", 207L),
                new ChatMessageRequest.ConfirmedCard("CANCEL_ORDER", 206L));

        chatService.sendMessage(new ChatMessageRequest(null, "Cancel my last order", null, confirmed), CUSTOMER);

        // the lines are saved first, so the model reads them in the history it gets for the question
        InOrder inOrder = inOrder(chatMemory);
        ArgumentCaptor<List<Message>> lines = ArgumentCaptor.forClass(List.class);
        inOrder.verify(chatMemory).add(eq(conversation.getId()), lines.capture());
        inOrder.verify(chatMemory).get(conversation.getId());
        assertThat(lines.getValue()).allMatch(AssistantMessage.class::isInstance).extracting(Message::getText)
                .containsExactly("Order #207 placed with the Confirm button.", "Order #206 cancelled with the Confirm button.");
    }

    @Test
    void onlyACustomersChatGetsTheConfirmedLines() {
        when(conversationService.startOrContinue(null, null)).thenReturn(conversation);
        when(assistant.answer(anyList(), anyString(), anyList(), any(), isNull())).thenReturn("Hello!");

        chatService.sendMessage(new ChatMessageRequest(null, "Hi", null,
                List.of(new ChatMessageRequest.ConfirmedCard("PLACE_ORDER", 207L))), UserContext.GUEST);

        assertThat(savedMessages()).containsExactly("Hi", "Hello!");      // the question and the answer only
    }

    @Test
    void onlyALoggedInCustomerGetsTheToolsForOwnCartAndOrders() {
        when(conversationService.startOrContinue(null, null)).thenReturn(Conversation.start(null, Instant.parse("2026-10-26T10:00:00Z")));
        when(chatMemory.get(any())).thenReturn(List.of());
        when(assistant.answer(anyList(), anyString(), anyList(), any(), isNull())).thenReturn("Hello!");

        chatService.sendMessage(new ChatMessageRequest(null, "Hi"), CUSTOMER);
        chatService.sendMessage(new ChatMessageRequest(null, "Hi"), UserContext.GUEST);

        verify(groceryTools).toolsFor(any(), eq(Audience.CUSTOMER));
        verify(groceryTools).toolsFor(any(), eq(Audience.GUEST));
    }

    @Test
    void aVendorGetsTheVendorToolsAndTheVendorRules() {
        UserContext vendor = new UserContext(3L, "VENDOR", "vendor-jwt");
        when(conversationService.startOrContinue(null, 3L)).thenReturn(Conversation.start(3L, Instant.parse("2026-10-26T10:00:00Z")));
        when(chatMemory.get(any())).thenReturn(List.of());
        when(assistant.answer(anyList(), anyString(), anyList(), eq(Audience.VENDOR), isNull()))
                .thenAnswer(call -> {
                    recorders.getLast().recordResult("getVendorStoreToday", "{}",
                            "{\"status\":\"OK\",\"data\":{\"store\":\"Fresh Mart\",\"newOrders\":3}}");
                    return "Fresh Mart has 3 new orders.";
                });

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "Do I have new orders?"), vendor);

        assertThat(reply.answer()).isEqualTo("Fresh Mart has 3 new orders.");
        assertThat(reply.grounded()).isTrue();
        assertThat(reply.toolsUsed()).containsExactly("getVendorStoreToday");
        verify(groceryTools).toolsFor(any(), eq(Audience.VENDOR));
    }

    @Test
    void aCheckedAnswerComesWithLinksAndTheFallbackWithout() {
        String products = "{\"status\":\"OK\",\"data\":[{\"productId\":12,\"name\":\"Amul Butter\",\"price\":\"₹56\"}]}";
        when(assistant.answer(anyList(), anyString(), anyList(), any(), any()))
                .thenAnswer(call -> toolResultAndAnswer(products, "Amul Butter costs ₹56."))
                .thenAnswer(call -> toolResultAndAnswer(products, "Amul Butter costs ₹60."));

        ChatReplyResponse checked = chatService.sendMessage(new ChatMessageRequest(null, "Price of Amul butter?"), CUSTOMER);

        assertThat(checked.links()).containsExactly(new ChatLink("Amul Butter", "/products/12"));

        // Rejected twice: the fixed answer has no links
        when(assistant.answer(anyList(), anyString(), anyList(), any(), any()))
                .thenAnswer(call -> toolResultAndAnswer(products, "Amul Butter costs ₹60."));
        ChatReplyResponse fallback = chatService.sendMessage(new ChatMessageRequest(null, "Price of Amul butter?"), CUSTOMER);

        assertThat(fallback.answer()).isEqualTo(ChatServiceImpl.FALLBACK_ANSWER);
        assertThat(fallback.links()).isEmpty();
    }

    @Test
    void theShopGetsTheBrowsersWishlistAsATool() {
        when(assistant.answer(anyList(), anyString(), anyList(), eq(Audience.CUSTOMER), isNull())).thenAnswer(call -> {
            // The model calls getWishlist, as the real one does for "how many items are in my wishlist?"
            List<ToolCallback> tools = call.getArgument(2);
            assertThat(tools).extracting(tool -> tool.getToolDefinition().name()).containsExactly("getWishlist");
            assertThat(tools.getFirst().call("{}")).contains("\"count\":1", "Himalaya Baby Lotion");
            return "You have 1 product on your wishlist: Himalaya Baby Lotion.";
        });

        ChatReplyResponse reply = chatService.sendMessage(
                new ChatMessageRequest(null, "How many items are in my wishlist?", List.of("Himalaya Baby Lotion")), CUSTOMER);

        assertThat(reply.answer()).isEqualTo("You have 1 product on your wishlist: Himalaya Baby Lotion.");
        assertThat(reply.grounded()).isTrue();
        assertThat(reply.toolsUsed()).containsExactly("getWishlist");
    }

    @Test
    void withoutAWishlistFromThePageTheToolSaysItIsNotAvailableAndAPanelHasNoWishlistTool() {
        UserContext vendor = new UserContext(3L, "VENDOR", "vendor-jwt");
        when(conversationService.startOrContinue(null, 3L)).thenReturn(Conversation.start(3L, Instant.parse("2026-10-26T10:00:00Z")));
        when(chatMemory.get(any())).thenReturn(List.of());
        List<List<ToolCallback>> toolLists = new ArrayList<>();
        when(assistant.answer(anyList(), anyString(), anyList(), any(), isNull())).thenAnswer(call -> {
            toolLists.add(call.getArgument(2));
            return "Hello!";
        });

        chatService.sendMessage(new ChatMessageRequest(null, "Hi"), CUSTOMER);
        chatService.sendMessage(new ChatMessageRequest(null, "Hi", List.of("Tomato")), vendor);

        assertThat(toolLists).hasSize(2);
        assertThat(toolLists.get(0)).singleElement().satisfies(tool -> assertThat(tool.call("{}"))
                .contains("SERVICE_UNAVAILABLE", "The customer can see it on the wishlist page of the website."));
        assertThat(toolLists.get(1)).isEmpty();
    }

    @Test
    void onlyTheCustomersOwnWordsAllowAChangeOfTheCart() {
        when(assistant.answer(anyList(), anyString(), anyList(), any(), isNull())).thenReturn("Sure.");

        chatService.sendMessage(new ChatMessageRequest(null, "Add 2 kg tomatoes"), CUSTOMER);
        chatService.sendMessage(new ChatMessageRequest(null, "What do customers say about Amul Butter?"), CUSTOMER);
        // the answer to the assistant's question "which milk?" counts with the message before it
        when(chatMemory.get(conversation.getId()))
                .thenReturn(List.of(new UserMessage("add milk"), new AssistantMessage("Which milk do you mean?")));
        chatService.sendMessage(new ChatMessageRequest(null, "The Amul one"), CUSTOMER);

        assertThat(recorders).extracting(ToolCallRecorder::changesAsked).containsExactly(true, false, true);
    }

    @Test
    void aCardWaitsForTheCustomersApproval_andTheReplySaysSo() {
        ChatApprovalResponse pending = new ChatApprovalResponse("5d1f0b4e-9f2a-4a51-8f43-0c2b7a1e9d10", "PENDING", Instant.parse("2026-11-01T10:00:00Z"), null, null);
        when(approvalService.open(eq(conversation), anyString(), any())).thenReturn(pending);
        when(assistant.answer(anyList(), anyString(), anyList(), any(), isNull())).thenAnswer(call -> {
            recorders.getLast().recordResult("prepareMyCartAdd", "{\"product\":\"tomatoes\",\"quantity\":2,\"unit\":\"kg\"}", TOMATO_ADD);
            return "Here is 2 x Tomato (1 kg) for ₹72. Please press Confirm to add it to your cart.";
        });

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "Add 2 kg tomatoes"), CUSTOMER);

        assertThat(reply.action().kind()).isEqualTo("ADD_TO_CART");
        assertThat(reply.approval()).isEqualTo(pending);
        // stored with exactly the answer the history shows, so the card finds its answer again after a reload
        verify(approvalService).open(conversation, reply.answer(), reply.action());
    }

    @Test
    void anAnswerWithoutACardHasNoApproval() {
        when(assistant.answer(anyList(), anyString(), anyList(), any(), isNull())).thenReturn("We deliver from 7 am to 10 pm.");

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "When do you deliver?"), CUSTOMER);

        assertThat(reply.approval()).isNull();
        verifyNoInteractions(approvalService);
    }

    @Test
    void aCardStillComes_whenItsApprovalCannotBeSaved() {
        when(approvalService.open(any(), anyString(), any())).thenThrow(new IllegalStateException("database down"));
        when(assistant.answer(anyList(), anyString(), anyList(), any(), isNull())).thenAnswer(call -> {
            recorders.getLast().recordResult("prepareMyCartAdd", "{\"product\":\"tomatoes\",\"quantity\":2,\"unit\":\"kg\"}", TOMATO_ADD);
            return "Here is 2 x Tomato (1 kg) for ₹72.";
        });

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "Add 2 kg tomatoes"), CUSTOMER);

        assertThat(reply.action().kind()).isEqualTo("ADD_TO_CART");
        assertThat(reply.approval()).isNull();
    }

    @Test
    void theModelsAddCardGetsItsAnswerFromTheCode() {
        when(assistant.answer(anyList(), anyString(), anyList(), any(), isNull())).thenAnswer(call -> {
            recorders.getLast().recordResult("prepareMyCartAdd", "{\"product\":\"tomatoes\",\"quantity\":2,\"unit\":\"kg\"}", TOMATO_ADD);
            return "Here is 2 x Tomato (1 kg) for ₹72. Please press Confirm to add it to your cart.";
        });

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "Add 2 kg tomatoes"), CUSTOMER);

        // even a good answer of the model is replaced: under a cart card the answer is always written in code
        assertThat(reply.answer()).isEqualTo("Here is the product to add to your cart: 2 x Tomato (1 kg) for ₹72. Please check "
                + "it and press Confirm to add it; nothing is added before that.");
        assertThat(reply.action().kind()).isEqualTo("ADD_TO_CART");
        assertThat(reply.action().items()).singleElement().satisfies(line -> {
            assertThat(line.productId()).isEqualTo(1L);
            assertThat(line.quantity()).isEqualTo(2);
            assertThat(line.newQuantity()).isEqualTo(2);
        });
        assertThat(reply.links()).contains(new ChatLink("Your cart", "/cart"));
    }

    @Test
    void aFalseAddedNextToTheAddCard_getsTheCardsOwnWords() {
        // nothing is added before the click, so "I added ..." is false even next to the card
        when(assistant.answer(anyList(), anyString(), anyList(), any(), any())).thenAnswer(call -> {
            if (call.getArgument(4) == null) {
                recorders.getLast().recordResult("prepareMyCartAdd", "{\"product\":\"tomatoes\",\"quantity\":2,\"unit\":\"kg\"}", TOMATO_ADD);
            }
            return "Done! I added 2 kg of tomatoes to your cart.";
        });

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "Add 2 kg tomatoes"), CUSTOMER);

        assertThat(reply.answer()).isEqualTo("Here is the product to add to your cart: 2 x Tomato (1 kg) for ₹72. Please "
                + "check it and press Confirm to add it; nothing is added before that.");
        assertThat(reply.action().kind()).isEqualTo("ADD_TO_CART");
    }

    @Test
    void whenTheModelOnlyAsks_theNamedProductGetsTheAddCardFromTheCode_andTheOrderComesAfterTheClick() {
        // the user's words; the real model found Harpic, then asked "Would you like me to add it to your cart and prepare
        // the order?"
        List<String> addArguments = new ArrayList<>();
        List<String> prepareArguments = new ArrayList<>();
        givenTheServerTools(addArguments, addPrepared(119, "Harpic Toilet Cleaner", "500 ml", 1, "₹99.75", 1), prepareArguments,
                ChatActionsTest.SUMMARY);
        when(assistant.answer(anyList(), anyString(), anyList(), any(), isNull())).thenAnswer(call -> {
            recorders.getLast().recordResult("searchProducts", "{\"query\":\"Harpic Toilet Cleaner\"}", "{\"status\":\"OK\","
                    + "\"data\":[{\"productId\":119,\"name\":\"Harpic Toilet Cleaner\",\"price\":\"₹99.75\"}]}");
            return "I found Harpic Toilet Cleaner. Would you like me to add it to your cart and prepare the order?";
        });

        ChatReplyResponse reply = chatService.sendMessage(
                new ChatMessageRequest(null, "i want to buy this \"Harpic Toilet Cleaner\" product. can you order for me ?"), CUSTOMER);

        // no amount said: none is sent (the real tool then asks how many; this fake one prepares a pack)
        assertThat(addArguments).containsExactly("{\"product\":\"Harpic Toilet Cleaner\"}");
        assertThat(reply.action().kind()).isEqualTo("ADD_TO_CART");
        // the order summary reads the cart as it is: it comes after the click, not now
        assertThat(prepareArguments).isEmpty();
        assertThat(reply.answer()).isEqualTo("""
                Here is the product to add to your cart: 1 x Harpic Toilet Cleaner (500 ml) for ₹99.75. Please check it and \
                press Confirm to add it; nothing is added before that.
                After you confirm, ask me to place your order.""");
    }

    @Test
    void whenTheShopSaysNoToTheNamedProduct_thatIsTheAnswer_andNoCard() {
        List<String> prepareArguments = new ArrayList<>();
        givenTheServerTools(new ArrayList<>(), "{\"status\":\"REFUSED\",\"message\":\"The cart has products from Green Basket, "
                + "and Harpic Toilet Cleaner is sold by Fresh Mart. A cart can hold the products of one store only: place the "
                + "order for this cart first, or empty the cart.\"}", prepareArguments, ChatActionsTest.SUMMARY);
        when(assistant.answer(anyList(), anyString(), anyList(), any(), isNull())).thenAnswer(call -> {
            recorders.getLast().recordResult("searchProducts", "{\"query\":\"Harpic\"}",
                    "{\"status\":\"OK\",\"data\":[{\"name\":\"Harpic Toilet Cleaner\"}]}");
            return "Would you like me to add Harpic Toilet Cleaner to your cart?";
        });

        ChatReplyResponse reply = chatService.sendMessage(
                new ChatMessageRequest(null, "I want to buy Harpic Toilet Cleaner. Order it for me."), CUSTOMER);

        assertThat(reply.answer()).isEqualTo("The cart has products from Green Basket, and Harpic Toilet Cleaner is sold by "
                + "Fresh Mart. A cart can hold the products of one store only: place the order for this cart first, or empty the cart.");
        assertThat(prepareArguments).isEmpty();
        assertThat(reply.action()).isNull();
    }

    @Test
    void oneMoreOfAProductInTheCart_getsTheAddCardFromTheCode_whenTheModelOnlyClaimsIt() {
        // the user's words; the real model wrote "Added ... your total is ₹555.75" twice without calling the tool
        List<String> addArguments = new ArrayList<>();
        givenTheServerTools(addArguments, addPrepared(42, "Aashirvaad Whole Wheat Atta", "5 kg", 1, "₹270.75", 2),
                new ArrayList<>(), ChatActionsTest.SUMMARY);
        when(assistant.answer(anyList(), anyString(), anyList(), any(), any()))
                .thenReturn("Added one more Aashirvaad Whole Wheat Atta. Your total is ₹555.75.");

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null,
                "can you add one more item for Aashirvaad Whole Wheat Atta in my cart ?"), CUSTOMER);

        // "one more": the one pack the customer said
        assertThat(addArguments).containsExactly("{\"product\":\"Aashirvaad Whole Wheat Atta\",\"quantity\":1}");
        assertThat(reply.answer()).isEqualTo("Here is the product to add to your cart: 1 x Aashirvaad Whole Wheat Atta (5 kg) "
                + "for ₹270.75 (that makes 2 in your cart). Please check it and press Confirm to add it; nothing is added before that.");
        assertThat(reply.action().items()).singleElement().satisfies(line -> assertThat(line.newQuantity()).isEqualTo(2));
    }

    @Test
    void oneMore_whenTheAmountCheckStoppedTheModelsNewQuantity_preparesOnePack() {
        // the real model, live: a new quantity of 2 for Aashirvaad (right, but "2" is not in the words), so the amount
        // check asked "How much ... would you like?" instead
        List<String> addArguments = new ArrayList<>();
        givenTheServerTools(addArguments, addPrepared(42, "Aashirvaad Whole Wheat Atta", "5 kg", 1, "₹270.75", 2),
                new ArrayList<>(), ChatActionsTest.SUMMARY);
        String question = "can you add one more item for Aashirvaad Whole Wheat Atta in my cart ?";
        when(assistant.answer(anyList(), anyString(), anyList(), any(), isNull())).thenAnswer(call -> {
            String stopped = "{\"status\":\"CHOOSE\",\"message\":\"" + ChangeRequest.unsaidAmount(question, 2, null)
                    .replace("\"", "\\\"") + "\"}";
            recorders.getLast().recordResult("prepareMyCartQuantity", "{\"product\":\"Aashirvaad Whole Wheat Atta\",\"quantity\":2}", stopped);
            return "How much Aashirvaad Whole Wheat Atta would you like? For example, 2 kg or 2 packs?";
        });

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, question), CUSTOMER);

        assertThat(addArguments).hasSize(1);
        assertThat(reply.action().kind()).isEqualTo("ADD_TO_CART");
        assertThat(reply.answer()).startsWith("Here is the product to add to your cart: 1 x Aashirvaad Whole Wheat Atta (5 kg)");
    }

    // ----- "Add tomoto in my cart?" - "How much Tomato ...?" - "3kg" (the user's conversation, 2026-10-31) -----

    /** prepareMyCartAdd without an amount: it found Tomato (also for "tomoto") and asks how much */
    private static final String HOW_MUCH_TOMATO = "{\"status\":\"CHOOSE\",\"message\":\"How much Tomato (1 kg) does the "
            + "customer want? Ask them, and prepare nothing until they answer.\",\"data\":{\"amountFor\":{\"productId\":1,"
            + "\"name\":\"Tomato\",\"packSize\":\"1 kg\",\"price\":\"₹36\",\"store\":\"Fresh Mart\"}}}";
    private static final String TOMATO_QUESTION = "How much Tomato would you like to add? One pack is 1 kg for ₹36. Nothing is "
            + "added yet.";
    private static final String THREE_KG_TOMATO_CARD = "Here is the product to add to your cart: 3 x Tomato (1 kg) for ₹108. "
            + "Please check it and press Confirm to add it; nothing is added before that.";

    @Test
    void aTypoWithoutAnAmount_getsOneQuestionAboutTheCatalogsProduct_andTheConversationRemembersIt() {
        // live: the model sent "tomato" with 1 kg (its amount is left out, so the tool asked), then the code sent the
        // customer's "tomoto". Before, the answer was "How much tomato ...?" AND "The shop has no product called tomoto".
        List<String> addArguments = new ArrayList<>();
        givenTheServerTools(addArguments, HOW_MUCH_TOMATO, new ArrayList<>(), ChatActionsTest.SUMMARY);
        when(assistant.answer(anyList(), anyString(), anyList(), any(), isNull())).thenAnswer(call -> {
            recorders.getLast().recordResult("prepareMyCartAdd", "{\"product\":\"tomato\",\"quantity\":1,\"unit\":\"kg\"}", HOW_MUCH_TOMATO);
            return "How much tomato would you like?";
        });

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "Add tomoto in my cart?"), CUSTOMER);

        assertThat(reply.answer()).isEqualTo(TOMATO_QUESTION);         // one question, the catalog's name, no "no product"
        assertThat(reply.action()).isNull();                           // nothing to confirm yet
        assertThat(conversation.getPendingProductId()).isEqualTo(1L);
        assertThat(conversation.getPendingProductName()).isEqualTo("Tomato");
        verify(conversationService).messageAdded(conversation);        // saved with the conversation
    }

    @ParameterizedTest
    @ValueSource(strings = {"3kg", "3 kg", "Three kilograms", "I need 3 kilos", "Just 3 kg, please"})
    void theAnswerToHowMuch_preparesThePendingProduct_withoutAskingTheModel(String answer) {
        conversation.setPendingAdd(1L, "Tomato");
        List<String> addArguments = new ArrayList<>();
        givenTheServerTools(addArguments, addPrepared(1, "Tomato", "1 kg", 3, "₹108", 3), new ArrayList<>(), ChatActionsTest.SUMMARY);

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, answer), CUSTOMER);

        assertThat(addArguments).containsExactly("{\"product\":\"1\",\"quantity\":3,\"unit\":\"kg\"}");    // by its id
        assertThat(reply.answer()).isEqualTo(THREE_KG_TOMATO_CARD);
        assertThat(reply.action().kind()).isEqualTo(ChatAction.ADD_TO_CART);
        assertThat(reply.action().items()).singleElement().satisfies(line -> {
            assertThat(line.productId()).isEqualTo(1L);
            assertThat(line.newQuantity()).isEqualTo(3);
        });
        assertThat(reply.grounded()).isTrue();
        assertThat(conversation.hasPendingAdd()).isFalse();             // done: the card's Confirm button adds it
        assertThat(savedMessages()).containsExactly(answer, THREE_KG_TOMATO_CARD);
        verify(assistant, never()).answer(anyList(), anyString(), anyList(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0 kg", "-2 kg", "2 or 3 kg"})
    void anAmountThatCannotBeRight_isAskedAgain_andTheQuestionStaysOpen(String answer) {
        conversation.setPendingAdd(1L, "Tomato");

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, answer), CUSTOMER);

        assertThat(reply.answer()).isEqualTo("How much Tomato would you like to add? Please tell me one amount above zero, "
                + "for example 2 packs.");
        assertThat(reply.action()).isNull();
        assertThat(conversation.getPendingProductId()).isEqualTo(1L);
        verify(groceryTools, never()).toolsFor(any(), any());
        verify(assistant, never()).answer(anyList(), anyString(), anyList(), any(), any());
    }

    @Test
    void theShopsQuestionAboutAnAmount_isTheAnswer_andAnotherAmountCanStillBeGiven() {
        conversation.setPendingAdd(1L, "Tomato");
        String tooMany = "That would be 25 packs of Tomato (1 kg a pack), and a cart takes at most 20 packs of a product. How "
                + "many packs should it be?";
        givenTheServerTools(new ArrayList<>(), "{\"status\":\"CHOOSE\",\"message\":\"" + tooMany + "\"}", new ArrayList<>(),
                ChatActionsTest.SUMMARY);

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "25 kg"), CUSTOMER);

        assertThat(reply.answer()).isEqualTo(tooMany);
        assertThat(reply.action()).isNull();
        assertThat(conversation.getPendingProductId()).isEqualTo(1L);
    }

    @Test
    void aProductThatIsGoneMeanwhile_endsTheQuestion() {
        conversation.setPendingAdd(1L, "Tomato");
        givenTheServerTools(new ArrayList<>(), "{\"status\":\"NOT_FOUND\",\"message\":\"The shop has no product with id 1.\"}",
                new ArrayList<>(), ChatActionsTest.SUMMARY);

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "3 kg"), CUSTOMER);

        assertThat(reply.answer()).isEqualTo("Sorry, Tomato is not sold any more, so nothing was added.");
        assertThat(conversation.hasPendingAdd()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"cancel", "never mind", "No, don't add it"})
    void callingOffThePendingAddition_addsNothing(String answer) {
        conversation.setPendingAdd(1L, "Tomato");

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, answer), CUSTOMER);

        assertThat(reply.answer()).isEqualTo("Okay, I have not added Tomato. Nothing in your cart was changed.");
        assertThat(reply.action()).isNull();
        assertThat(conversation.hasPendingAdd()).isFalse();
        verify(groceryTools, never()).toolsFor(any(), any());
        verify(assistant, never()).answer(anyList(), anyString(), anyList(), any(), any());
    }

    @Test
    void anotherQuestionWhileAnAmountIsAsked_endsTheQuestion_andTheModelAnswersIt() {
        conversation.setPendingAdd(1L, "Tomato");
        when(assistant.answer(anyList(), eq("How much is Amul butter?"), anyList(), any(), isNull()))
                .thenAnswer(call -> toolResultAndAnswer(BUTTER_RESULT, "Amul Butter costs ₹56."));

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "How much is Amul butter?"), CUSTOMER);

        assertThat(reply.answer()).isEqualTo("Amul Butter costs ₹56.");
        assertThat(conversation.hasPendingAdd()).isFalse();
    }

    @Test
    void anotherProductWithAnAmount_isANewRequest_notTheAnswerForThePendingOne() {
        conversation.setPendingAdd(1L, "Tomato");
        List<String> addArguments = new ArrayList<>();
        givenTheServerTools(addArguments, addPrepared(2, "Onion", "1 kg", 2, "₹80", 2), new ArrayList<>(), ChatActionsTest.SUMMARY);
        when(assistant.answer(anyList(), anyString(), anyList(), any(), isNull())).thenReturn("Sure.");

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "Add 2 kg onions to my cart"), CUSTOMER);

        assertThat(addArguments).containsExactly("{\"product\":\"onions\",\"quantity\":2,\"unit\":\"kg\"}");
        assertThat(reply.action().items()).extracting(ChatAction.Line::name).containsExactly("Onion");
        assertThat(conversation.hasPendingAdd()).isFalse();
    }

    @Test
    void theProductIsPreparedOnlyOnce_theSameAmountAgainIsNoSecondCard() {
        conversation.setPendingAdd(1L, "Tomato");
        List<String> addArguments = new ArrayList<>();
        givenTheServerTools(addArguments, addPrepared(1, "Tomato", "1 kg", 3, "₹108", 3), new ArrayList<>(), ChatActionsTest.SUMMARY);
        when(assistant.answer(anyList(), anyString(), anyList(), any(), isNull())).thenReturn("Can I help you with anything else?");

        ChatReplyResponse first = chatService.sendMessage(new ChatMessageRequest(null, "3 kg"), CUSTOMER);
        ChatReplyResponse again = chatService.sendMessage(new ChatMessageRequest(null, "3 kg"), CUSTOMER);

        assertThat(first.action().kind()).isEqualTo(ChatAction.ADD_TO_CART);
        assertThat(addArguments).hasSize(1);            // nothing waits after the card: "3 kg" again is a new message
        assertThat(again.action()).isNull();
    }

    @Test
    void eachConversationHasItsOwnPendingAddition() {
        // customer 4 was asked how much Tomato; customer 5's "3 kg" in their own conversation has nothing to do with it
        conversation.setPendingAdd(1L, "Tomato");
        Conversation others = Conversation.start(5L, Instant.parse("2026-10-26T10:00:00Z"));
        when(conversationService.startOrContinue(UUID.fromString(others.getId()), 5L)).thenReturn(others);
        when(assistant.answer(anyList(), eq("3 kg"), anyList(), any(), isNull())).thenReturn("3 kg of which product would you like?");

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(UUID.fromString(others.getId()), "3 kg"),
                new UserContext(5L, "CUSTOMER", "customer5-jwt"));

        assertThat(reply.answer()).isEqualTo("3 kg of which product would you like?");
        assertThat(others.hasPendingAdd()).isFalse();
        assertThat(conversation.getPendingProductId()).isEqualTo(1L);   // customer 4's question is untouched
    }

    @Test
    void twoProductsOneRefused_oneCardForTheOther_andTheShopsReasonForTheRefusedOne() {
        // the user's report (log of 8114, 2026-10-30 00:22): the tomato could go in, the carrot could not (another store)
        List<String> prepareArguments = new ArrayList<>();
        givenTheServerTools(new ArrayList<>(), TOMATO_ADD, prepareArguments, ChatActionsTest.SUMMARY);
        when(assistant.answer(anyList(), anyString(), anyList(), any(), isNull())).thenAnswer(call -> {
            recorders.getLast().recordResult("prepareMyCartAdd", "{\"product\":\"tomato\",\"quantity\":1,\"unit\":\"kg\"}",
                    addPrepared(1, "Tomato", "1 kg", 1, "₹36", 1));
            recorders.getLast().recordResult("prepareMyCartAdd", "{\"product\":\"Organic Carrot\",\"quantity\":2,\"unit\":\"kg\"}",
                    CARROT_REFUSED);
            return "I have prepared Tomato and Organic Carrot for your cart.";     // a claim the answer check cannot catch
        });

        ChatReplyResponse reply = chatService.sendMessage(
                new ChatMessageRequest(null, "can you add tomato 1kg and Organic Carrot 2 kg in my cart?"), CUSTOMER);

        assertThat(reply.answer()).isEqualTo("""
                Here is the product to add to your cart: 1 x Tomato (1 kg) for ₹36. Please check it and press Confirm to add \
                it; nothing is added before that.
                """ + CARROT_REFUSAL);
        assertThat(reply.action().items()).extracting(ChatAction.Line::name).containsExactly("Tomato");
        assertThat(reply.grounded()).isTrue();
        assertThat(prepareArguments).isEmpty();
    }

    @Test
    void threeProducts_oneCardWithAllOfThem() {
        givenTheServerTools(new ArrayList<>(), TOMATO_ADD, new ArrayList<>(), ChatActionsTest.SUMMARY);
        when(assistant.answer(anyList(), anyString(), anyList(), any(), isNull())).thenAnswer(call -> {
            recorders.getLast().recordResult("prepareMyCartAdd", "{\"product\":\"Tomato\",\"quantity\":1,\"unit\":\"kg\"}",
                    addPrepared(1, "Tomato", "1 kg", 1, "₹36", 1));
            // a product that is in the cart already: the card says what the cart has after the click
            recorders.getLast().recordResult("prepareMyCartAdd", "{\"product\":\"Onion\",\"quantity\":2,\"unit\":\"kg\"}",
                    addPrepared(2, "Onion", "1 kg", 2, "₹80", 3));
            recorders.getLast().recordResult("prepareMyCartAdd", "{\"product\":\"Potato\",\"quantity\":3,\"unit\":\"kg\"}",
                    addPrepared(3, "Potato", "1 kg", 3, "₹85.50", 3));
            return "The cart now has Tomato, Onion, and Potato. The total amount to pay is ₹221.50.";
        });

        ChatReplyResponse reply = chatService.sendMessage(
                new ChatMessageRequest(null, "Add Tomato 1 kg, Onion 2 kg and Potato 3 kg to my cart"), CUSTOMER);

        assertThat(reply.answer()).isEqualTo("Here are the products to add to your cart: 1 x Tomato (1 kg) for ₹36, 2 x Onion "
                + "(1 kg) for ₹80 (that makes 3 in your cart) and 3 x Potato (1 kg) for ₹85.50. Please check them and press "
                + "Confirm to add them; nothing is added before that.");
        assertThat(reply.action().items()).extracting(ChatAction.Line::productId).containsExactly(1L, 2L, 3L);
        assertThat(savedMessages().getLast()).isEqualTo(reply.answer());
        verify(assistant, times(1)).answer(anyList(), anyString(), anyList(), any(), any());
    }

    @Test
    void aListThatTheModelOnlyClaimedToAdd_isPreparedInCode_withTheCustomersAmounts() {
        // the user's message (live log of 8114, 2026-10-30 01:26-01:44): the model read both products and the cart, then
        // wrote that both were added with an invented total. Both are Green Basket's, the cart Fresh Mart's.
        List<String> addArguments = new ArrayList<>();
        givenTheServerTools(addArguments, null, new ArrayList<>(), ChatActionsTest.SUMMARY);
        doAnswer(call -> {
            String arguments = call.getArgument(0);
            addArguments.add(arguments);
            String product = arguments.contains("Akshayakalpa") ? "Akshayakalpa Organic Milk" : "Organic Carrot";
            String refused = CARROT_REFUSED.replace("Organic Carrot", product);
            recorders.getLast().recordResult("prepareMyCartAdd", arguments, refused);
            return refused;
        }).when(addTool).call(anyString());
        when(assistant.answer(anyList(), anyString(), anyList(), any(), any()))
                .thenReturn("I have added Akshayakalpa Organic Milk 1L and Organic Carrot 2 kg to your cart. The total is ₹151.");

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null,
                "can you add Akshayakalpa Organic Milk 1L and Organic Carrot 2 kg in my cart?"), CUSTOMER);

        // each product once, with the customer's own amount, through the tool and its checks
        assertThat(addArguments).containsExactly("{\"product\":\"Akshayakalpa Organic Milk\",\"quantity\":1,\"unit\":\"l\"}",
                "{\"product\":\"Organic Carrot\",\"quantity\":2,\"unit\":\"kg\"}");
        assertThat(reply.answer()).isEqualTo(CARROT_REFUSAL.replace("Organic Carrot", "Akshayakalpa Organic Milk") + "\n"
                + CARROT_REFUSAL);
        assertThat(reply.action()).isNull();
        assertThat(reply.grounded()).isTrue();
    }

    @Test
    void anAmountTheCustomerNamed_isPreparedWithThatAmount() {
        // "2 kg" goes to the tool as the customer said it (the tool turns it into packs); the model only searched and asked
        List<String> addArguments = new ArrayList<>();
        givenTheServerTools(addArguments, TOMATO_ADD, new ArrayList<>(), ChatActionsTest.SUMMARY);
        when(assistant.answer(anyList(), anyString(), anyList(), any(), isNull())).thenAnswer(call -> {
            recorders.getLast().recordResult("searchProducts", "{\"query\":\"tomato\"}",
                    "{\"status\":\"OK\",\"data\":[{\"name\":\"Tomato\",\"packSize\":\"1 kg\"}]}");
            return "Would you like me to add 2 kg of Tomato to your cart and prepare the order?";
        });

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "I want to buy 2 kg Tomato, order them for me"),
                CUSTOMER);

        assertThat(addArguments).containsExactly("{\"product\":\"Tomato\",\"quantity\":2,\"unit\":\"kg\"}");
        assertThat(reply.action().kind()).isEqualTo("ADD_TO_CART");
    }

    @Test
    void aModelThatFailsAfterTheAddCardWasPrepared_showsTheCard() {
        when(assistant.answer(anyList(), anyString(), anyList(), any(), any())).thenAnswer(call -> {
            if (call.getArgument(4) == null) {
                recorders.getLast().recordResult("prepareMyCartAdd", "{\"product\":\"tomatoes\",\"quantity\":2,\"unit\":\"kg\"}", TOMATO_ADD);
            }
            throw new AssistantUnavailableException(new RuntimeException("Read timed out"));
        });

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "Add 2 kg tomatoes"), CUSTOMER);

        assertThat(reply.answer()).startsWith("Here is the product to add to your cart: 2 x Tomato (1 kg) for ₹72.");
        assertThat(reply.action().kind()).isEqualTo("ADD_TO_CART");
        assertThat(savedMessages()).containsExactly("Add 2 kg tomatoes", reply.answer());
    }

    @Test
    void anAddToolThatCannotBeReached_getsTheToolsDownAnswer_andNoCard() {
        when(assistant.answer(anyList(), anyString(), anyList(), any(), isNull())).thenAnswer(call -> {
            recorders.getLast().recordFailure("prepareMyCartAdd", "{\"product\":\"tomatoes\"}");
            return "I added tomatoes to your cart.";
        });

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "Add 2 kg tomatoes"), CUSTOMER);

        assertThat(reply.answer()).isEqualTo(ChatServiceImpl.TOOLS_DOWN_ANSWER);
        assertThat(reply.action()).isNull();
        assertThat(reply.grounded()).isFalse();
    }

    /** prepareMyCartAdd's OK for one product, as mcp-server-service's CartAddPreview */
    private static String addPrepared(long productId, String name, String packSize, int packs, String price, int newQuantity) {
        return "{\"status\":\"OK\",\"message\":\"Adding prepared; NOTHING is added yet.\",\"data\":{\"store\":\"Fresh Mart\","
                + "\"items\":[{\"productId\":" + productId + ",\"name\":\"" + name + "\",\"packSize\":\"" + packSize
                + "\",\"quantity\":" + packs + ",\"lineTotal\":\"" + price + "\"}],\"confirm\":{\"productId\":" + productId
                + ",\"quantity\":" + packs + ",\"newQuantity\":" + newQuantity + "}}}";
    }

    private static final String CARROT_REFUSAL = "The cart has products from Fresh Mart, and Organic Carrot is sold by Green "
            + "Basket. A cart can hold the products of one store only: place the order for this cart first, or empty the cart.";
    private static final String CARROT_REFUSED = "{\"status\":\"REFUSED\",\"message\":\"" + CARROT_REFUSAL + "\"}";

    /** prepareMyCartAdd of the last givenTheServerTools, for a test's own answer */
    private ToolCallback addTool;

    /**
     * mcp-server-service's prepareMyCartAdd and prepareMyOrder, answering `added` and `prepared` and noting their
     * arguments, and any `more` tools
     */
    private void givenTheServerTools(List<String> addArguments, String added, List<String> prepareArguments, String prepared,
                                     ToolCallback... more) {
        ToolCallback add = namedTool("prepareMyCartAdd");
        lenient().when(add.call(anyString())).thenAnswer(call -> {
            addArguments.add(call.getArgument(0));
            recorders.getLast().recordResult("prepareMyCartAdd", call.getArgument(0), added);
            return added;
        });
        addTool = add;
        ToolCallback prepare = namedTool("prepareMyOrder");
        lenient().when(prepare.call(anyString())).thenAnswer(call -> {
            prepareArguments.add(call.getArgument(0));
            recorders.getLast().recordResult("prepareMyOrder", call.getArgument(0), prepared);
            return prepared;
        });
        List<ToolCallback> serverTools = new ArrayList<>(List.of(add, prepare));
        serverTools.addAll(List.of(more));
        doAnswer(call -> {
            recorders.add(call.getArgument(0));
            return serverTools;
        }).when(groceryTools).toolsFor(any(), any());
    }

    @Test
    void placeMyOrderGetsTheQuestionHowToPay_whenTheModelOnlyClaimsASummary() {
        // the real model, "Place my order" after adding 3 kg of tomatoes: "Your order summary is ready", without the tool
        List<String> prepareArguments = new ArrayList<>();
        givenPrepareMyOrderAnswers(prepareArguments, "{\"status\":\"CHOOSE\",\"message\":\"Ask the customer how they want "
                + "to pay: cash on delivery, or online (UPI or card) right after confirming.\"}");
        when(assistant.answer(anyList(), anyString(), anyList(), any(), any())).thenReturn("Your order summary is ready, press Confirm.");

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "Place my order"), CUSTOMER);

        assertThat(prepareArguments).containsExactly("{}");
        assertThat(reply.answer()).isEqualTo(ChatServiceImpl.ASK_PAYMENT_ANSWER);
        assertThat(reply.action()).isNull();
        // "Where is my order?" is no request to order
        chatService.sendMessage(new ChatMessageRequest(null, "Where is my order?"), CUSTOMER);
        assertThat(prepareArguments).hasSize(1);
    }

    @Test
    void theAnswerToHowToPayGetsTheSummary() {
        // "Place my order" got "How would you like to pay ...?"; then "Cash on delivery", and the model called nothing
        when(chatMemory.get(conversation.getId())).thenReturn(List.of(new UserMessage("Place my order"),
                new AssistantMessage(ChatServiceImpl.ASK_PAYMENT_ANSWER)));
        List<String> prepareArguments = new ArrayList<>();
        givenPrepareMyOrderAnswers(prepareArguments, ChatActionsTest.SUMMARY);
        when(assistant.answer(anyList(), anyString(), anyList(), any(), any())).thenReturn("Your order is ready, press Confirm.");

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "Cash on delivery"), CUSTOMER);

        assertThat(prepareArguments).containsExactly("{\"paymentMethod\":\"cash\"}");
        assertThat(reply.action().kind()).isEqualTo("PLACE_ORDER");
        assertThat(reply.answer()).isEqualTo(ChatServiceImpl.ORDER_SUMMARY_ANSWER);
    }

    /** mcp-server-service's tools: prepareMyCartAdd and prepareMyOrder, which answers `prepared` */
    private void givenPrepareMyOrderAnswers(List<String> prepareArguments, String prepared) {
        ToolCallback prepare = namedTool("prepareMyOrder");
        when(prepare.call(anyString())).thenAnswer(call -> {
            prepareArguments.add(call.getArgument(0));
            recorders.getLast().recordResult("prepareMyOrder", call.getArgument(0), prepared);
            return prepared;
        });
        List<ToolCallback> serverTools = List.of(namedTool("prepareMyCartAdd"), prepare);
        doAnswer(call -> {
            recorders.add(call.getArgument(0));
            return serverTools;
        }).when(groceryTools).toolsFor(any(), any());
    }

    // ----- the checkout in code: "proceed to pay it", "online", ... (the user's conversation, 2026-11-01) -----

    private static final String HOW_TO_PAY = "{\"status\":\"CHOOSE\",\"message\":\"Ask the customer how they want to pay: cash "
            + "on delivery, or online (UPI or card) right after confirming.\"}";
    private static final String ONLINE_SUMMARY = ChatActionsTest.SUMMARY
            .replace("\"payment\":\"Cash on delivery\"", "\"payment\":\"Online (UPI or card), right after confirming\"")
            .replace("\"paymentMethod\":\"COD\"", "\"paymentMethod\":\"ONLINE\"");
    /** prepareMyOrder after the order was placed: the cart is empty, order #207 waits for its online payment */
    private static final String WAITS_FOR_PAYMENT = "{\"status\":\"REFUSED\",\"message\":\"Nothing new to order: order #207 "
            + "(₹138) is placed and waits for the customer's online payment: they pay it with the Pay Now button.\","
            + "\"data\":{\"orderNumber\":207,\"payable\":\"₹138\",\"paymentUrl\":\"/orders/207\"}}";
    private static final String ONLINE_SUMMARY_ANSWER = ChatServiceImpl.ORDER_SUMMARY_ANSWER + " " + ChatServiceImpl.PAY_NOW_AFTER_CONFIRM;

    /**
     * mcp-server-service's prepareMyOrder as it answers: the question how to pay without a way to pay, else the summary
     * with it (or `afterTheOrder` once the order is placed); and prepareMyCartAdd, which must not be called
     */
    private ToolCallback givenTheOrderTool(List<String> prepareArguments, String afterTheOrder) {
        ToolCallback prepare = namedTool("prepareMyOrder");
        lenient().when(prepare.call(anyString())).thenAnswer(call -> {
            String arguments = call.getArgument(0);
            prepareArguments.add(arguments);
            String result = afterTheOrder != null ? afterTheOrder
                    : arguments.contains("online") ? ONLINE_SUMMARY : arguments.contains("cash") ? ChatActionsTest.SUMMARY : HOW_TO_PAY;
            recorders.getLast().recordResult("prepareMyOrder", arguments, result);
            return result;
        });
        ToolCallback add = namedTool("prepareMyCartAdd");
        lenient().doAnswer(call -> {
            recorders.add(call.getArgument(0));
            return List.of(add, prepare);
        }).when(groceryTools).toolsFor(any(), any());
        return add;
    }

    @Test
    void theUsersConversation_proceedToPay_thenOnline_getsTheSummaryWithConfirm_withoutTheModel() {
        // 1, 2: before, both got the cart read again and the same text, and no summary
        List<String> prepareArguments = new ArrayList<>();
        givenTheOrderTool(prepareArguments, null);

        ChatReplyResponse asked = chatService.sendMessage(new ChatMessageRequest(null, "proceed to pay it"), CUSTOMER);
        assertThat(asked.answer()).isEqualTo(ChatServiceImpl.ASK_PAYMENT_ANSWER);
        assertThat(asked.action()).isNull();
        assertThat(conversation.getCheckoutStep()).isEqualTo(CheckoutStep.AWAITING_PAYMENT_METHOD);

        ChatReplyResponse summary = chatService.sendMessage(new ChatMessageRequest(null, "online"), CUSTOMER);
        assertThat(summary.answer()).isEqualTo(ONLINE_SUMMARY_ANSWER);
        assertThat(summary.action().kind()).isEqualTo(ChatAction.PLACE_ORDER);
        assertThat(summary.action().paymentMethod()).isEqualTo("ONLINE");
        assertThat(summary.paymentUrl()).isNull();          // no order yet: Pay Now comes after the Confirm click
        assertThat(conversation.getCheckoutStep()).isEqualTo(CheckoutStep.AWAITING_CONFIRMATION);

        assertThat(prepareArguments).containsExactly("{}", "{\"paymentMethod\":\"online\"}");
        verify(assistant, never()).answer(anyList(), anyString(), anyList(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Online.", "Pay online.", "I want to pay using online payment", "Proceed to pay via online",
            "Proceed with online payment.", "I want to purchase everything in my cart using online payment.",
            "Pay for all my cart items online.", "Let's complete the payment online."})
    void theWaysToSayOnline_allMakeTheOnlineSummary(String answer) {
        // 3, 4, 5
        conversation.setCheckoutStep(CheckoutStep.AWAITING_PAYMENT_METHOD);
        List<String> prepareArguments = new ArrayList<>();
        givenTheOrderTool(prepareArguments, null);

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, answer), CUSTOMER);

        assertThat(prepareArguments).containsExactly("{\"paymentMethod\":\"online\"}");
        assertThat(reply.action().paymentMethod()).isEqualTo("ONLINE");
        verify(assistant, never()).answer(anyList(), anyString(), anyList(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"COD", "Cash on delivery.", "I prefer cash payment.", "Pay when the order is delivered.",
            "I want to pay after delivery."})
    void theWaysToSayCashOnDelivery_allMakeTheCashSummary(String answer) {
        // 6: the existing cash-on-delivery order, no payment link
        conversation.setCheckoutStep(CheckoutStep.AWAITING_PAYMENT_METHOD);
        List<String> prepareArguments = new ArrayList<>();
        givenTheOrderTool(prepareArguments, null);

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, answer), CUSTOMER);

        assertThat(prepareArguments).containsExactly("{\"paymentMethod\":\"cash\"}");
        assertThat(reply.answer()).isEqualTo(ChatServiceImpl.ORDER_SUMMARY_ANSWER);
        assertThat(reply.action().paymentMethod()).isEqualTo("COD");
        assertThat(reply.paymentUrl()).isNull();
    }

    @Test
    void paymentWords_areNeverAProduct() {
        // 13: "The shop has no product called "by online payment"" (the user's conversation)
        List<String> prepareArguments = new ArrayList<>();
        ToolCallback add = givenTheOrderTool(prepareArguments, null);

        ChatReplyResponse reply = chatService.sendMessage(
                new ChatMessageRequest(null, "i want to buy them by online payment. can you proceed payment"), CUSTOMER);

        assertThat(reply.action().paymentMethod()).isEqualTo("ONLINE");
        verify(add, never()).call(anyString());
    }

    @Test
    void anotherWayToPay_underTheSummary_makesANewSummary() {
        // 8: online first, then cash on delivery (the order does not exist yet: nothing to undo)
        conversation.setCheckoutStep(CheckoutStep.AWAITING_CONFIRMATION);
        List<String> prepareArguments = new ArrayList<>();
        givenTheOrderTool(prepareArguments, null);

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "Cash on delivery instead"), CUSTOMER);

        assertThat(prepareArguments).containsExactly("{\"paymentMethod\":\"cash\"}");
        assertThat(reply.action().paymentMethod()).isEqualTo("COD");
        assertThat(conversation.getCheckoutStep()).isEqualTo(CheckoutStep.AWAITING_CONFIRMATION);
    }

    @Test
    void payingAgainAfterTheOrderWasPlaced_ordersNothingNew_andGivesPayNowForThatOrder() {
        // 7: Confirm placed order #207 (the website sends it with the next message), then "Proceed to pay"
        conversation.setCheckoutStep(CheckoutStep.AWAITING_CONFIRMATION);
        List<String> prepareArguments = new ArrayList<>();
        givenTheOrderTool(prepareArguments, WAITS_FOR_PAYMENT);
        ChatMessageRequest request = new ChatMessageRequest(null, "Proceed to pay", null,
                List.of(new ChatMessageRequest.ConfirmedCard("PLACE_ORDER", 207L)));

        ChatReplyResponse reply = chatService.sendMessage(request, CUSTOMER);

        assertThat(reply.answer()).isEqualTo("Order #207 is already placed and waits for your online payment of ₹138. Please "
                + "press Pay Now to pay it; nothing new was ordered.");
        assertThat(reply.action()).isNull();                          // no new summary, so no second order
        assertThat(reply.paymentUrl()).isEqualTo("/orders/207");      // order-service's own, for Pay Now
        assertThat(reply.links()).extracting(ChatLink::path).containsExactly("/orders/207");
        assertThat(conversation.getCheckoutStep()).isNull();
    }

    @Test
    void aNewCheckoutAfterAPlacedOrder_asksHowToPayAgain() {
        // found live by the browser test: "Cash on Delivery", Confirm (order #207), a new cart, "proceed with checkout"
        // got a cash summary at once, from the way to pay of the order already placed
        when(chatMemory.get(conversation.getId())).thenReturn(List.of(new UserMessage("Cash on Delivery"),
                new AssistantMessage(ChatServiceImpl.ORDER_SUMMARY_ANSWER), new AssistantMessage("Order #207 placed with the Confirm button.")));
        List<String> prepareArguments = new ArrayList<>();
        givenTheOrderTool(prepareArguments, null);

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "proceed with checkout"), CUSTOMER);

        assertThat(prepareArguments).containsExactly("{}");
        assertThat(reply.answer()).isEqualTo(ChatServiceImpl.ASK_PAYMENT_ANSWER);
    }

    @Test
    void theWayToPayOfTheMessageBefore_countsWhenNoOrderWasPlacedSince() {
        // "I want to buy 1 Harpic ..., cash on delivery", Confirm on the ADD card (no order yet), "Please place my order now"
        when(chatMemory.get(conversation.getId())).thenReturn(List.of(
                new UserMessage("I want to buy 1 Harpic Toilet Cleaner. Please order it for me, cash on delivery."),
                new AssistantMessage("Here is the product to add to your cart: 1 x Harpic Toilet Cleaner (500 ml) for ₹99.75.")));
        List<String> prepareArguments = new ArrayList<>();
        givenTheOrderTool(prepareArguments, null);

        chatService.sendMessage(new ChatMessageRequest(null, "Please place my order now"), CUSTOMER);

        assertThat(prepareArguments).containsExactly("{\"paymentMethod\":\"cash\"}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"never mind", "cancel", "No, don't place the order"})
    void callingOffTheCheckout_ordersNothing(String answer) {
        // 12 (before the order exists)
        conversation.setCheckoutStep(CheckoutStep.AWAITING_PAYMENT_METHOD);

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, answer), CUSTOMER);

        assertThat(reply.answer()).isEqualTo(ChatServiceImpl.CHECKOUT_CANCELLED_ANSWER);
        assertThat(conversation.getCheckoutStep()).isNull();
        verify(groceryTools, never()).toolsFor(any(), any());
        verify(assistant, never()).answer(anyList(), anyString(), anyList(), any(), any());
    }

    @Test
    void cancellingAnOrderThatExists_isNoCheckoutWord_theModelPreparesIt() {
        conversation.setCheckoutStep(CheckoutStep.AWAITING_PAYMENT_METHOD);
        when(assistant.answer(anyList(), anyString(), anyList(), any(), isNull())).thenReturn("Which order do you mean?");

        chatService.sendMessage(new ChatMessageRequest(null, "Cancel my last order"), CUSTOMER);

        verify(assistant).answer(anyList(), eq("Cancel my last order"), anyList(), any(), isNull());
        assertThat(conversation.getCheckoutStep()).isNull();
    }

    @Test
    void eachConversationHasItsOwnCheckout() {
        // 14: customer 4 was asked how to pay; customer 5's "online" in their own conversation is no answer to it
        conversation.setCheckoutStep(CheckoutStep.AWAITING_PAYMENT_METHOD);
        Conversation others = Conversation.start(5L, Instant.parse("2026-10-26T10:00:00Z"));
        when(conversationService.startOrContinue(UUID.fromString(others.getId()), 5L)).thenReturn(others);
        when(assistant.answer(anyList(), eq("online"), anyList(), any(), isNull())).thenReturn("What would you like to do online?");

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(UUID.fromString(others.getId()), "online"),
                new UserContext(5L, "CUSTOMER", "customer5-jwt"));

        assertThat(reply.answer()).isEqualTo("What would you like to do online?");
        assertThat(others.getCheckoutStep()).isNull();
        assertThat(conversation.getCheckoutStep()).isEqualTo(CheckoutStep.AWAITING_PAYMENT_METHOD);
    }

    @Test
    void theShopsUnreachable_saysSo_andTheQuestionStaysOpen() {
        conversation.setCheckoutStep(CheckoutStep.AWAITING_PAYMENT_METHOD);
        ToolCallback prepare = namedTool("prepareMyOrder");
        when(prepare.call(anyString())).thenAnswer(call -> {
            recorders.getLast().recordFailure("prepareMyOrder", call.getArgument(0));
            return "{\"status\":\"SERVICE_UNAVAILABLE\"}";
        });
        doAnswer(call -> {
            recorders.add(call.getArgument(0));
            return List.of(prepare);
        }).when(groceryTools).toolsFor(any(), any());

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "Online"), CUSTOMER);

        assertThat(reply.answer()).isEqualTo(ChatServiceImpl.TOOLS_DOWN_ANSWER);
        assertThat(conversation.getCheckoutStep()).isEqualTo(CheckoutStep.AWAITING_PAYMENT_METHOD);
    }

    @Test
    @SuppressWarnings("unchecked")
    void theModelDoesNotGetTheApologyOfAnEarlierAnswer() {
        // the real model copied it as its answer to the next message
        when(chatMemory.get(conversation.getId())).thenReturn(List.of(new UserMessage("Remove Dettol Soap from my cart"),
                new AssistantMessage(ChatServiceImpl.NO_CART_CHANGE_ANSWER), new UserMessage("I want to buy Harpic Toilet Cleaner"),
                new AssistantMessage(ChatServiceImpl.FALLBACK_ANSWER), new UserMessage("What is the price of Amul Butter?"),
                new AssistantMessage("Amul Butter costs ₹56.")));
        when(assistant.answer(anyList(), anyString(), anyList(), any(), isNull())).thenReturn("How would you like to pay?");

        chatService.sendMessage(new ChatMessageRequest(null, "Cash on delivery"), CUSTOMER);

        // the turns with an apology are left out completely: their questions would look like open requests
        ArgumentCaptor<List<Message>> history = ArgumentCaptor.forClass(List.class);
        verify(assistant).answer(history.capture(), anyString(), anyList(), any(), isNull());
        assertThat(history.getValue()).extracting(Message::getText)
                .containsExactly("What is the price of Amul Butter?", "Amul Butter costs ₹56.");
    }

    @Test
    void withoutACardAModelThatFailsIsStillAnError() {
        when(assistant.answer(anyList(), anyString(), anyList(), any(), isNull()))
                .thenThrow(new AssistantUnavailableException(new RuntimeException("Connection refused")));

        assertThatThrownBy(() -> chatService.sendMessage(new ChatMessageRequest(null, "Hi"), CUSTOMER))
                .isInstanceOf(AssistantUnavailableException.class);
    }

    @Test
    void anOrderSummaryComesWithItsConfirmCard_andAnAnswerThatSaysPlacedIsReplaced() {
        when(assistant.answer(anyList(), anyString(), anyList(), any(), any())).thenAnswer(call -> {
            recorders.getLast().recordResult("prepareMyOrder", "{\"paymentMethod\":\"cash\"}", ChatActionsTest.SUMMARY);
            return "Your order has been placed! You will pay ₹102 in cash.";
        });

        // with the tomatoes: not only about the checkout, so the model answers (a plain "Place my order" is code's)
        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "Place my order with the tomatoes, cash on delivery"), CUSTOMER);

        assertThat(reply.answer()).isEqualTo(ChatServiceImpl.ORDER_SUMMARY_ANSWER);
        assertThat(reply.grounded()).isTrue();
        assertThat(reply.action().kind()).isEqualTo("PLACE_ORDER");
        assertThat(reply.action().payable()).isEqualTo("₹102");
        assertThat(reply.action().checkoutFingerprint()).isEqualTo("fp-ready-cart-001");
    }

    @Test
    void aYesThatTheModelTakesForAPlacedOrderGetsPressConfirm() {
        // "yes" under a summary: the model claimed the order without any tool, and then quoted amounts from memory
        when(assistant.answer(anyList(), anyString(), anyList(), any(), any()))
                .thenReturn("Great, your order has been placed!")
                .thenReturn("Your order of ₹138 is on its way.");

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "yes"), CUSTOMER);

        assertThat(reply.answer()).isEqualTo(ChatServiceImpl.PRESS_CONFIRM_ANSWER);
        assertThat(reply.action()).isNull();
        verify(assistant, times(2)).answer(anyList(), anyString(), anyList(), any(), any());
    }

    @Test
    void aCancellationSummaryKeepsItsCard_andAFalseCancelledGetsPressConfirm() {
        when(assistant.answer(anyList(), anyString(), anyList(), any(), any())).thenAnswer(call -> {
            recorders.getLast().recordResult("prepareMyOrderCancellation", "{\"orderNumber\":198}", ChatActionsTest.CANCELLATION);
            return "Done, order #198 has been cancelled.";
        });

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "Cancel order 198"), CUSTOMER);

        assertThat(reply.answer()).isEqualTo(ChatServiceImpl.CANCEL_SUMMARY_ANSWER);
        assertThat(reply.action().kind()).isEqualTo("CANCEL_ORDER");
        assertThat(reply.action().orderId()).isEqualTo(198L);

        // "yes" under the summary: no tool, a claim of a cancellation
        when(assistant.answer(anyList(), anyString(), anyList(), any(), any())).thenReturn("Your order has been cancelled.");
        assertThat(chatService.sendMessage(new ChatMessageRequest(null, "yes"), CUSTOMER).answer())
                .isEqualTo(ChatServiceImpl.PRESS_CONFIRM_CANCEL_ANSWER);
    }

    @Test
    void anAnswerThatSendsTheCustomerToAMissingConfirmButtonIsAskedAgain_thenGetsTheNoSummaryAnswer() {
        // the real model, for "Cancel my last order" right after ordering, without any tool
        when(assistant.answer(anyList(), anyString(), anyList(), any(), any()))
                .thenReturn("I found your last order. It will be cancelled. Press Confirm to cancel it.");

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "Cancel my last order"), CUSTOMER);

        assertThat(reply.answer()).isEqualTo(ChatServiceImpl.NO_SUMMARY_ANSWER);
        assertThat(reply.action()).isNull();
        ArgumentCaptor<String> correction = ArgumentCaptor.forClass(String.class);
        verify(assistant, times(2)).answer(anyList(), anyString(), anyList(), any(), correction.capture());
        assertThat(correction.getAllValues().get(1)).contains("prepareMyOrderCancellation");
    }

    @Test
    void anOfferToPlaceTheOrderIsAskedAgain_thenGetsTheNoSummaryAnswer() {
        when(assistant.answer(anyList(), anyString(), anyList(), any(), any()))
                .thenReturn("I can place the order with cash on delivery now.");

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "Cash on delivery"), CUSTOMER);

        assertThat(reply.answer()).isEqualTo(ChatServiceImpl.NO_SUMMARY_ANSWER);
        verify(assistant, times(2)).answer(anyList(), anyString(), anyList(), any(), any());
    }

    @Test
    void aGoodAnswerKeepsItsCard_andOtherAnswersHaveNone() {
        when(assistant.answer(anyList(), anyString(), anyList(), any(), isNull())).thenAnswer(call -> {
            recorders.getLast().recordResult("prepareMyOrder", "{\"paymentMethod\":\"cash\"}", ChatActionsTest.SUMMARY);
            return "Here is your order: 2 x Tomato, ₹102 in cash on delivery. Please check it and press Confirm.";
        });

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "Place my order with the tomatoes"), CUSTOMER);

        assertThat(reply.answer()).startsWith("Here is your order");
        assertThat(conversation.getCheckoutStep()).isEqualTo(CheckoutStep.AWAITING_CONFIRMATION);   // the summary waits for Confirm
        assertThat(reply.action()).isNotNull();

        when(assistant.answer(anyList(), anyString(), anyList(), any(), isNull())).thenAnswer(call -> toolResultAndAnswer(BUTTER_RESULT, "Amul Butter costs ₹56."));
        assertThat(chatService.sendMessage(new ChatMessageRequest(null, "Price of Amul butter?"), CUSTOMER).action()).isNull();
    }

    @Test
    void aRemovalTheModelOnlyClaims_getsTheCardFromTheCode_andNothingIsRemoved() {
        // the user's words; the real model wrote "I've removed ... Your new total is ₹641.25." without a tool
        List<String> prepareArguments = new ArrayList<>();
        ToolCallback add = givenTheCartAndTheRemovalTool(prepareArguments, ChatActionsTest.REMOVAL);
        when(assistant.answer(anyList(), anyString(), anyList(), any(), isNull()))
                .thenReturn("I've removed Harpic Toilet Cleaner from your cart. Your new total is ₹641.25.");

        ChatReplyResponse reply = chatService.sendMessage(
                new ChatMessageRequest(null, "Can you remove Harpic Toilet Cleaner from my cart?"), CUSTOMER);

        assertThat(prepareArguments).containsExactly("{\"product\":\"Harpic Toilet Cleaner\"}");     // a line of the cart
        assertThat(reply.answer()).isEqualTo(ChatServiceImpl.REMOVE_SUMMARY_ANSWER);
        assertThat(reply.action().kind()).isEqualTo("REMOVE_FROM_CART");
        assertThat(reply.action().items()).singleElement().satisfies(line -> assertThat(line.productId()).isEqualTo(119L));
        assertThat(reply.grounded()).isTrue();
        assertThat(reply.links()).contains(new ChatLink("Your cart", "/cart"));
        // the card makes a second attempt unnecessary, and nothing changed the cart
        verify(assistant, times(1)).answer(anyList(), anyString(), anyList(), any(), any());
        verify(add, never()).call(anyString());
    }

    @Test
    void theModelsOwnRemovalSummaryKeepsItsCard_andGetsTheAnswerFromTheCode() {
        List<String> prepareArguments = new ArrayList<>();
        givenTheCartAndTheRemovalTool(prepareArguments, ChatActionsTest.REMOVAL);
        when(assistant.answer(anyList(), anyString(), anyList(), any(), isNull())).thenAnswer(call -> {
            recorders.getLast().recordResult("prepareMyCartRemoval", "{\"product\":\"harpic\"}", ChatActionsTest.REMOVAL);
            return "Please check Harpic Toilet Cleaner (500 ml) below and press Confirm to take it out of your cart.";
        });

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "remove the harpic"), CUSTOMER);

        assertThat(reply.answer()).isEqualTo("Here is the product you want to take out of your cart: Harpic Toilet Cleaner "
                + "(500 ml). Please check it and press Confirm to remove it; nothing is removed before that.");
        assertThat(reply.action().kind()).isEqualTo("REMOVE_FROM_CART");
        assertThat(prepareArguments).isEmpty();         // the code prepared nothing more
    }

    @Test
    void aFalseRemovedNextToTheCardGetsTheCardsOwnWords_andKeepsTheCard() {
        givenTheCartAndTheRemovalTool(new ArrayList<>(), ChatActionsTest.REMOVAL);
        when(assistant.answer(anyList(), anyString(), anyList(), any(), any())).thenAnswer(call -> {
            recorders.getLast().recordResult("prepareMyCartRemoval", "{\"product\":\"harpic\"}", ChatActionsTest.REMOVAL);
            return "Done! Harpic Toilet Cleaner has been removed from your cart.";
        });

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "remove the harpic"), CUSTOMER);

        // the card is there after the first attempt: the answer is written from it, with the product's name, and the
        // model is not asked again
        assertThat(reply.answer()).isEqualTo("Here is the product you want to take out of your cart: Harpic Toilet Cleaner "
                + "(500 ml). Please check it and press Confirm to remove it; nothing is removed before that.");
        assertThat(reply.action().kind()).isEqualTo("REMOVE_FROM_CART");
        verify(assistant, times(1)).answer(anyList(), anyString(), anyList(), any(), any());
    }

    @Test
    void aRemovalClaimForAProductThatIsNotInTheCart_getsNothingWasChanged() {
        List<String> prepareArguments = new ArrayList<>();
        givenTheCartAndTheRemovalTool(prepareArguments, ChatActionsTest.REMOVAL);
        when(assistant.answer(anyList(), anyString(), anyList(), any(), any())).thenReturn("I've removed Dettol Soap from your cart.");

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "Remove Dettol Soap from my cart"), CUSTOMER);

        assertThat(prepareArguments).isEmpty();         // no line of the cart is called that
        assertThat(reply.answer()).isEqualTo(ChatServiceImpl.NO_CART_CHANGE_ANSWER);
        assertThat(reply.action()).isNull();
        assertThat(reply.links()).containsExactly(new ChatLink("Your cart", "/cart"));
        ArgumentCaptor<String> correction = ArgumentCaptor.forClass(String.class);
        verify(assistant, times(2)).answer(anyList(), anyString(), anyList(), any(), correction.capture());
        assertThat(correction.getAllValues().get(1)).contains("prepareMyCartRemoval");
    }

    @Test
    void twoPackSizesOfTheNamedProduct_getAWhichOneQuestionAndNoCard() {
        String choose = "{\"status\":\"CHOOSE\",\"message\":\"The cart has several products that match. Ask the customer "
                + "which one they mean.\",\"data\":{\"options\":[{\"productId\":119,\"name\":\"Harpic Toilet Cleaner\","
                + "\"packSize\":\"500 ml\"},{\"productId\":120,\"name\":\"Harpic Toilet Cleaner\",\"packSize\":\"1 l\"}]}}";
        givenTheCartAndTheRemovalTool(new ArrayList<>(), choose);
        when(assistant.answer(anyList(), anyString(), anyList(), any(), isNull()))
                .thenReturn("I've removed Harpic Toilet Cleaner from your cart.");

        ChatReplyResponse reply = chatService.sendMessage(
                new ChatMessageRequest(null, "Can you remove Harpic Toilet Cleaner from my cart?"), CUSTOMER);

        assertThat(reply.answer()).isEqualTo("Which one do you want to take out of your cart: Harpic Toilet Cleaner (500 ml) "
                + "or Harpic Toilet Cleaner (1 l)?");
        assertThat(reply.action()).isNull();
    }

    @Test
    void aRemovalWithAnAmountIsLeftToTheModel() {
        // "remove 1 Harpic" may mean one pack less
        List<String> prepareArguments = new ArrayList<>();
        givenTheCartAndTheRemovalTool(prepareArguments, ChatActionsTest.REMOVAL);
        when(assistant.answer(anyList(), anyString(), anyList(), any(), isNull()))
                .thenReturn("Should I take all Harpic Toilet Cleaner packs out of your cart, or only one?");

        ChatReplyResponse reply = chatService.sendMessage(
                new ChatMessageRequest(null, "remove 1 Harpic Toilet Cleaner from my cart"), CUSTOMER);

        assertThat(prepareArguments).isEmpty();
        assertThat(reply.answer()).isEqualTo("Should I take all Harpic Toilet Cleaner packs out of your cart, or only one?");
        assertThat(reply.action()).isNull();
    }

    @Test
    void theUsersDoNotWantTomato_getsTheRemovalCardFromTheCode_alsoAfterASlowFirstAttempt() {
        // 2026-10-30, the live log: the model read the cart, then only announced the removal ("I'll remove ...", rejected).
        // The first attempt took 102 s, so there was no second one; and the "do" of "i do not want" (Hindi for two) had
        // stopped the code's own step. The customer got "I'm sorry, I can't give you a reliable answer to that right now."
        chatService = service(4, Duration.ZERO);        // no second attempt, as after that slow first one
        List<String> prepareArguments = new ArrayList<>();
        givenTheTomatoCart(prepareArguments);
        when(assistant.answer(anyList(), anyString(), anyList(), any(), isNull())).thenAnswer(call -> {
            recorders.getLast().recordResult("getMyCart", "{}", TOMATO_CART);
            return "I'll remove Tomato from your cart for you.";
        });

        ChatReplyResponse reply = chatService.sendMessage(
                new ChatMessageRequest(null, "i do not want tomato in my cart. can you remove it please"), CUSTOMER);

        assertThat(prepareArguments).containsExactly("{\"product\":\"Tomato\"}");
        assertThat(reply.answer()).isEqualTo(ChatServiceImpl.REMOVE_SUMMARY_ANSWER);
        assertThat(reply.action().kind()).isEqualTo("REMOVE_FROM_CART");
        assertThat(reply.action().items()).extracting(ChatAction.Line::productId).containsExactly(1L);
        assertThat(reply.grounded()).isTrue();
        verify(assistant, times(1)).answer(anyList(), anyString(), anyList(), any(), any());
    }

    @Test
    void twoProductsToRemove_getOneCardWithBoth_andTheProductToKeepStays() {
        // the model prepared only the first product, but claimed both
        List<String> prepareArguments = new ArrayList<>();
        givenTheTomatoCart(prepareArguments);
        when(assistant.answer(anyList(), anyString(), anyList(), any(), isNull())).thenAnswer(call -> {
            recorders.getLast().recordResult("prepareMyCartRemoval", "{\"product\":\"Tomato\"}", removalOf(1, "Tomato", 2, "₹72"));
            return "Please press Confirm to remove Tomato and Potato from your cart.";
        });

        ChatReplyResponse reply = chatService.sendMessage(
                new ChatMessageRequest(null, "Remove Tomato and Potato from my cart, but keep Onion."), CUSTOMER);

        assertThat(prepareArguments).containsExactly("{\"product\":\"Potato\"}");      // only the one the model missed
        assertThat(reply.action().kind()).isEqualTo("REMOVE_FROM_CART");
        assertThat(reply.action().items()).extracting(ChatAction.Line::productId).containsExactly(1L, 3L);
        assertThat(reply.answer()).isEqualTo("Here are the products you want to take out of your cart: Tomato (1 kg) and "
                + "Potato (1 kg). Please check them and press Confirm to remove them; nothing is removed before that.");
        assertThat(reply.grounded()).isTrue();
    }

    @Test
    void aProductThatIsNotInTheCart_isSaidApartFromTheCard() {
        List<String> prepareArguments = new ArrayList<>();
        givenTheTomatoCart(prepareArguments);
        when(assistant.answer(anyList(), anyString(), anyList(), any(), isNull())).thenAnswer(call -> {
            recorders.getLast().recordResult("prepareMyCartRemoval", "{\"product\":\"tomato\"}", removalOf(1, "Tomato", 2, "₹72"));
            recorders.getLast().recordResult("prepareMyCartRemoval", "{\"product\":\"mango\"}", MANGO_NOT_IN_THE_CART);
            return "Both are ready: press Confirm to remove Tomato and Mango.";
        });

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "Remove Tomato and Mango from my cart"), CUSTOMER);

        assertThat(prepareArguments).isEmpty();
        assertThat(reply.action().items()).extracting(ChatAction.Line::productId).containsExactly(1L);
        assertThat(reply.answer()).isEqualTo("Here is the product you want to take out of your cart: Tomato (1 kg). Please "
                + "check it and press Confirm to remove it; nothing is removed before that.\n"
                + "The cart has no product called \"mango\".");
    }

    @Test
    void dontRemove_preparesNoRemoval() {
        List<String> prepareArguments = new ArrayList<>();
        givenTheTomatoCart(prepareArguments);
        when(assistant.answer(anyList(), anyString(), anyList(), any(), isNull())).thenReturn("Sure, the tomatoes stay in your cart.");

        ChatReplyResponse reply = chatService.sendMessage(
                new ChatMessageRequest(null, "Please don't remove the tomatoes, I still need them"), CUSTOMER);

        assertThat(prepareArguments).isEmpty();
        assertThat(reply.action()).isNull();
        assertThat(reply.answer()).isEqualTo("Sure, the tomatoes stay in your cart.");
    }

    @Test
    void emptyMyCart_theModelOnlyClaimsIt_getsTheCardFromTheCode() {
        ToolCallback clear = namedTool("prepareMyCartClear");
        String cart = "{\"status\":\"OK\",\"message\":\"Emptying prepared; NOTHING is removed yet.\",\"data\":{\"store\":"
                + "\"Fresh Mart\",\"items\":[{\"name\":\"Tomato\",\"packSize\":\"1 kg\",\"quantity\":2,\"lineTotal\":\"₹72\"}],"
                + "\"payable\":\"₹102\"}}";
        when(clear.call(anyString())).thenAnswer(call -> {
            recorders.getLast().recordResult("prepareMyCartClear", "{}", cart);
            return cart;
        });
        ToolCallback add = namedTool("prepareMyCartAdd");
        doAnswer(call -> {
            recorders.add(call.getArgument(0));
            return List.of(clear, add);
        }).when(groceryTools).toolsFor(any(), any());
        when(assistant.answer(anyList(), anyString(), anyList(), any(), isNull())).thenReturn("Done! Your cart has been emptied.");

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "Empty my cart"), CUSTOMER);

        assertThat(reply.answer()).isEqualTo(ChatServiceImpl.CLEAR_SUMMARY_ANSWER);
        assertThat(reply.action().kind()).isEqualTo("CLEAR_CART");
        assertThat(reply.action().payable()).isEqualTo("₹102");
        verify(assistant, times(1)).answer(anyList(), anyString(), anyList(), any(), any());
        verify(add, never()).call(anyString());

        // an empty cart: the tool's own words, and no card
        String alreadyEmpty = "{\"status\":\"NOT_FOUND\",\"message\":\"The cart is empty already, so there is nothing to remove.\"}";
        doAnswer(call -> {
            recorders.getLast().recordResult("prepareMyCartClear", "{}", alreadyEmpty);
            return alreadyEmpty;
        }).when(clear).call(anyString());
        ChatReplyResponse empty = chatService.sendMessage(new ChatMessageRequest(null, "Empty my cart"), CUSTOMER);
        assertThat(empty.answer()).isEqualTo("The cart is empty already, so there is nothing to remove.");
        assertThat(empty.action()).isNull();
    }

    @Test
    void aNewQuantityKeepsItsCard_andAFalseChangedGetsTheFixedAnswer() {
        String prepared = "{\"status\":\"OK\",\"message\":\"Change prepared; NOTHING is changed yet.\",\"data\":{\"store\":"
                + "\"Fresh Mart\",\"items\":[{\"productId\":1,\"name\":\"Tomato\",\"packSize\":\"1 kg\",\"quantity\":2,"
                + "\"lineTotal\":\"₹72\"}],\"confirm\":{\"productId\":1,\"quantity\":3}}}";
        when(assistant.answer(anyList(), anyString(), anyList(), any(), any())).thenAnswer(call -> {
            recorders.getLast().recordResult("prepareMyCartQuantity", "{\"product\":\"tomatoes\",\"quantity\":3,\"unit\":\"kg\"}", prepared);
            return "I've changed the tomatoes in your cart to 3 kg.";
        });

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "Make it 3 kg of tomatoes"), CUSTOMER);

        assertThat(reply.answer()).isEqualTo(ChatServiceImpl.QUANTITY_SUMMARY_ANSWER);
        assertThat(reply.action().kind()).isEqualTo("SET_CART_QUANTITY");
        assertThat(reply.action().newQuantity()).isEqualTo(3);
    }

    @Test
    void underACartCardTheAnswerIsAlwaysWrittenInCode() {
        String prepared = "{\"status\":\"OK\",\"message\":\"Change prepared; NOTHING is changed yet.\",\"data\":{\"store\":"
                + "\"Fresh Mart\",\"items\":[{\"productId\":1,\"name\":\"Tomato\",\"packSize\":\"1 kg\",\"quantity\":2,"
                + "\"lineTotal\":\"₹72\"}],\"confirm\":{\"productId\":1,\"quantity\":3}}}";
        // the real model's answer: it passes the answer check, but says the change is made before the click
        when(assistant.answer(anyList(), anyString(), anyList(), any(), any())).thenAnswer(call -> {
            recorders.getLast().recordResult("prepareMyCartQuantity", "{\"product\":\"tomatoes\",\"quantity\":3,\"unit\":\"kg\"}", prepared);
            return "Tomatoes are now listed as 3 kg in your cart. Please check it and press Confirm to update the amount.";
        });

        ChatReplyResponse reply = chatService.sendMessage(new ChatMessageRequest(null, "Make it 3 kg of tomatoes"), CUSTOMER);

        assertThat(reply.answer()).isEqualTo(ChatServiceImpl.QUANTITY_SUMMARY_ANSWER);
        assertThat(reply.action().kind()).isEqualTo("SET_CART_QUANTITY");
        assertThat(reply.grounded()).isTrue();
        verify(assistant, times(1)).answer(anyList(), anyString(), anyList(), any(), any());
    }

    /**
     * mcp-server-service's getMyCart (CART_WITH_HARPIC), prepareMyCartRemoval (answering `prepared` and noting its
     * arguments) and addToMyCart, which is returned: no removal may ever call it
     */
    private ToolCallback givenTheCartAndTheRemovalTool(List<String> prepareArguments, String prepared) {
        ToolCallback cart = namedTool("getMyCart");
        lenient().when(cart.call(anyString())).thenAnswer(call -> {
            recorders.getLast().recordResult("getMyCart", "{}", CART_WITH_HARPIC);
            return CART_WITH_HARPIC;
        });
        ToolCallback prepare = namedTool("prepareMyCartRemoval");
        lenient().when(prepare.call(anyString())).thenAnswer(call -> {
            prepareArguments.add(call.getArgument(0));
            recorders.getLast().recordResult("prepareMyCartRemoval", call.getArgument(0), prepared);
            return prepared;
        });
        ToolCallback add = namedTool("prepareMyCartAdd");
        List<ToolCallback> serverTools = List.of(cart, prepare, add);
        doAnswer(call -> {
            recorders.add(call.getArgument(0));
            return serverTools;
        }).when(groceryTools).toolsFor(any(), any());
        return add;
    }

    /**
     * mcp-server-service's getMyCart (TOMATO_CART) and prepareMyCartRemoval, which finds the line by its name like the
     * real tool ("Tomato", "the potatoes") and answers NOT_FOUND for any other product. The arguments of the calls the
     * code makes are noted in prepareArguments; the model's own calls are recorded by each test.
     */
    private void givenTheTomatoCart(List<String> prepareArguments) {
        ToolCallback cart = namedTool("getMyCart");
        lenient().when(cart.call(anyString())).thenAnswer(call -> {
            recorders.getLast().recordResult("getMyCart", "{}", TOMATO_CART);
            return TOMATO_CART;
        });
        ToolCallback prepare = namedTool("prepareMyCartRemoval");
        lenient().when(prepare.call(anyString())).thenAnswer(call -> {
            String arguments = call.getArgument(0);
            String product = arguments.toLowerCase(Locale.ROOT);
            String result = product.contains("tomato") ? removalOf(1, "Tomato", 2, "₹72")
                    : product.contains("onion") ? removalOf(2, "Onion", 1, "₹40")
                    : product.contains("potato") ? removalOf(3, "Potato", 3, "₹85.50")
                    : MANGO_NOT_IN_THE_CART;
            prepareArguments.add(arguments);
            recorders.getLast().recordResult("prepareMyCartRemoval", arguments, result);
            return result;
        });
        List<ToolCallback> serverTools = List.of(cart, prepare, namedTool("prepareMyCartAdd"));
        doAnswer(call -> {
            recorders.add(call.getArgument(0));
            return serverTools;
        }).when(groceryTools).toolsFor(any(), any());
    }

    /** prepareMyCartRemoval's OK for one line of TOMATO_CART */
    private static String removalOf(long productId, String name, int quantity, String lineTotal) {
        return "{\"status\":\"OK\",\"message\":\"Removal prepared; NOTHING is removed yet.\",\"data\":{\"store\":\"Fresh Mart\","
                + "\"items\":[{\"productId\":" + productId + ",\"name\":\"" + name + "\",\"packSize\":\"1 kg\",\"quantity\":"
                + quantity + ",\"lineTotal\":\"" + lineTotal + "\"}],\"confirm\":{\"productId\":" + productId + "}}}";
    }

    /** A tool of mcp-server-service with only a name */
    private static ToolCallback namedTool(String name) {
        ToolCallback tool = mock(ToolCallback.class);
        lenient().when(tool.getToolDefinition())
                .thenReturn(ToolDefinition.builder().name(name).description(name).inputSchema("{}").build());
        return tool;
    }

    /** What the real tools do during assistant.answer(): record their results, then the model answers. */
    private String toolResultAndAnswer(String toolResult, String answer) {
        recorders.getLast().recordResult("searchProducts", "{\"query\":\"amul butter\"}", toolResult);
        return answer;
    }

    @SuppressWarnings("unchecked")
    private List<String> savedMessages() {
        ArgumentCaptor<List<Message>> saved = ArgumentCaptor.forClass(List.class);
        verify(chatMemory).add(eq(conversation.getId()), saved.capture());
        return saved.getValue().stream().map(Message::getText).toList();
    }

    private ChatServiceImpl service(int maxConcurrentChats, Duration retryOnlyWithin) {
        var properties = new ChatProperties(new ByteArrayResource(new byte[0]), new ByteArrayResource(new byte[0]), 10,
                maxConcurrentChats, Duration.ofMillis(50), retryOnlyWithin, Duration.ofDays(30), ZoneId.of("Asia/Kolkata"), false);
        var validator = new AnswerValidator(new SystemPrompt("Rules:\n1. Use only the facts in the tool results."));
        return new ChatServiceImpl(conversationService, chatMemory, groceryTools, assistant, validator, properties, approvalService);
    }
}
