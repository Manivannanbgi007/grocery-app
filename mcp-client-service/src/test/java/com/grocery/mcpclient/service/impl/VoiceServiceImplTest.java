package com.grocery.mcpclient.service.impl;

import com.grocery.mcpclient.client.SpeechClient;
import com.grocery.mcpclient.config.VoiceProperties;
import com.grocery.mcpclient.exception.AssistantBusyException;
import com.grocery.mcpclient.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VoiceServiceImplTest {

    private final SpeechClient speechClient = mock(SpeechClient.class);
    private final VoiceServiceImpl voiceService = new VoiceServiceImpl(speechClient, settings(1));

    @Test
    void aRecordingBecomesACleanedQuestion() {
        when(speechClient.transcribe(any(), anyString(), anyString())).thenReturn(" which offers are running this week. ");

        String question = voiceService.transcribe(recording("audio/webm;codecs=opus", new byte[] {1, 2})).text();

        assertThat(question).isEqualTo("Which offers are running this week.");
        verify(speechClient).transcribe(new byte[] {1, 2}, "question.webm", "audio/webm;codecs=opus");
    }

    @Test
    void silenceOrNoise_isNoQuestion() {
        when(speechClient.transcribe(any(), anyString(), anyString())).thenReturn("Thank you.");

        assertThatThrownBy(() -> voiceService.transcribe(recording("audio/webm", new byte[] {1})))
                .isInstanceOf(BusinessException.class)
                .hasMessage(VoiceServiceImpl.NOTHING_HEARD);
    }

    @Test
    void anEmptyFileOrNoAudio_neverReachesTheSpeechServer() {
        assertThatThrownBy(() -> voiceService.transcribe(recording("audio/webm", new byte[0])))
                .isInstanceOf(BusinessException.class).hasMessageContaining("empty");
        assertThatThrownBy(() -> voiceService.transcribe(recording("text/plain", new byte[] {1})))
                .isInstanceOf(BusinessException.class).hasMessageContaining("audio recording");
        verifyNoInteractions(speechClient);
    }

    @Test
    void theAnswerIsMadeEasyToListenTo_beforeItIsReadAloud() {
        when(speechClient.speak(anyString())).thenReturn(new byte[] {9});

        assertThat(voiceService.speak("Amul Butter costs ₹58.00 for 100 g.")).containsExactly(9);
        verify(speechClient).speak("Amul Butter costs 58 rupees for 100 grams.");
    }

    @Test
    void whenTheOnlyVoiceSlotStaysBusy_theNextRequestIsTurnedAway() throws InterruptedException {
        CountDownLatch working = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        when(speechClient.speak(anyString())).thenAnswer(call -> {
            working.countDown();
            finish.await(5, TimeUnit.SECONDS);
            return new byte[] {1};
        });
        Thread first = Thread.ofVirtual().start(() -> voiceService.speak("first answer"));
        assertThat(working.await(5, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> voiceService.speak("second answer"))
                .isInstanceOf(AssistantBusyException.class)
                .hasMessage(VoiceServiceImpl.VOICE_BUSY);

        finish.countDown();
        first.join();
        assertThat(voiceService.speak("third answer")).containsExactly(1);   // the slot is free again
    }

    private static MockMultipartFile recording(String contentType, byte[] bytes) {
        return new MockMultipartFile("audio", "question.webm", contentType, bytes);
    }

    private static VoiceProperties settings(int maxConcurrent) {
        return new VoiceProperties("http://localhost:8000", "Systran/faster-distil-whisper-small.en",
                "speaches-ai/Kokoro-82M-v1.0-ONNX", "af_heart", "en", 1.0, maxConcurrent,
                Duration.ofMillis(100), Duration.ofSeconds(1), Duration.ofSeconds(5));
    }
}
