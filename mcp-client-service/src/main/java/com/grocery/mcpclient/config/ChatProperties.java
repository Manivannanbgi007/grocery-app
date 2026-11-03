package com.grocery.mcpclient.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.ZoneId;

/**
 * Settings under "app.chat" in application.yml.
 *
 * @param systemPrompt          the rules the model must follow with guests and customers (prompts/system-prompt.txt)
 * @param panelSystemPrompt     the rules for vendors, delivery partners and admins (prompts/panel-system-prompt.txt)
 * @param historyMessages      how many earlier messages of the conversation the model sees (user + assistant)
 * @param maxConcurrentChats    how many questions are answered at the same time; Ollama on a CPU is slow, so more
 *                              questions would only wait in Ollama's queue and block threads here
 * @param busyWait              how long a question waits for a free slot before the answer is "busy, try again"
 * @param retryOnlyWithin       a rejected answer is retried only when the first attempt took less than this,
 *                              so a slow model never makes the customer wait twice as long
 * @param conversationRetention conversations without a new message for this long are deleted every night
 * @param timeZone              the shop's time zone, used for "today" in the system prompt
 * @param warmUp                ask one question when the service starts, so the model is loaded before the first
 *                              customer asks (a cold model needs over a minute on a CPU)
 */
@Validated
@ConfigurationProperties(prefix = "app.chat")
public record ChatProperties(
        @NotNull Resource systemPrompt,
        @NotNull Resource panelSystemPrompt,
        @Min(2) @Max(40) int historyMessages,
        @Min(1) @Max(50) int maxConcurrentChats,
        @NotNull Duration busyWait,
        @NotNull Duration retryOnlyWithin,
        @NotNull Duration conversationRetention,
        @NotNull ZoneId timeZone,
        boolean warmUp) {
}
