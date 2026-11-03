package com.grocery.auth.mapper;

import com.grocery.auth.entity.PasswordResetToken;
import com.grocery.auth.entity.User;
import com.grocery.events.user.PasswordResetRequested;
import com.grocery.events.user.SocialLoginLinked;
import com.grocery.events.user.UserRegistered;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Builds the Avro events this service publishes. Every event gets a new unique eventId.
 */
@Component
public class UserEventMapper {

    public UserRegistered toUserRegistered(User user) {
        return UserRegistered.newBuilder()
                .setEventId(newEventId())
                .setOccurredAt(Instant.now())
                .setUserId(user.getId())
                .setFullName(user.getFullName())
                .setEmail(user.getEmail())
                .setPhone(user.getPhone())
                .setRole(user.getRole().name())
                .build();
    }

    /** encryptedToken: the token from ResetTokenCipher; the plain token is never put into the event. */
    public PasswordResetRequested toPasswordResetRequested(PasswordResetToken resetToken, String encryptedToken) {
        User user = resetToken.getUser();
        return PasswordResetRequested.newBuilder()
                .setEventId(newEventId())
                .setOccurredAt(Instant.now())
                .setUserId(user.getId())
                .setFullName(user.getFullName())
                .setEmail(user.getEmail())
                .setEncryptedResetToken(encryptedToken)
                .setExpiresAt(resetToken.getExpiresAt())
                .build();
    }

    /** providerName: as shown to users, e.g. "Google". */
    public SocialLoginLinked toSocialLoginLinked(User user, String providerName) {
        return SocialLoginLinked.newBuilder()
                .setEventId(newEventId())
                .setOccurredAt(Instant.now())
                .setUserId(user.getId())
                .setFullName(user.getFullName())
                .setEmail(user.getEmail())
                .setProvider(providerName)
                .build();
    }

    private String newEventId() {
        return UUID.randomUUID().toString();
    }
}
