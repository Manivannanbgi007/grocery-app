package com.grocery.notification.service.impl;

import com.grocery.events.delivery.DeliveryAssigned;
import com.grocery.events.delivery.PartnerKycReviewed;
import com.grocery.events.inventory.LowStockDetected;
import com.grocery.events.order.OrderAccepted;
import com.grocery.events.order.OrderCancelled;
import com.grocery.events.order.OrderConfirmed;
import com.grocery.events.order.OrderDelivered;
import com.grocery.events.order.OrderDeliveryFailed;
import com.grocery.events.order.OrderOutForDelivery;
import com.grocery.events.order.OrderPacked;
import com.grocery.events.order.OrderRejected;
import com.grocery.events.payment.PaymentFailed;
import com.grocery.events.payment.RefundCompleted;
import com.grocery.events.store.StoreApproved;
import com.grocery.events.store.StoreRegistered;
import com.grocery.events.store.StoreStatusChanged;
import com.grocery.events.user.PasswordResetRequested;
import com.grocery.events.user.SocialLoginLinked;
import com.grocery.events.user.UserRegistered;
import com.grocery.notification.entity.Contact;
import com.grocery.notification.entity.StoreContact;
import com.grocery.notification.exception.UnreadableResetTokenException;
import com.grocery.notification.message.NotificationTemplates;
import com.grocery.notification.repository.ContactRepository;
import com.grocery.notification.repository.StoreContactRepository;
import com.grocery.notification.security.ResetTokenCipher;
import com.grocery.notification.sender.NotificationDispatcher;
import com.grocery.notification.service.EventNotificationService;
import com.grocery.notification.service.ProcessedEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Each handler: skip a duplicate event, then (sometimes) update the contact book, then notify.
 * An email that cannot be sent never fails the handler (see EmailSender), so the consumer never gets stuck on a mail problem.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EventNotificationServiceImpl implements EventNotificationService {

    private static final String SYSTEM = "SYSTEM";

    private final ProcessedEventService processedEventService;
    private final ContactRepository contactRepository;
    private final StoreContactRepository storeContactRepository;
    private final NotificationTemplates templates;
    private final NotificationDispatcher dispatcher;
    private final ResetTokenCipher resetTokenCipher;

    // ----- users and stores -----

    @Override
    public void handleUserRegistered(UserRegistered event) {
        if (isDuplicate(event.getEventId(), "UserRegistered")) {
            return;
        }
        contactRepository.save(new Contact(event.getUserId(), event.getFullName(), event.getEmail(), event.getPhone(), event.getRole()));
        dispatcher.notifyUser(event.getUserId(), templates.welcome(event.getFullName()));
    }

    @Override
    public void handlePasswordResetRequested(PasswordResetRequested event) {
        if (isDuplicate(event.getEventId(), "PasswordResetRequested")) {
            return;
        }
        if (event.getEncryptedResetToken() == null) {
            // Only events written before 2026-10-28 have none (their token was in plain text, a field removed since).
            // Such a token stopped working 30 minutes after it was issued: nothing to send.
            if (event.getExpiresAt().isBefore(Instant.now())) {
                log.info("Skipping PasswordResetRequested {}: an old event without an encrypted token, expired at {}",
                        event.getEventId(), event.getExpiresAt());
                return;
            }
            throw new UnreadableResetTokenException("PasswordResetRequested " + event.getEventId() + " has no reset token");
        }
        // auth-service encrypts the token (ResetTokenCipher). It is secret: email only, never stored as an in-app
        // notification, never logged
        String token = resetTokenCipher.decrypt(event.getEncryptedResetToken(), event.getUserId());
        dispatcher.emailOnly(event.getEmail(), event.getFullName(), templates.passwordReset(token, event.getExpiresAt()));
    }

    @Override
    public void handleSocialLoginLinked(SocialLoginLinked event) {
        if (isDuplicate(event.getEventId(), "SocialLoginLinked")) {
            return;
        }
        // In the app and by email: if someone else connected their account, the owner can react (reset the password)
        dispatcher.notifyUser(event.getUserId(), templates.socialLoginLinked(event.getProvider()));
    }

    @Override
    public void handleStoreRegistered(StoreRegistered event) {
        if (isDuplicate(event.getEventId(), "StoreRegistered")) {
            return;
        }
        storeContactRepository.save(new StoreContact(event.getStoreId(), event.getOwnerUserId(), event.getStoreName()));
        dispatcher.notifyUser(event.getOwnerUserId(), templates.storeRegistered(event.getStoreId(), event.getStoreName()));
    }

    @Override
    public void handleStoreApproved(StoreApproved event) {
        if (isDuplicate(event.getEventId(), "StoreApproved")) {
            return;
        }
        storeContactRepository.save(new StoreContact(event.getStoreId(), event.getOwnerUserId(), event.getStoreName()));
        dispatcher.notifyUser(event.getOwnerUserId(), templates.storeApproved(event.getStoreId(), event.getStoreName()));
    }

    @Override
    public void handleStoreStatusChanged(StoreStatusChanged event) {
        if (isDuplicate(event.getEventId(), "StoreStatusChanged")) {
            return;
        }
        String storeName = storeContactRepository.findById(event.getStoreId()).map(StoreContact::getStoreName).orElse("Your store");
        dispatcher.notifyUser(event.getOwnerUserId(), templates.storeStatusChanged(event, storeName));
    }

    // ----- orders -----

    @Override
    public void handleOrderConfirmed(OrderConfirmed event) {
        if (isDuplicate(event.getEventId(), "OrderConfirmed")) {
            return;
        }
        dispatcher.notifyUser(event.getCustomerUserId(), templates.orderConfirmed(event));
        dispatcher.notifyStoreOwner(event.getStoreId(), templates.newOrderForStore(event));   // "receive new order notifications"
    }

    @Override
    public void handleOrderAccepted(OrderAccepted event) {
        if (!isDuplicate(event.getEventId(), "OrderAccepted")) {
            dispatcher.notifyUser(event.getCustomerUserId(), templates.orderAccepted(event.getOrderId()));
        }
    }

    @Override
    public void handleOrderRejected(OrderRejected event) {
        if (!isDuplicate(event.getEventId(), "OrderRejected")) {
            dispatcher.notifyUser(event.getCustomerUserId(), templates.orderRejected(event));
        }
    }

    @Override
    public void handleOrderPacked(OrderPacked event) {
        if (!isDuplicate(event.getEventId(), "OrderPacked")) {
            dispatcher.notifyUser(event.getCustomerUserId(), templates.orderPacked(event.getOrderId()));
        }
    }

    @Override
    public void handleOrderOutForDelivery(OrderOutForDelivery event) {
        if (!isDuplicate(event.getEventId(), "OrderOutForDelivery")) {
            dispatcher.notifyUser(event.getCustomerUserId(), templates.orderOutForDelivery(event.getOrderId()));
        }
    }

    @Override
    public void handleOrderDelivered(OrderDelivered event) {
        if (!isDuplicate(event.getEventId(), "OrderDelivered")) {
            dispatcher.notifyUser(event.getCustomerUserId(), templates.orderDelivered(event));
        }
    }

    @Override
    public void handleOrderCancelled(OrderCancelled event) {
        if (isDuplicate(event.getEventId(), "OrderCancelled")) {
            return;
        }
        dispatcher.notifyUser(event.getCustomerUserId(), templates.orderCancelled(event));
        // The system only cancels before the store saw the order (no stock, payment failed): no need to tell the store
        if (!SYSTEM.equals(event.getCancelledBy())) {
            dispatcher.notifyStoreOwner(event.getStoreId(), templates.orderCancelledForStore(event));
        }
    }

    @Override
    public void handleOrderDeliveryFailed(OrderDeliveryFailed event) {
        if (isDuplicate(event.getEventId(), "OrderDeliveryFailed")) {
            return;
        }
        dispatcher.notifyUser(event.getCustomerUserId(), templates.orderDeliveryFailed(event));
        dispatcher.notifyStoreOwner(event.getStoreId(), templates.orderDeliveryFailedForStore(event));
    }

    @Override
    public void handleDeliveryAssigned(DeliveryAssigned event) {
        if (isDuplicate(event.getEventId(), "DeliveryAssigned")) {
            return;
        }
        dispatcher.notifyUser(event.getCustomerUserId(), templates.deliveryPartnerAssigned(event));
        dispatcher.notifyUser(event.getPartnerUserId(), templates.newDelivery(event));
    }

    @Override
    public void handlePartnerKycReviewed(PartnerKycReviewed event) {
        if (!isDuplicate(event.getEventId(), "PartnerKycReviewed")) {
            dispatcher.notifyUser(event.getPartnerUserId(), templates.partnerKycReviewed(event));
        }
    }

    // ----- payments and stock -----

    @Override
    public void handlePaymentFailed(PaymentFailed event) {
        if (!isDuplicate(event.getEventId(), "PaymentFailed")) {
            dispatcher.notifyUser(event.getCustomerUserId(), templates.paymentFailed(event));
        }
    }

    @Override
    public void handleRefundCompleted(RefundCompleted event) {
        if (!isDuplicate(event.getEventId(), "RefundCompleted")) {
            dispatcher.notifyUser(event.getCustomerUserId(), templates.refundCompleted(event));
        }
    }

    @Override
    public void handleLowStockDetected(LowStockDetected event) {
        if (!isDuplicate(event.getEventId(), "LowStockDetected")) {
            dispatcher.notifyStoreOwner(event.getStoreId(), templates.lowStock(event));
        }
    }

    private boolean isDuplicate(String eventId, String eventType) {
        boolean firstTime = processedEventService.markAsProcessed(eventId, eventType);
        if (!firstTime) {
            log.info("Skipping duplicate {} eventId={}", eventType, eventId);
        }
        return !firstTime;
    }
}
