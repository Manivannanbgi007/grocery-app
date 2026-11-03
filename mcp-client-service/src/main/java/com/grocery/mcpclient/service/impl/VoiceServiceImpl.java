package com.grocery.mcpclient.service.impl;

import com.grocery.mcpclient.assistant.SpeechTextCleaner;
import com.grocery.mcpclient.assistant.TranscriptCleaner;
import com.grocery.mcpclient.client.SpeechClient;
import com.grocery.mcpclient.config.VoiceProperties;
import com.grocery.mcpclient.dto.response.TranscriptionResponse;
import com.grocery.mcpclient.exception.AssistantBusyException;
import com.grocery.mcpclient.exception.BusinessException;
import com.grocery.mcpclient.service.VoiceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Checks the request, waits for a free voice slot, calls the speech server and cleans what comes back.
 *
 * At most app.voice.max-concurrent recordings and answers are processed at the same time: speech runs on the CPU
 * that Ollama needs too. The words a user said are never logged (only sizes), and recordings are not stored.
 */
@Slf4j
@Service
public class VoiceServiceImpl implements VoiceService {

    static final String NOTHING_HEARD = "I couldn't hear a question. Please try again, a little closer to the microphone.";
    static final String VOICE_BUSY = "Voice is busy right now. Please try again in a moment, or type your question.";
    private static final String DEFAULT_FILENAME = "question.webm";

    private final SpeechClient speechClient;
    private final VoiceProperties properties;
    private final Semaphore voiceSlots;

    public VoiceServiceImpl(SpeechClient speechClient, VoiceProperties properties) {
        this.speechClient = speechClient;
        this.properties = properties;
        this.voiceSlots = new Semaphore(properties.maxConcurrent(), true);
    }

    @Override
    public TranscriptionResponse transcribe(MultipartFile audio) {
        String contentType = audio.getContentType();
        if (audio.isEmpty()) {
            throw new BusinessException("The recording is empty. Please try again.");
        }
        if (contentType == null || !contentType.startsWith("audio/")) {
            throw new BusinessException("Please send an audio recording (audio/webm, audio/ogg, audio/mp4 ...)");
        }
        byte[] recording = readBytes(audio);
        String filename = audio.getOriginalFilename() == null || audio.getOriginalFilename().isBlank()
                ? DEFAULT_FILENAME : audio.getOriginalFilename();

        String heard = withVoiceSlot(() -> speechClient.transcribe(recording, filename, contentType));
        String question = TranscriptCleaner.clean(heard);
        if (question.isEmpty()) {
            throw new BusinessException(NOTHING_HEARD);
        }
        log.info("Voice: a recording of {} KB ({}) became a question of {} characters",
                recording.length / 1024, contentType, question.length());
        return new TranscriptionResponse(question);
    }

    @Override
    public byte[] speak(String text) {
        String spoken = SpeechTextCleaner.clean(text);
        if (spoken.isEmpty()) {
            throw new BusinessException("There is nothing to read aloud.");
        }
        byte[] mp3 = withVoiceSlot(() -> speechClient.speak(spoken));
        log.info("Voice: {} characters read aloud ({} KB of mp3)", spoken.length(), mp3.length / 1024);
        return mp3;
    }

    /** Runs the work when a voice slot is free within app.voice.busy-wait, otherwise answers "busy" (503). */
    private <T> T withVoiceSlot(Supplier<T> work) {
        try {
            if (!voiceSlots.tryAcquire(properties.busyWait().toMillis(), TimeUnit.MILLISECONDS)) {
                throw new AssistantBusyException(VOICE_BUSY);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssistantBusyException(VOICE_BUSY);
        }
        try {
            return work.get();
        } finally {
            voiceSlots.release();
        }
    }

    private static byte[] readBytes(MultipartFile audio) {
        try {
            return audio.getBytes();
        } catch (IOException ex) {
            throw new BusinessException("The recording could not be read. Please try again.");
        }
    }
}
