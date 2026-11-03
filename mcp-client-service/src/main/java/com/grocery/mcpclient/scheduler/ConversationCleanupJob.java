package com.grocery.mcpclient.scheduler;

import com.grocery.mcpclient.config.ChatProperties;
import com.grocery.mcpclient.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Deletes conversations without a new message for app.chat.conversation-retention (default 30 days), so chat_db
 * does not keep what customers typed for ever. Runs every night (app.chat.cleanup-cron).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationCleanupJob {

    private final ConversationService conversationService;
    private final ChatProperties chatProperties;
    private final Clock clock;

    @Scheduled(cron = "${app.chat.cleanup-cron}", zone = "${app.chat.time-zone}")
    public void deleteOldConversations() {
        Instant before = clock.instant().minus(chatProperties.conversationRetention());
        int deleted = conversationService.deleteInactiveSince(before);
        log.info("Clean-up: deleted {} conversation(s) without a message since {}", deleted, before);
    }
}
