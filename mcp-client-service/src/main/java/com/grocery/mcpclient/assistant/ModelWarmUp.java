package com.grocery.mcpclient.assistant;

import com.grocery.mcpclient.config.ChatProperties;
import com.grocery.mcpclient.context.Audience;
import com.grocery.mcpclient.tool.GroceryToolsClient;
import com.grocery.mcpclient.tool.ToolCallRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Asks the model one simple question right after start-up (in the background), so Ollama loads the model and
 * caches the system prompt and the tool list. Without it, the first customer waited about 90 seconds on a CPU;
 * afterwards answers take a few seconds. A failure here is only logged: the service works without it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelWarmUp {

    private final ChatProperties chatProperties;
    private final GroceryToolsClient groceryTools;
    private final ShoppingAssistant assistant;

    @EventListener(ApplicationReadyEvent.class)
    public void warmUpInBackground() {
        if (chatProperties.warmUp()) {
            Thread.ofVirtual().name("model-warm-up").start(this::warmUp);
        }
    }

    void warmUp() {
        long startedAt = System.currentTimeMillis();
        try {
            assistant.answer(List.of(), "Hello", groceryTools.toolsFor(new ToolCallRecorder(), Audience.GUEST), Audience.GUEST, null);
            log.info("Model warm-up finished in {} ms", System.currentTimeMillis() - startedAt);
        } catch (RuntimeException ex) {
            log.info("Model warm-up skipped: {}", ex.getCause() == null ? ex.toString() : ex.getCause().toString());
        }
    }
}
