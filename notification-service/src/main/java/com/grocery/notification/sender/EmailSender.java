package com.grocery.notification.sender;

import com.grocery.notification.config.NotificationProperties;
import com.grocery.notification.enums.EmailStatus;
import com.grocery.notification.logging.Loggable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Sends plain-text emails over SMTP. Locally that is Mailpit (http://localhost:8025 shows every email).
 *
 * A failing mail server must not lose the notification or block the Kafka consumer, so a failure
 * is logged and reported as FAILED instead of thrown.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Loggable   // Talks to the mail server (SMTP): LoggingAspect logs the time of every call and warns when it is slow
public class EmailSender {

    private final JavaMailSender mailSender;
    private final NotificationProperties notificationProperties;

    public EmailStatus send(String to, String subject, String text) {
        SimpleMailMessage email = new SimpleMailMessage();
        email.setFrom(notificationProperties.fromEmail());
        email.setTo(to);
        email.setSubject(subject);
        email.setText(text);
        try {
            mailSender.send(email);
            log.info("Email '{}' sent to {}", subject, maskEmails(to));
            return EmailStatus.SENT;
        } catch (MailException ex) {
            log.warn("Email '{}' to {} failed: {}", subject, maskEmails(to), maskEmails(ex.getMessage()));
            return EmailStatus.FAILED;
        }
    }

    /**
     * Email addresses are personal data and do not belong in logs: "customer1@grocery.com" -> "c***@grocery.com"
     * (enough to tell addresses apart; the full address is in the notification in the database).
     * Mail server errors often contain the address too, so the whole text is masked.
     */
    static String maskEmails(String text) {
        return text == null ? null : EMAIL_ADDRESS.matcher(text).replaceAll("$1***@$2");
    }

    private static final Pattern EMAIL_ADDRESS = Pattern.compile("([A-Za-z0-9])[A-Za-z0-9._%+-]*@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})");
}
