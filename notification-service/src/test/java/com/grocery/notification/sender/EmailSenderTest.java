package com.grocery.notification.sender;

import com.grocery.notification.config.NotificationProperties;
import com.grocery.notification.enums.EmailStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(OutputCaptureExtension.class)   // lets a test read the log output
class EmailSenderTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final EmailSender emailSender = new EmailSender(mailSender, new NotificationProperties("no-reply@grocery.local"));

    @Test
    void sendsAPlainTextEmailFromTheConfiguredAddress() {
        EmailStatus status = emailSender.send("customer1@grocery.com", "Order #1 confirmed", "Hi Priya");

        ArgumentCaptor<SimpleMailMessage> email = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(email.capture());
        assertThat(status).isEqualTo(EmailStatus.SENT);
        assertThat(email.getValue().getFrom()).isEqualTo("no-reply@grocery.local");
        assertThat(email.getValue().getTo()).containsExactly("customer1@grocery.com");
    }

    @Test
    void aMailServerProblem_isReportedAsFailed_notThrown() {
        doThrow(new MailSendException("Connection refused")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThat(emailSender.send("customer1@grocery.com", "Order #1 confirmed", "Hi Priya")).isEqualTo(EmailStatus.FAILED);
    }

    @Test
    void theLogGetsMaskedAddressesOnly_alsoInsideAnErrorMessage() {
        assertThat(EmailSender.maskEmails("customer1@grocery.com")).isEqualTo("c***@grocery.com");
        assertThat(EmailSender.maskEmails("Invalid Addresses: <priya.sharma+orders@mail.example.in>; retry later"))
                .isEqualTo("Invalid Addresses: <p***@mail.example.in>; retry later");
        assertThat(EmailSender.maskEmails("Connection refused")).isEqualTo("Connection refused");
        assertThat(EmailSender.maskEmails(null)).isNull();
    }

    @Test
    void theSentLogLine_doesNotContainTheAddress(CapturedOutput output) {
        emailSender.send("customer1@grocery.com", "Order #1 confirmed", "Hi Priya");

        assertThat(output.getOut()).contains("Email 'Order #1 confirmed' sent to c***@grocery.com")
                .doesNotContain("customer1@grocery.com");
    }
}
