package com.grocery.mcpclient.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Who a conversation belongs to (a row in conversations).
 *
 * The messages themselves are in SPRING_AI_CHAT_MEMORY (Spring AI's JdbcChatMemoryRepository), with the same id.
 * The id is a random UUID made by this service, so nobody can guess another person's conversation.
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    @Column(length = 36)
    private String id;

    /** The logged-in user who started it, or null for a guest. */
    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_message_at", nullable = false)
    private Instant lastMessageAt;

    /**
     * The product whose amount the assistant's last answer asked for ("How much Tomato would you like to add?"), so the
     * customer's next message ("3 kg") adds exactly it (PendingAdd). Null when no such question is open.
     */
    @Column(name = "pending_product_id")
    private Long pendingProductId;

    /** That product's name in the catalog ("Tomato"), for the answers */
    @Column(name = "pending_product_name", length = 200)
    private String pendingProductName;

    /** Where the conversation is in buying the cart (the chat asked how to pay, or showed the summary); null otherwise */
    @Enumerated(EnumType.STRING)
    @Column(name = "checkout_step", length = 30)
    private CheckoutStep checkoutStep;

    public static Conversation start(Long ownerUserId, Instant now) {
        Conversation conversation = new Conversation();
        conversation.id = UUID.randomUUID().toString();
        conversation.ownerUserId = ownerUserId;
        conversation.createdAt = now;
        conversation.lastMessageAt = now;
        return conversation;
    }

    /** A guest's conversation belongs to guests (whoever has the id); a user's conversation only to that user. */
    public boolean belongsTo(Long userId) {
        return Objects.equals(ownerUserId, userId);
    }

    public void messageAdded(Instant now) {
        this.lastMessageAt = now;
    }

    public boolean hasPendingAdd() {
        return pendingProductId != null;
    }

    /** The assistant asked how much of this product the customer wants; their next message answers it. */
    public void setPendingAdd(long productId, String productName) {
        this.pendingProductId = productId;
        this.pendingProductName = productName;
    }

    /** The product was prepared for the card, the customer called it off, or they asked something else. */
    public void clearPendingAdd() {
        this.pendingProductId = null;
        this.pendingProductName = null;
    }

    /** @param step the new step of the checkout; null when it ends (placed, called off, or another question) */
    public void setCheckoutStep(CheckoutStep step) {
        this.checkoutStep = step;
    }
}
