package com.grocery.mcpclient.dto.request;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * One message of the customer.
 *
 * @param conversationId null for the first message; afterwards the id from the previous answer, so the assistant
 *                       remembers the conversation ("and how much is it?")
 * @param message        the question, at most 500 characters
 * @param wishlist       the names of the products on the wishlist, which the shop keeps in the customer's browser
 *                       (there is no wishlist in the backend), so the assistant can answer "what is on my
 *                       wishlist?". null when the page does not send it (the panels).
 * @param confirmed      the cards under earlier answers that the customer confirmed since their last message. The
 *                       website placed or cancelled those orders itself, so the chat would not know it otherwise
 *                       ("cancel my last order" right after ordering). null or empty when there are none.
 */
@Schema(description = "A message to the shopping assistant")
public record ChatMessageRequest(
        @Schema(description = "Empty for a new conversation, else the conversationId of the previous answer",
                example = "3f0c8a8e-2d6b-4c1e-9a43-8f1f4f3c2b10")
        UUID conversationId,

        @Schema(description = "The customer's question", example = "Do you have Amul butter? How much is it?")
        @NotBlank(message = "Please type a message")
        @Size(max = MAX_LENGTH, message = "A message can have at most " + MAX_LENGTH + " characters")
        String message,

        @ArraySchema(schema = @Schema(description = "A product name", example = "Himalaya Baby Lotion"),
                arraySchema = @Schema(description = "The product names on the wishlist saved in the browser; "
                        + "empty when it is empty, left out by the panels"))
        @Size(max = MAX_WISHLIST_ITEMS, message = "A wishlist can have at most " + MAX_WISHLIST_ITEMS + " products")
        List<@NotBlank(message = "A wishlist product needs a name")
             @Size(max = MAX_PRODUCT_NAME_LENGTH, message = "A product name can have at most "
                     + MAX_PRODUCT_NAME_LENGTH + " characters") String> wishlist,

        @ArraySchema(arraySchema = @Schema(description = "The cards the customer confirmed since their last message "
                + "(the website placed or cancelled the order itself); left out when there are none"))
        @Size(max = MAX_CONFIRMED, message = "At most " + MAX_CONFIRMED + " confirmed cards can be sent")
        List<@NotNull(message = "A confirmed card is missing") @Valid ConfirmedCard> confirmed) {

    public static final int MAX_LENGTH = 500;
    /** The shop's wishlist holds at most 50 products (useWishlist.ts). */
    public static final int MAX_WISHLIST_ITEMS = 50;
    public static final int MAX_PRODUCT_NAME_LENGTH = 200;
    public static final int MAX_CONFIRMED = 5;

    /** A message without a wishlist (the panels, and older pages). */
    public ChatMessageRequest(UUID conversationId, String message) {
        this(conversationId, message, null, null);
    }

    /** A message without confirmed cards. */
    public ChatMessageRequest(UUID conversationId, String message, List<String> wishlist) {
        this(conversationId, message, wishlist, null);
    }

    /**
     * A card the customer confirmed: the website placed (PLACE_ORDER) or cancelled (CANCEL_ORDER) this order. Only a
     * kind and a number, never a text of the page: the chat writes its own sentence about it ("Order #207 placed with
     * the Confirm button."). The number is not checked here; a tool that is called with it reads the order with the
     * customer's own login, and order-service answers 404 for anyone else's order.
     */
    @Schema(description = "A card the customer confirmed")
    public record ConfirmedCard(
            @Schema(description = "PLACE_ORDER or CANCEL_ORDER", example = "PLACE_ORDER")
            @NotNull(message = "kind is required")
            @Pattern(regexp = "PLACE_ORDER|CANCEL_ORDER", message = "kind must be PLACE_ORDER or CANCEL_ORDER")
            String kind,

            @Schema(description = "The order that was placed or cancelled", example = "207")
            @NotNull(message = "orderId is required")
            @Positive(message = "orderId must be a positive number")
            Long orderId) {
    }
}
