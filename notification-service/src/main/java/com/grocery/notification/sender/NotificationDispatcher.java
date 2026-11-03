package com.grocery.notification.sender;

import com.grocery.notification.entity.Contact;
import com.grocery.notification.entity.Notification;
import com.grocery.notification.message.NotificationMessage;
import com.grocery.notification.repository.ContactRepository;
import com.grocery.notification.repository.NotificationRepository;
import com.grocery.notification.repository.StoreContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Delivers a message to a user: saves it as an in-app notification and emails it when the
 * contact book has the user's address.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private static final String SIGNATURE = "\n\n- Team Grocery";

    private final NotificationRepository notificationRepository;
    private final ContactRepository contactRepository;
    private final StoreContactRepository storeContactRepository;
    private final EmailSender emailSender;

    @Transactional(propagation = Propagation.MANDATORY)   // part of the event handler's transaction
    public void notifyUser(Long userId, NotificationMessage message) {
        Notification notification = Notification.create(userId, message.type(), message.title(), message.body(), message.referenceId());

        Optional<Contact> contact = contactRepository.findById(userId);
        if (contact.isPresent()) {
            String to = contact.get().getEmail();
            notification.recordEmail(to, emailSender.send(to, message.title(), emailText(contact.get().getFullName(), message.body())));
        } else {
            log.info("No contact for user id={} - '{}' is in-app only", userId, message.title());
        }
        notificationRepository.save(notification);
    }

    /** Notifies the vendor who owns the store (looked up in store_contacts). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyStoreOwner(Long storeId, NotificationMessage message) {
        storeContactRepository.findById(storeId).ifPresentOrElse(
                store -> notifyUser(store.getOwnerUserId(), message),
                () -> log.warn("Unknown store id={} - '{}' not delivered", storeId, message.title()));
    }

    /** For users who are not logged in (password reset): email only, no in-app notification. */
    public void emailOnly(String email, String fullName, NotificationMessage message) {
        emailSender.send(email, message.title(), emailText(fullName, message.body()));
    }

    private String emailText(String fullName, String body) {
        return "Hi " + fullName + ",\n\n" + body + SIGNATURE;
    }
}
