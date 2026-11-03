package com.grocery.mcpclient.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.grocery.mcpclient.config.VoiceProperties;
import com.grocery.mcpclient.exception.BusinessException;
import com.grocery.mcpclient.exception.VoiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;

/**
 * Talks to the local speech server (speaches, docker-compose profile "ai"). It has the same endpoints as OpenAI's
 * audio API, so another OpenAI-compatible server would work too:
 *
 *   POST /v1/audio/transcriptions   recording -> text   (Whisper)
 *   POST /v1/audio/speech           text -> mp3         (Kokoro)
 *
 * A recording speaches cannot decode (415) is the user's problem: 400 with a clear message. Anything else (not
 * running, too slow, a model missing) means voice is unavailable: 503, and the user can still type.
 * Own timeouts (app.voice.*-timeout) instead of the service-wide 120 s that Ollama needs.
 */
@Slf4j
@Component
public class SpeechClient {

    static final String UNREADABLE_RECORDING = "Sorry, I could not understand the recording. Please try again.";

    private final VoiceProperties properties;
    private final RestClient restClient;

    public SpeechClient(RestClient.Builder restClientBuilder, VoiceProperties properties) {
        this.properties = properties;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
    }

    /**
     * @param contentType what the browser recorded, for example "audio/webm;codecs=opus"
     * @return the words speaches heard; empty when it heard none
     */
    public String transcribe(byte[] audio, String filename, String contentType) {
        MultipartBodyBuilder form = new MultipartBodyBuilder();
        form.part("file", new ByteArrayResource(audio)).filename(filename).contentType(MediaType.parseMediaType(contentType));
        form.part("model", properties.transcriptionModel());
        form.part("language", properties.language());
        form.part("response_format", "json");
        form.part("temperature", "0");        // the most likely words, no "creative" guesses
        form.part("vad_filter", "true");      // leave out silence and noise: without it Whisper "hears" words like "you"
        try {
            Transcription transcription = restClient.post()
                    .uri("/v1/audio/transcriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form.build())
                    .retrieve()
                    .body(Transcription.class);
            return transcription == null || transcription.text() == null ? "" : transcription.text();
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.UNSUPPORTED_MEDIA_TYPE || ex.getStatusCode() == HttpStatus.BAD_REQUEST) {
                log.info("The speech server could not read a recording ({} bytes, {}): {}", audio.length, contentType,
                        ex.getResponseBodyAsString());
                throw new BusinessException(UNREADABLE_RECORDING);
            }
            throw new VoiceUnavailableException(ex);   // 404 model not installed, 422 bad setting ...
        } catch (RestClientException ex) {
            throw new VoiceUnavailableException(ex);
        }
    }

    /** @return the text read aloud, as mp3 */
    public byte[] speak(String text) {
        try {
            byte[] mp3 = restClient.post()
                    .uri("/v1/audio/speech")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SpeechBody(properties.speechModel(), text, properties.voice(), "mp3", properties.speed()))
                    .retrieve()
                    .body(byte[].class);
            if (mp3 == null || mp3.length == 0) {
                throw new RestClientException("The speech server returned no audio");
            }
            return mp3;
        } catch (RestClientException ex) {
            throw new VoiceUnavailableException(ex);
        }
    }

    /** speaches' answer to a transcription (response_format=json); other fields are not needed. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Transcription(String text) {
    }

    /** The request to read a text aloud (OpenAI's field names). */
    record SpeechBody(String model, String input, String voice,
                              @JsonProperty("response_format") String responseFormat, double speed) {
    }
}
