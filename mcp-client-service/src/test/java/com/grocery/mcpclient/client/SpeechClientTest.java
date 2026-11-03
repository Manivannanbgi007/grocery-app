package com.grocery.mcpclient.client;

import com.grocery.mcpclient.config.VoiceProperties;
import com.grocery.mcpclient.exception.BusinessException;
import com.grocery.mcpclient.exception.VoiceUnavailableException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The calls to the speech server, against a small fake speaches (the JDK's HTTP server): what is sent, and how its
 * answers and errors are turned into ours.
 */
class SpeechClientTest {

    private HttpServer fakeSpeaches;
    private final AtomicReference<String> lastRequest = new AtomicReference<>();
    private final AtomicReference<String> lastContentType = new AtomicReference<>();

    @AfterEach
    void stopFakeSpeaches() {
        if (fakeSpeaches != null) {
            fakeSpeaches.stop(0);
        }
    }

    @Test
    void aRecordingIsSentAsMultipart_withTheModelLanguageAndSilenceFilter() throws IOException {
        SpeechClient client = clientFor("/v1/audio/transcriptions", 200, "application/json",
                "{\"text\": \"which offers are running this week.\", \"language\": \"en\"}");

        String heard = client.transcribe("RIFF-recording".getBytes(StandardCharsets.UTF_8), "question.webm", "audio/webm;codecs=opus");

        assertThat(heard).isEqualTo("which offers are running this week.");
        assertThat(lastContentType.get()).startsWith("multipart/form-data");
        assertThat(lastRequest.get())
                .contains("name=\"file\"; filename=\"question.webm\"", "Content-Type: audio/webm;codecs=opus", "RIFF-recording")
                .contains("name=\"model\"", "Systran/faster-distil-whisper-small.en")
                .contains("name=\"language\"", "name=\"vad_filter\"", "name=\"temperature\"");
    }

    @Test
    void aRecordingTheServerCannotDecode_isTheUsersProblem() throws IOException {
        SpeechClient client = clientFor("/v1/audio/transcriptions", 415, "application/json",
                "{\"detail\": \"Failed to decode audio. The provided file type is not supported.\"}");

        assertThatThrownBy(() -> client.transcribe(new byte[] {1}, "question.webm", "audio/webm"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(SpeechClient.UNREADABLE_RECORDING);
    }

    @Test
    void aMissingModel_isNotTheUsersProblem_butVoiceIsUnavailable() throws IOException {
        SpeechClient client = clientFor("/v1/audio/transcriptions", 404, "application/json",
                "{\"detail\": \"Model is not installed locally.\"}");

        assertThatThrownBy(() -> client.transcribe(new byte[] {1}, "question.webm", "audio/webm"))
                .isInstanceOf(VoiceUnavailableException.class);
    }

    @Test
    void anAnswerIsReadAloud_withTheModelAndVoiceFromTheSettings() throws IOException {
        SpeechClient client = clientFor("/v1/audio/speech", 200, "audio/mpeg", "ID3-mp3-bytes");

        byte[] mp3 = client.speak("Amul Butter costs 58 rupees.");

        assertThat(new String(mp3, StandardCharsets.UTF_8)).isEqualTo("ID3-mp3-bytes");
        assertThat(lastContentType.get()).startsWith("application/json");
        assertThat(lastRequest.get()).contains("\"model\":\"speaches-ai/Kokoro-82M-v1.0-ONNX\"", "\"voice\":\"af_heart\"",
                "\"input\":\"Amul Butter costs 58 rupees.\"", "\"response_format\":\"mp3\"", "\"speed\":1.0");
    }

    @Test
    void aServerErrorOrNoServer_meansVoiceIsUnavailable() throws IOException {
        SpeechClient failing = clientFor("/v1/audio/speech", 500, "application/json", "{\"detail\": \"boom\"}");
        assertThatThrownBy(() -> failing.speak("Hello")).isInstanceOf(VoiceUnavailableException.class);

        SpeechClient nobodyThere = new SpeechClient(RestClient.builder(), settings("http://localhost:1"));
        assertThatThrownBy(() -> nobodyThere.speak("Hello")).isInstanceOf(VoiceUnavailableException.class);
        assertThatThrownBy(() -> nobodyThere.transcribe(new byte[] {1}, "question.webm", "audio/webm"))
                .isInstanceOf(VoiceUnavailableException.class);
    }

    /** Starts a fake speaches that answers every request to {@code path} with the given status and body. */
    private SpeechClient clientFor(String path, int status, String contentType, String body) throws IOException {
        fakeSpeaches = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        fakeSpeaches.createContext(path, exchange -> {
            lastRequest.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            byte[] answer = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(status, answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
        });
        fakeSpeaches.start();
        return new SpeechClient(RestClient.builder(), settings("http://localhost:" + fakeSpeaches.getAddress().getPort()));
    }

    private static VoiceProperties settings(String baseUrl) {
        return new VoiceProperties(baseUrl, "Systran/faster-distil-whisper-small.en", "speaches-ai/Kokoro-82M-v1.0-ONNX",
                "af_heart", "en", 1.0, 2, Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(5));
    }
}
