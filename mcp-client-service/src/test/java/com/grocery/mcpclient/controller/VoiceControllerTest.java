package com.grocery.mcpclient.controller;

import com.grocery.mcpclient.dto.response.TranscriptionResponse;
import com.grocery.mcpclient.exception.AssistantBusyException;
import com.grocery.mcpclient.exception.BusinessException;
import com.grocery.mcpclient.exception.VoiceUnavailableException;
import com.grocery.mcpclient.service.VoiceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The voice endpoints: what goes in, what comes out, and that every error is JSON (also for the mp3 endpoint). */
@WebMvcTest(VoiceController.class)
class VoiceControllerTest {

    private static final MockMultipartFile RECORDING =
            new MockMultipartFile("audio", "question.webm", "audio/webm;codecs=opus", new byte[] {1, 2, 3});
    private static final String AUDIO_OR_JSON = "audio/mpeg, application/json";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VoiceService voiceService;

    @Test
    void aRecordingBecomesTheQuestionsText() throws Exception {
        when(voiceService.transcribe(any())).thenReturn(new TranscriptionResponse("Which offers are running this week?"));

        mockMvc.perform(multipart("/api/v1/chat/transcriptions").file(RECORDING))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.text").value("Which offers are running this week?"));
    }

    @Test
    void aRecordingNeedsThePartAudio_sentAsMultipart() throws Exception {
        mockMvc.perform(multipart("/api/v1/chat/transcriptions")
                        .file(new MockMultipartFile("file", "question.webm", "audio/webm", new byte[] {1})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Required file 'audio' is missing"));

        mockMvc.perform(post("/api/v1/chat/transcriptions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(voiceService);
    }

    @Test
    void aRecordingThatIsTooLarge_orHoldsNoQuestion_isA400() throws Exception {
        // MockMvc does not check the multipart size limit itself, so the exception Spring would throw is simulated
        when(voiceService.transcribe(any())).thenThrow(new MaxUploadSizeExceededException(2 * 1024 * 1024));
        mockMvc.perform(multipart("/api/v1/chat/transcriptions").file(RECORDING))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The recording is too long. Please ask a shorter question (at most 30 seconds)."));

        doThrow(new BusinessException("I couldn't hear a question.")).when(voiceService).transcribe(any());
        mockMvc.perform(multipart("/api/v1/chat/transcriptions").file(RECORDING))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("I couldn't hear a question."));
    }

    @Test
    void aTextIsReadAloudAsMp3() throws Exception {
        when(voiceService.speak("Amul Butter costs ₹58.")).thenReturn(new byte[] {(byte) 0xFF, (byte) 0xFB, 0x10});

        mockMvc.perform(post("/api/v1/chat/speech").header(HttpHeaders.ACCEPT, AUDIO_OR_JSON)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\": \"Amul Butter costs ₹58.\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/mpeg"))
                .andExpect(content().bytes(new byte[] {(byte) 0xFF, (byte) 0xFB, 0x10}));
    }

    @Test
    void theTextToReadAloud_mustBeThereAndNotTooLong() throws Exception {
        mockMvc.perform(post("/api/v1/chat/speech").header(HttpHeaders.ACCEPT, AUDIO_OR_JSON)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\": \"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errors[0].message").value("Please give the text to read aloud"));

        mockMvc.perform(post("/api/v1/chat/speech").header(HttpHeaders.ACCEPT, AUDIO_OR_JSON)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\": \"" + "a".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("At most 2000 characters can be read aloud"));
        verifyNoInteractions(voiceService);
    }

    @Test
    void whenTheSpeechServerIsDown_theMp3EndpointStillAnswersWithJson() throws Exception {
        when(voiceService.speak(anyString())).thenThrow(new VoiceUnavailableException(new RuntimeException("connection refused")));

        mockMvc.perform(post("/api/v1/chat/speech").header(HttpHeaders.ACCEPT, AUDIO_OR_JSON)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\": \"Hello\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Voice is not available right now. Please type your question."));
    }

    @Test
    void whenAllVoiceSlotsAreBusy_theClientIsToldWhenToTryAgain() throws Exception {
        when(voiceService.transcribe(any())).thenThrow(new AssistantBusyException("Voice is busy right now."));

        mockMvc.perform(multipart("/api/v1/chat/transcriptions").file(RECORDING))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "10"))
                .andExpect(jsonPath("$.message").value("Voice is busy right now."));
    }
}
