package com.grocery.mcpclient.service.impl;

import com.grocery.mcpclient.assistant.AddReport;
import com.grocery.mcpclient.assistant.AnswerCheck;
import com.grocery.mcpclient.assistant.AnswerLinks;
import com.grocery.mcpclient.assistant.ChatActions;
import com.grocery.mcpclient.assistant.AnswerValidator;
import com.grocery.mcpclient.assistant.PendingAdd;
import com.grocery.mcpclient.assistant.PurchaseSteps;
import com.grocery.mcpclient.assistant.RemovalReport;
import com.grocery.mcpclient.assistant.ShoppingAssistant;
import com.grocery.mcpclient.assistant.UserMessageCleaner;
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
import com.grocery.mcpclient.service.ChatService;
import com.grocery.mcpclient.service.ConversationService;
import com.grocery.mcpclient.tool.ChangeRequest;
import com.grocery.mcpclient.tool.GroceryToolsClient;
import com.grocery.mcpclient.tool.ToolCallRecorder;
import com.grocery.mcpclient.tool.WishlistToolCallback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * One question, step by step:
 *
 * 1. Clean the message and find (or start) the conversation.
 * 2. Wait for a free answer slot (at most app.chat.max-concurrent-chats questions at the same time). When the last answer
 *    asked how much of a product the customer wants (PendingAdd), their answer ("3 kg", "never mind") is handled in
 *    code for exactly that product, without the model (answerThePendingAdd). The same for the checkout ("Proceed to
 *    pay", then "Online"; CheckoutStep): the order summary is prepared in code (answerTheCheckout).
 * 3. Load the last messages of the conversation (without the turns that got an apology, see withoutFailedTurns) and the
 *    tools of mcp-server-service (in the shop also getWishlist, with the wishlist the page sent: it is kept in the
 *    browser, see WishlistToolCallback). Before that, the cards the customer confirmed since their last message become a
 *    line of the conversation ("Order #207 placed with the Confirm button."): the website places or cancels the order
 *    itself, so the model would not know it otherwise.
 * 4. Ask the model. Check the answer against the tool results (AnswerValidator).
 * 5. No tool changes data: every change (adding to the cart, a new amount, taking out, emptying, ordering, cancelling) is
 *    a card that the customer confirms with its button, in their own browser. So when the customer's words clearly ask
 *    for a card that the model did not prepare, the code prepares it itself through the same tools and checks (they
 *    only read): the card to empty the cart, to take out the products of the cart they named, to add the products they
 *    named with the amounts they said, the order summary. Its answer is then written in code (RemovalReport, AddReport:
 *    the products on the card, then the shop's reason for each product that is not on it), and the model is not asked
 *    a second time.
 * 6. Otherwise, if the check fails: ask once more, telling the model what was wrong (only if the first attempt was quick).
 * 7. If a tool could not be reached, or the answer still fails the check: give a fixed, honest answer instead (with a
 *    card: what the card is; for products that could not be prepared: the shop's own reason).
 * 8. Save the question and the answer in the conversation and return the answer, with links to the pages that fit
 *    it (AnswerLinks, from the tool results).
 *
 * Deliberately no @Transactional: a database transaction must not stay open while the model thinks for seconds.
 */
@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    static final String FALLBACK_ANSWER = "I'm sorry, I can't give you a reliable answer to that right now.";
    static final String TOOLS_DOWN_ANSWER =
            "I'm sorry, I can't reach the shop's information right now. Please try again in a few minutes.";
    static final String ORDER_SUMMARY_ANSWER =
            "Here is the summary of your order. Please check it and press Confirm to place the order; nothing is ordered before that.";
    static final String CANCEL_SUMMARY_ANSWER =
            "Here is the order you want to cancel. Please check it and press Confirm to cancel it; nothing is cancelled before that.";
    static final String REMOVE_SUMMARY_ANSWER = "Here is the product you want to take out of your cart. Please check it and "
            + "press Confirm to remove it; nothing is removed before that.";
    static final String QUANTITY_SUMMARY_ANSWER = "Here is the change to your cart. Please check it and press Confirm to make "
            + "it; nothing is changed before that.";
    static final String CLEAR_SUMMARY_ANSWER = "Here is your cart. Please check it and press Confirm to empty it; nothing is "
            + "removed before that.";
    /** For an answer that said the cart changed ("I've added it to your cart"): only the customer's click changes it */
    static final String NO_CART_CHANGE_ANSWER = "Nothing in your cart was changed: I only prepare a change, and your click on "
            + "Confirm under it makes it. Please ask me again, or use the cart page.";
    /** For an answer that claimed an order was placed (e.g. after the customer typed "yes" under a summary) */
    static final String PRESS_CONFIRM_ANSWER = "I can't place orders myself. To order, please press Confirm under the order "
            + "summary; nothing is ordered before that. If you don't see a summary, ask me to place your order.";
    private static final String CART_TOOL = "getMyCart";
    /** The cards that change the cart: their answer is always written in code (cartCardSentence) */
    private static final Set<String> CART_CARDS = Set.of(ChatAction.ADD_TO_CART, ChatAction.REMOVE_FROM_CART,
            ChatAction.SET_CART_QUANTITY, ChatAction.CLEAR_CART);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** "How would you like to pay", "how do you want to pay": the question the customer's next message may answer */
    private static final Pattern HOW_TO_PAY = Pattern.compile("\\bhow\\s+(?:would\\s+you\\s+like|do\\s+you\\s+want|you\\s+want)\\s+to\\s+pay\\b",
            Pattern.CASE_INSENSITIVE);
    /** Under an online order's summary: what comes after the click */
    static final String PAY_NOW_AFTER_CONFIRM = "After you confirm, the Pay Now button takes you to the online payment.";
    /** The end of the line for a card the customer confirmed: "Order #207 placed with the Confirm button." (confirmedLine) */
    private static final String WITH_THE_CONFIRM_BUTTON = " with the Confirm button.";
    /** "cancel", "no", "never mind" during a checkout */
    static final String CHECKOUT_CANCELLED_ANSWER = "Okay, nothing was ordered. Your cart stays as it is.";
    /** When the customer asked to order but has not said how to pay (prepareMyOrder asks) */
    static final String ASK_PAYMENT_ANSWER = "How would you like to pay: cash on delivery, or online (UPI or card) right "
            + "after confirming?";
    /** For an answer that sent the customer to a summary that is not there ("Press Confirm to cancel it." without a card) */
    static final String NO_SUMMARY_ANSWER = "I couldn't prepare a summary for that right now. If you see a summary with a "
            + "Confirm button above, press Confirm there; otherwise please ask me again.";
    /**
     * The fixed answers that say nothing about the question: kept for the customer, never shown to the model again (it
     * copied them as its next answer)
     */
    private static final Set<String> APOLOGIES = Set.of(FALLBACK_ANSWER, TOOLS_DOWN_ANSWER, NO_SUMMARY_ANSWER, NO_CART_CHANGE_ANSWER);
    /** For an answer that claimed an order was cancelled */
    static final String PRESS_CONFIRM_CANCEL_ANSWER = "I can't cancel orders myself. To cancel, please press Confirm under the "
            + "cancellation summary; nothing is cancelled before that. If you don't see a summary, ask me to cancel your order.";

    private final ConversationService conversationService;
    private final ChatMemory chatMemory;
    private final GroceryToolsClient groceryTools;
    private final ShoppingAssistant assistant;
    private final AnswerValidator answerValidator;
    private final ChatProperties chatProperties;
    private final ApprovalService approvalService;
    private final Semaphore answerSlots;

    public ChatServiceImpl(ConversationService conversationService, ChatMemory chatMemory, GroceryToolsClient groceryTools,
                           ShoppingAssistant assistant, AnswerValidator answerValidator, ChatProperties chatProperties,
                           ApprovalService approvalService) {
        this.conversationService = conversationService;
        this.chatMemory = chatMemory;
        this.groceryTools = groceryTools;
        this.assistant = assistant;
        this.answerValidator = answerValidator;
        this.chatProperties = chatProperties;
        this.approvalService = approvalService;
        this.answerSlots = new Semaphore(chatProperties.maxConcurrentChats(), true);
    }

    @Override
    public ChatReplyResponse sendMessage(ChatMessageRequest request, UserContext user) {
        String question = UserMessageCleaner.clean(request.message());
        Conversation conversation = conversationService.startOrContinue(request.conversationId(), user.userId());

        acquireAnswerSlot();
        try {
            return answer(conversation, question, user.audience(), request.wishlist(), request.confirmed());
        } finally {
            answerSlots.release();
        }
    }

    private ChatReplyResponse answer(Conversation conversation, String question, Audience audience, List<String> wishlist,
                                     List<ChatMessageRequest.ConfirmedCard> confirmed) {
        long startedAt = System.nanoTime();
        // Only a customer's chat shows cards. Without this line the model, seeing only its own summary, answered "cancel
        // my last order" as if nothing had been ordered.
        if (audience == Audience.CUSTOMER && confirmed != null && !confirmed.isEmpty()) {
            chatMemory.add(conversation.getId(), confirmed.stream().map(ChatServiceImpl::confirmedLine).toList());
            if (confirmed.stream().anyMatch(card -> ChatAction.PLACE_ORDER.equals(card.kind()))) {
                conversation.setCheckoutStep(null);     // the order is placed: order-service has it from here on
            }
        }
        // "3kg" after "How much Tomato would you like to add?": answered in code, for exactly that product
        if (audience == Audience.CUSTOMER && conversation.hasPendingAdd()) {
            ChatReplyResponse reply = answerThePendingAdd(conversation, question, wishlist, startedAt);
            if (reply != null) {
                return reply;
            }
        }
        // "Proceed to pay", then "Online": the checkout's steps are taken in code, never left to the model
        if (audience == Audience.CUSTOMER) {
            ChatReplyResponse reply = answerTheCheckout(conversation, question, wishlist, startedAt);
            if (reply != null) {
                return reply;
            }
        }
        List<Message> history = withoutFailedTurns(chatMemory.get(conversation.getId()));
        // The add tool prepares nothing unless the customer's own words ask for it, with the amount they said; the order
        // summary takes only a payment method the customer named
        String previousQuestion = previousQuestion(history);
        boolean changesAsked = audience == Audience.CUSTOMER && ChangeRequest.asked(question, previousQuestion);
        String customerWords = audience == Audience.CUSTOMER ? question + "\n" + Objects.toString(previousQuestion, "") : null;
        ToolCallRecorder recorder = new ToolCallRecorder(audience == Audience.CUSTOMER ? question : null, customerWords, changesAsked);
        List<ToolCallback> tools = toolsFor(recorder, audience, wishlist);

        String answer = ask(history, question, tools, audience, null, recorder);
        AnswerCheck check = answerValidator.check(answer, recorder);
        // An attempt that claimed an order was placed or cancelled (e.g. after "yes" under a summary) gets the truth in the end
        boolean claimedAPlacedOrder = check.problems().contains(AnswerValidator.PLACED_ORDER_PROBLEM);
        boolean claimedACancellation = check.problems().contains(AnswerValidator.CANCELLED_ORDER_PROBLEM);
        // a summary that is not there, or an offer to place or cancel: either way the customer needs a summary card
        boolean claimedASummary = check.problems().contains(AnswerValidator.SUMMARY_PROBLEM)
                || check.problems().contains(AnswerValidator.OFFER_PROBLEM);
        // a cart change ("I've added it to your cart"): the truth is that nothing changed
        boolean claimedACartChange = check.problems().contains(AnswerValidator.CART_CHANGE_PROBLEM);
        // "Empty my cart", "Can you remove Harpic Toilet Cleaner from my cart?", "Can you add Banana Robusta 1 dozen to my
        // cart?": the card, prepared in code when the model prepared none. Its answer replaces the model's, so the model
        // is then not asked a second time.
        String cardStep = cardStepIfAsked(audience, changesAsked, question, check.passed(), recorder, tools);
        boolean retried = false;
        if (cardStep == null && !check.passed() && !recorder.hasFailures()
                && elapsed(startedAt).compareTo(chatProperties.retryOnlyWithin()) < 0) {
            // Safe to repeat: no tool changes data, and the add tool gives the same result again for the same request
            log.info("Answer rejected ({}); asking the model once more", String.join("; ", check.problems()));
            retried = true;
            answer = ask(history, question, tools, audience, check.correction(audience), recorder);
            check = answerValidator.check(answer, recorder);
            claimedAPlacedOrder |= check.problems().contains(AnswerValidator.PLACED_ORDER_PROBLEM);
            claimedACancellation |= check.problems().contains(AnswerValidator.CANCELLED_ORDER_PROBLEM);
            claimedASummary |= check.problems().contains(AnswerValidator.SUMMARY_PROBLEM)
                    || check.problems().contains(AnswerValidator.OFFER_PROBLEM);
            claimedACartChange |= check.problems().contains(AnswerValidator.CART_CHANGE_PROBLEM);
            cardStep = productsReport(recorder);
            if (cardStep == null) {
                cardStep = cartCardSentence(recorder);
            }
        }

        String orderStep = orderStepIfAsked(audience, question, customerWords, history, recorder, tools);
        boolean grounded = false;
        List<ChatLink> links = List.of();
        // The card with its Confirm button (an order, a cancellation, a change of the cart), from a prepare tool's
        // result (never from the model's text)
        ChatAction action = ChatActions.of(recorder);
        if (cardStep != null || orderStep != null) {
            answer = Stream.of(cardStep, orderStep).filter(Objects::nonNull).collect(Collectors.joining("\n"));
            grounded = true;
        } else if (recorder.hasFailures() || !check.passed()) {
            if (!check.passed()) {
                log.warn("Answer rejected ({}); a fixed answer is used", String.join("; ", check.problems()));
            }
            if (action != null) {
                // What the card is; the summary keeps its Confirm button
                answer = summaryAnswerFor(action, recorder);
                grounded = true;
            } else if (recorder.hasFailures()) {
                answer = TOOLS_DOWN_ANSWER;
            } else if (AddReport.anyAddition(recorder)) {
                // "Organic Carrot is sold by Green Basket ...", "Only 3 packs ... are left": the shop's own reason
                answer = AddReport.of(recorder);
                grounded = true;
            } else {
                // A false "your order is placed / cancelled" or "added to your cart" (in either attempt) gets the truth
                // instead of a vague "I can't answer" (prepareMyOrder asked how to pay, but the model's answer about it
                // failed: the question is the answer)
                answer = claimedAPlacedOrder ? PRESS_CONFIRM_ANSWER
                        : claimedACancellation ? PRESS_CONFIRM_CANCEL_ANSWER
                        : claimedACartChange ? NO_CART_CHANGE_ANSWER
                        : preparedAQuestionHowToPay(recorder) ? ASK_PAYMENT_ANSWER
                        : claimedASummary ? NO_SUMMARY_ANSWER
                        : FALLBACK_ANSWER;
                if (claimedACartChange) {
                    links = List.of(AnswerLinks.CART_PAGE);
                }
            }
        } else {
            grounded = recorder.hasResults();
        }

        // "Add tomoto in my cart?": the add tool found Tomato and asks how much. The conversation remembers the product,
        // so the customer's answer ("3 kg") adds exactly it (answerThePendingAdd); the question is written in code, with
        // the catalog's name.
        PendingAdd pending = audience == Audience.CUSTOMER && !recorder.hasFailures() ? PendingAdd.askedIn(recorder) : null;
        if (pending != null) {
            conversation.setPendingAdd(pending.productId(), pending.productName());
            if (action == null && cardStep == null && orderStep == null) {
                answer = AddReport.of(recorder);
                grounded = true;
            }
        }
        // An order summary (or the question how to pay) prepared on this path too: the checkout goes on from there
        if (audience == Audience.CUSTOMER && !recorder.hasFailures()) {
            CheckoutStep step = checkoutStepAfter(recorder);
            if (step != null) {
                conversation.setCheckoutStep(step);
            }
        }

        chatMemory.add(conversation.getId(), List.of(new UserMessage(question), new AssistantMessage(answer)));
        conversationService.messageAdded(conversation);

        log.info("Conversation {}: answered in {} ms, audience={}, tools={}, card={}, codeStep={}, grounded={}, retried={}",
                conversation.getId(), elapsed(startedAt).toMillis(), audience, recorder.toolNames(),
                action == null ? "none" : action.kind(), cardStep != null || orderStep != null, grounded, retried);
        // Links only for an answer from the tool results: the other fixed answers get none
        if (grounded) {
            links = AnswerLinks.of(answer, recorder, audience);
        }
        return new ChatReplyResponse(UUID.fromString(conversation.getId()), answer, grounded, recorder.toolNames(), links, action,
                null, approvalFor(conversation, answer, action));
    }

    /**
     * The conversation as the model gets it: without the turns that got an apology (FALLBACK_ANSWER and the like). The
     * customer sees every answer again after a reload, but the model does not get the apologies (it copied "I'm sorry, I
     * can't give you a reliable answer ..." as its answer to the next message), and not the question of such a turn
     * either: a question without an answer looks like a request that is still open. "can you add Bru Instant Coffee 100g
     * for me in my cart ?" got the removal of Tomato, because "i do not want tomato in my cart. can you remove it please"
     * just before it had only an apology (the user's conversation, 2026-10-30).
     */
    static List<Message> withoutFailedTurns(List<Message> messages) {
        List<Message> kept = new ArrayList<>();
        for (Message message : messages) {
            if (message instanceof AssistantMessage && APOLOGIES.contains(message.getText())) {
                if (!kept.isEmpty() && kept.getLast() instanceof UserMessage) {
                    kept.removeLast();
                }
                continue;
            }
            kept.add(message);
        }
        return kept;
    }

    /**
     * "Order #207 placed with the Confirm button." / "Order #207 cancelled with the Confirm button.", written here from
     * the card's kind and number (never a text from the page), as the website's line after the click starts.
     */
    static Message confirmedLine(ChatMessageRequest.ConfirmedCard card) {
        String done = ChatAction.CANCEL_ORDER.equals(card.kind()) ? "cancelled" : "placed";
        return new AssistantMessage("Order #" + card.orderId() + " " + done + WITH_THE_CONFIRM_BUTTON);
    }

    /**
     * Whether an order was placed with its Confirm button after the customer's last message (a confirmedLine comes after
     * it): that message's way to pay belonged to that order. Found live (2026-11-01): "Cash on Delivery", Confirm, a new
     * cart, then "proceed with checkout" got a cash summary at once instead of the question how to pay.
     */
    private static boolean orderPlacedSinceTheLastQuestion(List<Message> history) {
        for (int i = history.size() - 1; i >= 0 && !(history.get(i) instanceof UserMessage); i--) {
            String text = Objects.toString(history.get(i).getText(), "");
            if (text.startsWith("Order #") && text.endsWith(" placed" + WITH_THE_CONFIRM_BUTTON)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The customer's answer to "How much Tomato would you like to add?" (Conversation.pendingProductId: the product the
     * add tool found and asked about). It is handled here in code, without the model: a short answer like "3kg" has no
     * product in it, and the model took it for a new question (the user's conversation, 2026-10-31).
     * - An amount ("3kg", "3 kg", "Three kilograms", "I need 3 kilos", "Just 3 kg, please"): prepareMyCartAdd with the
     *   product's id and that amount, through the same tool and checks; the card's Confirm button adds it. Once the card
     *   is there (or the product is gone) nothing waits any more. After the shop's "no" ("Only 3 packs ... are left",
     *   "1.5 kg is not a whole number of packs ...") the question stays open, so the customer can say another amount.
     * - An amount that cannot be right ("0 kg", "-2 kg", "2 or 3 kg"): the question again.
     * - "cancel", "no", "never mind": nothing is added, and nothing waits any more.
     * - Anything else ("what does milk cost?", "add 3 kg onions"): nothing waits any more, and the model answers it
     *   (this method returns null).
     */
    private ChatReplyResponse answerThePendingAdd(Conversation conversation, String question, List<String> wishlist,
                                                  long startedAt) {
        String product = conversation.getPendingProductName();
        ToolCallRecorder recorder = new ToolCallRecorder(question, question, true);
        String answer;
        boolean grounded = false;
        if (ChangeRequest.isAmountReply(question, product)) {
            ChangeRequest.ListedProduct amount = ChangeRequest.amountReply(question, product);
            if (amount == null) {
                answer = "How much " + product + " would you like to add? Please tell me one amount above zero, for "
                        + "example 2 packs.";
            } else {
                answer = prepareThePendingAdd(conversation, amount, recorder, wishlist);
                grounded = recorder.hasResults();
            }
        } else if (ChangeRequest.cancelAsked(question, product)) {
            conversation.clearPendingAdd();
            answer = "Okay, I have not added " + product + ". Nothing in your cart was changed.";
        } else {
            conversation.clearPendingAdd();
            return null;
        }
        return replyFromCode(conversation, question, answer, grounded, recorder, startedAt);
    }

    /**
     * The checkout, step by step in code (CheckoutStep), so it never depends on the model remembering where it is. The
     * user's conversation (2026-11-01): "proceed to pay it", "online" and "Proceed to pay via online" each got the cart
     * read again and the same text, because the model never called prepareMyOrder (no summary, no Confirm, no Pay Now);
     * "i want to buy them by online payment" got "The shop has no product called "by online payment"".
     * - A request to pay for or order the cart, and nothing else ("Proceed to pay", "Pay online", "I want to purchase
     *   everything in my cart using online payment"): prepareMyOrder, with the way to pay the message names.
     * - While the chat waits for the way to pay, or shows the summary: a way to pay ("Online", "COD", "I prefer cash
     *   payment") makes that summary; another one ("COD instead") makes a new summary with it.
     * - "cancel", "no", "never mind" during a checkout: nothing is ordered, and the checkout ends.
     * - Anything else: the checkout ends, and the model answers it (this method returns null).
     * Nothing is placed or paid here: the summary's Confirm button places the order (order-service, with an
     * Idempotency-Key), and an online order is then paid with Pay Now (Razorpay; payment-service checks the payment).
     */
    private ChatReplyResponse answerTheCheckout(Conversation conversation, String question, List<String> wishlist,
                                                long startedAt) {
        boolean inCheckout = conversation.getCheckoutStep() != null;
        boolean onlyCheckout = ChangeRequest.onlyAboutCheckout(question);
        boolean asksToPay = onlyCheckout && ChangeRequest.checkoutAsked(question);
        boolean namesAWayToPay = onlyCheckout && inCheckout && ChangeRequest.paymentOf(question) != null;
        ToolCallRecorder recorder = new ToolCallRecorder();
        String answer;
        if (asksToPay || namesAWayToPay) {
            // the way to pay of this message, else of the one before ("buy Harpic, cash on delivery", Confirm on the add
            // card, "place my order"), but not when an order was placed since that message: the way to pay was its own
            List<Message> history = withoutFailedTurns(chatMemory.get(conversation.getId()));
            String previous = orderPlacedSinceTheLastQuestion(history) ? "" : Objects.toString(previousQuestion(history), "");
            String payment = ChangeRequest.paymentOf(question);
            if (payment == null) {
                payment = ChangeRequest.paymentOf(previous);
            }
            recorder = new ToolCallRecorder(question, question + "\n" + previous, true);
            answer = prepareTheOrder(conversation, payment, recorder, wishlist);
        } else if (inCheckout && ChangeRequest.checkoutCancelAsked(question)) {
            conversation.setCheckoutStep(null);
            answer = CHECKOUT_CANCELLED_ANSWER;
        } else {
            conversation.setCheckoutStep(null);
            return null;
        }
        return replyFromCode(conversation, question, answer, recorder.hasResults() && !recorder.hasFailures(), recorder, startedAt);
    }

    /**
     * prepareMyOrder with the way to pay the customer named (none: the tool asks), through the same tool and checks.
     * Returns the answer, and sets the next step (checkoutStepAfter).
     *
     * @param payment "cash", "online" or null
     */
    private String prepareTheOrder(Conversation conversation, String payment, ToolCallRecorder recorder, List<String> wishlist) {
        ToolCallback prepare = toolNamed(toolsFor(recorder, Audience.CUSTOMER, wishlist), ChatActions.PREPARE_ORDER_TOOL);
        if (prepare == null) {
            return TOOLS_DOWN_ANSWER;
        }
        prepare.call(payment == null ? "{}" : "{\"paymentMethod\":\"" + payment + "\"}");
        if (recorder.hasFailures() || recorder.calls().isEmpty()) {
            return TOOLS_DOWN_ANSWER;                          // nothing changed: the step stays, the customer can ask again
        }
        conversation.setCheckoutStep(checkoutStepAfter(recorder));
        ChatAction summary = ChatActions.of(recorder);
        if (summary != null) {
            return "ONLINE".equals(summary.paymentMethod()) ? ORDER_SUMMARY_ANSWER + " " + PAY_NOW_AFTER_CONFIRM : ORDER_SUMMARY_ANSWER;
        }
        ToolCallRecorder.ToolCall prepared = recorder.calls().getLast();
        if ("CHOOSE".equals(prepared.status()) && payment == null) {
            return ASK_PAYMENT_ANSWER;
        }
        JsonNode unpaid = prepared.data();
        if (unpaid.path("paymentUrl").isString()) {
            // "Proceed to pay" after the order was placed: that order waits for its payment, nothing new is ordered
            return "Order #" + unpaid.path("orderNumber").asLong() + " is already placed and waits for your online payment of "
                    + unpaid.path("payable").asString() + ". Please press Pay Now to pay it; nothing new was ordered.";
        }
        return prepared.message();      // the cart cannot be ordered yet, no address, which address: the shop's own words
    }

    /**
     * The next checkout step after this question's tool calls: an order summary waits for its Confirm button,
     * prepareMyOrder's "how do you want to pay?" for the answer. Null otherwise.
     */
    private static CheckoutStep checkoutStepAfter(ToolCallRecorder recorder) {
        ChatAction card = ChatActions.of(recorder);
        if (card != null && ChatAction.PLACE_ORDER.equals(card.kind())) {
            return CheckoutStep.AWAITING_CONFIRMATION;
        }
        return preparedAQuestionHowToPay(recorder) ? CheckoutStep.AWAITING_PAYMENT_METHOD : null;
    }

    /**
     * prepareMyOrder's payment address for an order that waits for its online payment (UnpaidOrder of mcp-server-service):
     * order-service's own, for the Pay Now button. Null when there is none.
     */
    private static String paymentUrlOf(ToolCallRecorder recorder) {
        return recorder.calls().stream()
                .filter(call -> ChatActions.PREPARE_ORDER_TOOL.equals(call.toolName()) && "REFUSED".equals(call.status()))
                .map(call -> call.data().path("paymentUrl"))
                .filter(JsonNode::isString)
                .map(JsonNode::asString)
                .findFirst()
                .orElse(null);
    }

    /** Saves an answer written in code (no model) with its question, and makes the reply from this question's tools */
    private ChatReplyResponse replyFromCode(Conversation conversation, String question, String answer, boolean grounded,
                                            ToolCallRecorder recorder, long startedAt) {
        chatMemory.add(conversation.getId(), List.of(new UserMessage(question), new AssistantMessage(answer)));
        conversationService.messageAdded(conversation);
        ChatAction action = ChatActions.of(recorder);
        log.info("Conversation {}: answered in code in {} ms, tools={}, card={}, pendingAdd={}, checkoutStep={}",
                conversation.getId(), elapsed(startedAt).toMillis(), recorder.toolNames(),
                action == null ? "none" : action.kind(), conversation.hasPendingAdd(), conversation.getCheckoutStep());
        List<ChatLink> links = grounded ? AnswerLinks.of(answer, recorder, Audience.CUSTOMER) : List.of();
        return new ChatReplyResponse(UUID.fromString(conversation.getId()), answer, grounded, recorder.toolNames(), links,
                action, paymentUrlOf(recorder), approvalFor(conversation, answer, action));
    }

    /**
     * A card under the answer waits for the customer's click: its human approval starts PENDING (ApprovalService), so
     * the chat history can show "✓ Approved" or "Rejected" later. Null without a card. Should the approval not be
     * saved, the card still works (the website then makes its own key), only without a stored status: one missing
     * status must not cost the customer their answer.
     */
    private ChatApprovalResponse approvalFor(Conversation conversation, String answer, ChatAction action) {
        if (action == null) {
            return null;
        }
        try {
            return approvalService.open(conversation, answer, action);
        } catch (RuntimeException ex) {
            log.warn("Conversation {}: the card's approval could not be saved ({}); the card works without a stored status",
                    conversation.getId(), ex.getMessage());
            return null;
        }
    }

    /** prepareMyCartAdd for the pending product (by its id) with the customer's amount; returns the answer */
    private String prepareThePendingAdd(Conversation conversation, ChangeRequest.ListedProduct amount,
                                        ToolCallRecorder recorder, List<String> wishlist) {
        ToolCallback add = toolNamed(toolsFor(recorder, Audience.CUSTOMER, wishlist), GroceryToolsClient.ADD_TOOL);
        if (add == null) {
            return TOOLS_DOWN_ANSWER;
        }
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("product", String.valueOf(conversation.getPendingProductId()));
        arguments.put("quantity", amount.quantity());
        arguments.put("unit", amount.unit());
        add.call(JSON.writeValueAsString(arguments));

        if (recorder.hasFailures() || recorder.calls().isEmpty()) {
            return TOOLS_DOWN_ANSWER;               // nothing changed: the customer can send the amount again
        }
        if (ChatActions.of(recorder) != null) {
            conversation.clearPendingAdd();
            return AddReport.of(recorder);          // "Here is the product to add to your cart: 3 x Tomato (1 kg) for ₹108 ..."
        }
        if ("NOT_FOUND".equals(recorder.calls().getLast().status())) {
            String product = conversation.getPendingProductName();
            conversation.clearPendingAdd();
            return "Sorry, " + product + " is not sold any more, so nothing was added.";
        }
        return AddReport.of(recorder);              // the shop's "no" or its question: another amount may still work
    }

    /**
     * The model's answer. When the model fails AFTER a card was prepared, there is no exception (that would become a 503
     * that hides the card): the answer is then null, and the caller says what the card is itself.
     */
    private String ask(List<Message> history, String question, List<ToolCallback> tools, Audience audience,
                       String correction, ToolCallRecorder recorder) {
        try {
            return assistant.answer(history, question, tools, audience, correction);
        } catch (AssistantUnavailableException ex) {
            if (ChatActions.of(recorder) != null) {
                log.warn("The model failed after a card was prepared; the card is shown instead", ex);
                return null;
            }
            throw ex;
        }
    }

    /** The customer's message before this one, for an answer to the assistant's question ("the Amul one") */
    private static String previousQuestion(List<Message> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i) instanceof UserMessage message) {
                return message.getText();
            }
        }
        return null;
    }

    /**
     * The card the customer's words clearly ask for, prepared in code when the model did not prepare it: emptying the
     * cart, taking out the products of the cart they named, adding the products they named. Then, for two or more
     * products on one card (also when the model prepared them all itself), the answer written in code. Returns the
     * answer, or null when there is no such step and no cart card: then the model's answer stands (or it is asked again).
     */
    private static String cardStepIfAsked(Audience audience, boolean changesAsked, String question, boolean answerPassed,
                                          ToolCallRecorder recorder, List<ToolCallback> tools) {
        String step = clearStepIfAsked(audience, question, recorder, tools);
        if (step == null) {
            step = removalStepIfAsked(audience, question, recorder, tools);
        }
        if (step == null) {
            step = addStepIfAsked(audience, changesAsked, question, answerPassed, recorder, tools);
        }
        String report = productsReport(recorder);
        if (report != null) {
            return report;
        }
        return step != null ? step : cartCardSentence(recorder);
    }

    /**
     * The answer under a cart card the model prepared itself, written in code like the code steps' answers. The model's
     * own words can describe the change as done before the click ("Tomatoes are now listed as 3 kg in your cart", "Banana
     * Robusta is in your cart. Press Confirm to add it.") or repeat the tool's note to it ("tell the customer to check
     * it"); the card itself shows the details. Null when there is no cart card.
     */
    private static String cartCardSentence(ToolCallRecorder recorder) {
        ChatAction action = ChatActions.of(recorder);
        return action != null && CART_CARDS.contains(action.kind()) ? summaryAnswerFor(action, recorder) : null;
    }

    /**
     * "Remove Tomato and Potato from my cart", "Add Tomato 1 kg and Onion 2 kg": with two or more products the answer is
     * written in code from the tool results (RemovalReport, AddReport), also when the model prepared them all itself.
     * Null for one product or none.
     */
    private static String productsReport(ToolCallRecorder recorder) {
        if (RemovalReport.needed(recorder)) {
            return RemovalReport.of(recorder);
        }
        return AddReport.needed(recorder) ? AddReport.of(recorder) : null;
    }

    /**
     * The order summary when the customer clearly asked for it but the model prepared none: prepareMyOrder (it only
     * reads), with the way to pay the customer named. Two cases:
     * - the answer before asked how to pay, and this message names a way ("Cash on delivery");
     * - this message asks to order now ("Place my order") and the model did not call prepareMyOrder.
     * Returns the sentence for the answer (the summary, the question how to pay, or why the cart cannot be ordered
     * yet), or null when there is no such step (not a customer, a question to the customer still open, a card there).
     */
    private static String orderStepIfAsked(Audience audience, String question, String customerWords, List<Message> history,
                                           ToolCallRecorder recorder, List<ToolCallback> tools) {
        if (audience != Audience.CUSTOMER || ChatActions.of(recorder) != null || recorder.waitsForTheCustomer()) {
            return null;
        }
        boolean answersHowToPay = ChangeRequest.paymentOf(question) != null && askedHowToPay(history);
        // "Place my order": the model claimed a summary without calling the tool (the answer check caught it). Never next
        // to the add tool: when "buy X" was refused, the cart that is there is not what the customer wanted to order.
        boolean placeAskedNow = ChangeRequest.placeOrderAsked(question)
                && !recorder.toolNames().contains(ChatActions.PREPARE_ORDER_TOOL)
                && !recorder.toolNames().contains(GroceryToolsClient.ADD_TOOL);
        if (!answersHowToPay && !placeAskedNow) {
            return null;
        }
        ToolCallback prepare = toolNamed(tools, ChatActions.PREPARE_ORDER_TOOL);
        if (prepare == null) {
            return null;
        }
        String payment = ChangeRequest.paymentOf(customerWords);      // "cash", "online" or null (the tool then asks)
        prepare.call(payment == null ? "{}" : "{\"paymentMethod\":\"" + payment + "\"}");
        if (ChatActions.of(recorder) != null) {
            return ORDER_SUMMARY_ANSWER;
        }
        ToolCallRecorder.ToolCall prepared = recorder.calls().getLast();
        return "CHOOSE".equals(prepared.status()) ? ASK_PAYMENT_ANSWER : prepared.message();
    }

    /**
     * "Can you add Banana Robusta 1 dozen to my cart?": inside a longer conversation the small model answered without a
     * tool ("... is not sold ..."), twice, and the customer got FALLBACK_ANSWER (the user's conversation, 2026-10-30).
     * "can you add Akshayakalpa Organic Milk 1L and Organic Carrot 2 kg in my cart?": it said both were added without a
     * tool. "I want to buy this "Harpic Toilet Cleaner" product. Can you order it for me?": it only asked.
     * When the customer's message asks to add or buy and names products (ChangeRequest.productsToAdd: each with no amount,
     * or one number and its unit), the code prepares the addition of each of them that no prepareMyCartAdd call of this
     * answer was about, with the customer's own amount, through the same tool and all its checks (which product, the
     * packs, the store of the cart, the stock, the limit of 20; and RecordingToolCallback's checks). The tool only reads:
     * the card's Confirm button adds. A product that a tool of this answer found and the customer named in full is
     * passed by its name ("banana robusta" found by searchProducts as "Banana Robusta").
     * Returns AddReport's answer, or null when nothing was prepared here, or the code only learned that the words name no
     * product while the model's answer passed: then the model's answer stands.
     */
    private static String addStepIfAsked(Audience audience, boolean changesAsked, String question, boolean answerPassed,
                                         ToolCallRecorder recorder, List<ToolCallback> tools) {
        ChatAction card = ChatActions.of(recorder);
        if (audience != Audience.CUSTOMER || !changesAsked || recorder.hasFailures()
                || !(ChangeRequest.addAsked(question) || ChangeRequest.buyAsked(question))
                || ChangeRequest.removeAsked(question) || ChangeRequest.clearAsked(question)
                || (card != null && !ChatAction.ADD_TO_CART.equals(card.kind()))) {
            return null;
        }
        ToolCallback add = toolNamed(tools, GroceryToolsClient.ADD_TOOL);
        if (add == null) {
            return null;
        }
        // "Can you add one more item for X?": the model set X to 2, the amount check asked "how much?" (2 is not in the
        // words), and the code now prepares the one pack the customer said; any other question still stops it
        recorder.settleAmountQuestions();
        int before = recorder.calls().size();
        List<String> prepared = new ArrayList<>();
        for (ChangeRequest.ListedProduct product : ChangeRequest.productsToAdd(question)) {
            // a "how much?" is no answer of the model's call: "one more" is prepared with the one pack the customer said
            boolean tried = recorder.calls().stream().anyMatch(call -> GroceryToolsClient.ADD_TOOL.equals(call.toolName())
                    && ChangeRequest.sameProduct(call.argument("product"), product.product())
                    && !call.asksHowMuch());
            if (tried) {
                continue;
            }
            String found = PurchaseSteps.productToAdd(product.product(), recorder);
            Map<String, Object> arguments = new LinkedHashMap<>();
            arguments.put("product", found != null ? found : product.product());
            if (product.quantity() != null) {
                arguments.put("quantity", product.quantity());
            }
            if (product.unit() != null) {
                arguments.put("unit", product.unit());
            }
            add.call(JSON.writeValueAsString(arguments));
            prepared.add(Objects.toString(arguments.get("product")));
        }
        if (prepared.isEmpty()) {
            return null;
        }
        // Only "no product called ..." for words that may be no product name at all: the model's good answer stays
        boolean helps = ChatActions.of(recorder) != null || recorder.calls().subList(before, recorder.calls().size()).stream()
                .anyMatch(call -> !"NOT_FOUND".equals(call.status()) && !"INVALID_INPUT".equals(call.status()));
        if (!helps && answerPassed) {
            return null;
        }
        log.info("Code step: prepared the addition of {} (the model prepared none for them)", prepared);
        return AddReport.of(recorder);
    }

    /**
     * "Can you remove Harpic Toilet Cleaner from my cart?": the model wrote that the product was removed, without a tool.
     * "i do not want tomato in my cart. can you remove it please": it read the cart, then only announced the removal.
     * "Remove Tomato and Potato from my cart": it prepared only one of them.
     * When the customer's message asks to take products out and names lines of their cart in full, the code prepares
     * the removal of each of those lines that the model did not prepare, with prepareMyCartRemoval: it only reads, and
     * the customer's click on the card removes the products. The cart is read first when the model did not read it. No
     * product names in code: the lines come from the customer's own cart (PurchaseSteps.cartLinesToRemove).
     * Returns the sentence for the answer (REMOVE_SUMMARY_ANSWER with the card, "which one?" for two pack sizes, or the
     * tool's "not in the cart"), or null when there is no such step: then the model's answer stands. With two or more
     * products the caller replaces the sentence with RemovalReport.
     */
    private static String removalStepIfAsked(Audience audience, String question, ToolCallRecorder recorder,
                                             List<ToolCallback> tools) {
        ChatAction card = ChatActions.of(recorder);
        if (audience != Audience.CUSTOMER || !ChangeRequest.removeAsked(question)
                || (card != null && !ChatAction.REMOVE_FROM_CART.equals(card.kind())) || recorder.hasFailures()
                || recorder.waitsForTheCustomer()) {
            return null;
        }
        ToolCallback cart = toolNamed(tools, CART_TOOL);
        if (cart != null && !recorder.toolNames().contains(CART_TOOL)) {
            cart.call("{}");        // only reads: the lines of the customer's cart
        }
        List<String> notPrepared = PurchaseSteps.cartLinesToRemove(question, recorder).stream()
                .filter(product -> !PurchaseSteps.removalPrepared(product, recorder))
                .toList();
        ToolCallback prepare = toolNamed(tools, ChatActions.PREPARE_REMOVAL_TOOL);
        if (notPrepared.isEmpty() || prepare == null) {
            return null;
        }
        log.info("Code step: prepared the removal of {} (the model prepared none for them)", notPrepared);
        notPrepared.forEach(product -> prepare.call(JSON.writeValueAsString(Map.of("product", product))));
        if (ChatActions.of(recorder) != null) {
            return REMOVE_SUMMARY_ANSWER;
        }
        ToolCallRecorder.ToolCall prepared = recorder.calls().getLast();
        return switch (Objects.toString(prepared.status(), "")) {
            case "CHOOSE" -> PurchaseSteps.whichOneToRemove(prepared);
            case "NOT_FOUND" -> prepared.message();         // "The cart has no product called ..." (it changed meanwhile)
            default -> null;
        };
    }

    /**
     * "Empty my cart": the model wrote that the cart was emptied, or only asked. When the customer's message asks to empty
     * the whole cart and the model prepared nothing, the code prepares it with prepareMyCartClear (it only reads; the
     * customer's click on the card empties the cart). Returns CLEAR_SUMMARY_ANSWER with the card, the tool's "the cart is
     * empty already", or null when there is no such step.
     */
    private static String clearStepIfAsked(Audience audience, String question, ToolCallRecorder recorder,
                                           List<ToolCallback> tools) {
        if (audience != Audience.CUSTOMER || !ChangeRequest.clearAsked(question) || ChatActions.of(recorder) != null
                || recorder.toolNames().contains(ChatActions.PREPARE_CLEAR_TOOL) || recorder.hasFailures()
                || recorder.waitsForTheCustomer()) {
            return null;
        }
        ToolCallback prepare = toolNamed(tools, ChatActions.PREPARE_CLEAR_TOOL);
        if (prepare == null) {
            return null;
        }
        log.info("Code step: prepared emptying the cart (the model did not)");
        prepare.call("{}");
        if (ChatActions.of(recorder) != null) {
            return CLEAR_SUMMARY_ANSWER;
        }
        ToolCallRecorder.ToolCall prepared = recorder.calls().getLast();
        return "NOT_FOUND".equals(prepared.status()) ? prepared.message() : null;
    }

    /** The fixed answer under a card when the model's own answer failed the check */
    private static String summaryAnswerFor(ChatAction action, ToolCallRecorder recorder) {
        return switch (action.kind()) {
            case ChatAction.CANCEL_ORDER -> CANCEL_SUMMARY_ANSWER;
            case ChatAction.ADD_TO_CART -> AddReport.of(recorder);       // which products, the amounts and the prices
            case ChatAction.REMOVE_FROM_CART -> RemovalReport.of(recorder); // which products, by name
            case ChatAction.SET_CART_QUANTITY -> QUANTITY_SUMMARY_ANSWER;
            case ChatAction.CLEAR_CART -> CLEAR_SUMMARY_ANSWER;
            default -> ORDER_SUMMARY_ANSWER;
        };
    }

    /** prepareMyOrder answered in this turn that the customer must say how to pay (CHOOSE, no way to pay given) */
    private static boolean preparedAQuestionHowToPay(ToolCallRecorder recorder) {
        return recorder.calls().stream().anyMatch(call -> ChatActions.PREPARE_ORDER_TOOL.equals(call.toolName())
                && "CHOOSE".equals(call.status()) && ChangeRequest.paymentOf(call.arguments()) == null);
    }

    private static ToolCallback toolNamed(List<ToolCallback> tools, String name) {
        return tools.stream().filter(tool -> name.equals(tool.getToolDefinition().name())).findFirst().orElse(null);
    }

    /** Whether the assistant's last answer asked how to pay ("How would you like to pay: cash on delivery, or online ...?") */
    private static boolean askedHowToPay(List<Message> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i) instanceof AssistantMessage answer) {
                return HOW_TO_PAY.matcher(Objects.toString(answer.getText(), "")).find();
            }
        }
        return false;
    }

    /**
     * The tools of mcp-server-service, plus getWishlist for guests and customers (the shop's rules mention it; without
     * a wishlist from the page it says that the wishlist is not available here). The panels never get it: a wishlist
     * is a shopping thing.
     */
    private List<ToolCallback> toolsFor(ToolCallRecorder recorder, Audience audience, List<String> wishlist) {
        List<ToolCallback> tools = new ArrayList<>(groceryTools.toolsFor(recorder, audience));
        if (!audience.isPanel()) {
            tools.add(new WishlistToolCallback(wishlist, recorder));
        }
        return tools;
    }

    private void acquireAnswerSlot() {
        try {
            if (!answerSlots.tryAcquire(chatProperties.busyWait().toMillis(), TimeUnit.MILLISECONDS)) {
                throw new AssistantBusyException();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssistantBusyException();
        }
    }

    private static Duration elapsed(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt);
    }
}
