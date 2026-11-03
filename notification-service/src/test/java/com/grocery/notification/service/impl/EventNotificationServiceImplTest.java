package com.grocery.notification.service.impl;

import com.grocery.events.order.OrderCancelled;
import com.grocery.events.order.OrderConfirmed;
import com.grocery.events.user.PasswordResetRequested;
import com.grocery.events.user.SocialLoginLinked;
import com.grocery.events.user.UserRegistered;
import com.grocery.notification.entity.Contact;
import com.grocery.notification.entity.Notification;
import com.grocery.notification.entity.StoreContact;
import com.grocery.notification.enums.EmailStatus;
import com.grocery.notification.enums.NotificationType;
import com.grocery.notification.exception.UnreadableResetTokenException;
import com.grocery.notification.message.NotificationTemplates;
import com.grocery.notification.repository.ContactRepository;
import com.grocery.notification.repository.NotificationRepository;
import com.grocery.notification.repository.StoreContactRepository;
import com.grocery.notification.security.ResetTokenCipher;
import com.grocery.notification.sender.EmailSender;
import com.grocery.notification.sender.NotificationDispatcher;
import com.grocery.notification.service.ProcessedEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventNotificationServiceImplTest {

    @Mock
    private ProcessedEventService processedEventService;
    @Mock
    private ContactRepository contactRepository;
    @Mock
    private StoreContactRepository storeContactRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private EmailSender emailSender;

    private EventNotificationServiceImpl service;

    /** The local key, and a token encrypted with it by auth-service for user 4 (see ResetTokenCipherTest) */
    private static final String RESET_KEY = "bG9jYWwtZGV2LW9ubHktY2hhbmdlLW1lLTAxMjM0NTY=";
    private static final String ENCRYPTED_TOKEN = "v1.eOLGf4uskBkAJBnIyDRL4Gwx-if7D6nJw9y8b54cNjqZMPDGAx_z0gLJaKDfTDdwWulllWTbwTeOds-x";

    @BeforeEach
    void setUp() {
        NotificationDispatcher dispatcher = new NotificationDispatcher(notificationRepository, contactRepository, storeContactRepository, emailSender);
        service = new EventNotificationServiceImpl(processedEventService, contactRepository, storeContactRepository,
                new NotificationTemplates(), dispatcher, new ResetTokenCipher(RESET_KEY));
    }

    @Test
    void userRegistered_addsTheContact_andSendsAWelcome() {
        when(processedEventService.markAsProcessed(anyString(), eq("UserRegistered"))).thenReturn(true);
        Contact contact = new Contact(11L, "Neha Gupta", "neha@example.com", "9876543210", "CUSTOMER");
        when(contactRepository.findById(11L)).thenReturn(Optional.of(contact));
        when(emailSender.send(eq("neha@example.com"), anyString(), anyString())).thenReturn(EmailStatus.SENT);

        service.handleUserRegistered(UserRegistered.newBuilder()
                .setEventId("e1").setOccurredAt(Instant.now()).setUserId(11L).setFullName("Neha Gupta")
                .setEmail("neha@example.com").setPhone("9876543210").setRole("CUSTOMER").build());

        verify(contactRepository).save(any(Contact.class));
        Notification saved = savedNotifications(1).getFirst();
        assertThat(saved.getType()).isEqualTo(NotificationType.WELCOME);
        assertThat(saved.getEmailStatus()).isEqualTo(EmailStatus.SENT);
    }

    @Test
    void orderConfirmed_tellsTheCustomer_andTheStoreOwner() {
        when(processedEventService.markAsProcessed(anyString(), eq("OrderConfirmed"))).thenReturn(true);
        when(storeContactRepository.findById(1L)).thenReturn(Optional.of(new StoreContact(1L, 3L, "Fresh Mart")));

        service.handleOrderConfirmed(OrderConfirmed.newBuilder()
                .setEventId("e2").setOccurredAt(Instant.now()).setOrderId(21L).setCustomerUserId(4L).setStoreId(1L)
                .setTotalAmount("210.90").setPaymentMethod("COD").build());

        List<Notification> saved = savedNotifications(2);
        assertThat(saved).extracting(Notification::getUserId).containsExactly(4L, 3L);
        assertThat(saved).extracting(Notification::getType).containsExactly(NotificationType.ORDER_CONFIRMED, NotificationType.NEW_ORDER);
        assertThat(saved.getFirst().getMessage()).contains("Please pay ₹210.90 in cash on delivery");
        assertThat(saved.getFirst().getEmailStatus()).isEqualTo(EmailStatus.NOT_SENT);   // no contact in this test
    }

    @Test
    void orderCancelledByTheSystem_onlyTellsTheCustomer() {
        when(processedEventService.markAsProcessed(anyString(), eq("OrderCancelled"))).thenReturn(true);

        service.handleOrderCancelled(OrderCancelled.newBuilder()
                .setEventId("e3").setOccurredAt(Instant.now()).setOrderId(22L).setCustomerUserId(4L).setStoreId(1L)
                .setReason("Not enough stock: Amul Butter (sold out)").setCancelledBy("SYSTEM").build());

        assertThat(savedNotifications(1)).extracting(Notification::getUserId).containsExactly(4L);
        verifyNoInteractions(storeContactRepository);
    }

    @Test
    void passwordReset_decryptsTheToken_andSendsItByEmailOnly() {
        when(processedEventService.markAsProcessed(anyString(), eq("PasswordResetRequested"))).thenReturn(true);

        service.handlePasswordResetRequested(resetEvent(4L).setEncryptedResetToken(ENCRYPTED_TOKEN).build());

        verify(emailSender).send(eq("customer1@grocery.com"), eq("Reset your password"), contains("5f2b9c0e8d7a4e1f9b3c6a2d8e0f4b7c"));
        verify(notificationRepository, never()).save(any());   // the token is never stored
    }

    @Test
    void passwordReset_skipsAnOldEventWithoutAnEncryptedToken_itsCodeHasExpired() {
        when(processedEventService.markAsProcessed(anyString(), eq("PasswordResetRequested"))).thenReturn(true);

        // Written before 2026-10-28 (the token was in plain text then): expired long ago, nothing to send
        service.handlePasswordResetRequested(resetEvent(4L).setExpiresAt(Instant.now().minusSeconds(60)).build());

        verifyNoInteractions(emailSender);
    }

    @Test
    void passwordReset_failsForAValidEventWithoutAToken() {
        when(processedEventService.markAsProcessed(anyString(), eq("PasswordResetRequested"))).thenReturn(true);

        // Cannot happen with a working auth-service: the dead-letter topic gets it, so it can be looked at
        assertThatThrownBy(() -> service.handlePasswordResetRequested(resetEvent(4L).setExpiresAt(Instant.now().plusSeconds(600)).build()))
                .isInstanceOf(UnreadableResetTokenException.class);
        verifyNoInteractions(emailSender);
    }

    @Test
    void passwordReset_sendsNothing_whenTheTokenCannotBeDecrypted() {
        when(processedEventService.markAsProcessed(anyString(), eq("PasswordResetRequested"))).thenReturn(true);

        // Encrypted for user 4, but the event is user 5's: the dead-letter topic gets it (KafkaConsumerConfig)
        assertThatThrownBy(() -> service.handlePasswordResetRequested(resetEvent(5L).setEncryptedResetToken(ENCRYPTED_TOKEN).build()))
                .isInstanceOf(UnreadableResetTokenException.class);
        verifyNoInteractions(emailSender);
    }

    private static PasswordResetRequested.Builder resetEvent(long userId) {
        return PasswordResetRequested.newBuilder()
                .setEventId("e4").setOccurredAt(Instant.now()).setUserId(userId).setFullName("Priya Sharma")
                .setEmail("customer1@grocery.com").setExpiresAt(Instant.parse("2026-10-26T10:00:00Z"));
    }

    @Test
    void socialLoginLinked_isShownAndEmailed_soTheOwnerCanReact() {
        when(processedEventService.markAsProcessed(anyString(), eq("SocialLoginLinked"))).thenReturn(true);
        when(contactRepository.findById(4L)).thenReturn(Optional.of(new Contact(4L, "Priya Sharma", "customer1@grocery.com", null, "CUSTOMER")));
        when(emailSender.send(eq("customer1@grocery.com"), anyString(), anyString())).thenReturn(EmailStatus.SENT);

        service.handleSocialLoginLinked(SocialLoginLinked.newBuilder()
                .setEventId("e9").setOccurredAt(Instant.now()).setUserId(4L).setFullName("Priya Sharma")
                .setEmail("customer1@grocery.com").setProvider("Google").build());

        Notification saved = savedNotifications(1).getFirst();
        assertThat(saved.getType()).isEqualTo(NotificationType.SOCIAL_LOGIN_LINKED);
        assertThat(saved.getTitle()).isEqualTo("Google was connected to your account");
        verify(emailSender).send(eq("customer1@grocery.com"), eq("Google was connected to your account"), contains("Forgot password"));
    }

    @Test
    void duplicateEvent_sendsNothing() {
        when(processedEventService.markAsProcessed(anyString(), eq("OrderConfirmed"))).thenReturn(false);

        service.handleOrderConfirmed(OrderConfirmed.newBuilder()
                .setEventId("e2").setOccurredAt(Instant.now()).setOrderId(21L).setCustomerUserId(4L).setStoreId(1L)
                .setTotalAmount("210.90").setPaymentMethod("COD").build());

        verifyNoInteractions(notificationRepository, emailSender);
    }

    private List<Notification> savedNotifications(int expected) {
        ArgumentCaptor<Notification> notifications = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(expected)).save(notifications.capture());
        return notifications.getAllValues();
    }
}
