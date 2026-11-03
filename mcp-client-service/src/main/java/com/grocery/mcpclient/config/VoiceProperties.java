package com.grocery.mcpclient.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Settings under "app.voice" in application.yml: the local speech server (speaches, docker-compose profile "ai") that
 * turns a spoken question into text and an answer into speech (docs/ai-assistant-plan.md, "Voice").
 *
 * @param baseUrl            where speaches runs (http://localhost:8000 locally)
 * @param transcriptionModel the Whisper model that understands speech (an English-only one: smaller and faster)
 * @param speechModel        the model that speaks (Kokoro)
 * @param voice              the Kokoro voice; GET {baseUrl}/v1/audio/voices lists them
 * @param language           the language of the questions ("en")
 * @param speed              how fast answers are read: 1.0 = normal, 0.5 to 2.0
 * @param maxConcurrent      recordings and answers turned into text or speech at the same time; it all runs on the
 *                           CPU that Ollama needs too, so more would only make every user wait longer
 * @param busyWait           how long a request waits for a free slot before the answer is "busy, try again"
 * @param connectTimeout     how long to wait for a connection to speaches
 * @param readTimeout        how long to wait for its answer; shorter than the gateway's 60 s for the voice routes, so
 *                           the user gets our clear message instead of a gateway timeout
 */
@Validated
@ConfigurationProperties(prefix = "app.voice")
public record VoiceProperties(
        @NotBlank String baseUrl,
        @NotBlank String transcriptionModel,
        @NotBlank String speechModel,
        @NotBlank String voice,
        @NotBlank String language,
        @DecimalMin("0.5") @DecimalMax("2.0") double speed,
        @Min(1) @Max(20) int maxConcurrent,
        @NotNull Duration busyWait,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout) {
}
