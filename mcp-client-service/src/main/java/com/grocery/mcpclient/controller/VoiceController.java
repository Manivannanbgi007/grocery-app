package com.grocery.mcpclient.controller;

import com.grocery.mcpclient.dto.common.ApiResponse;
import com.grocery.mcpclient.dto.request.SpeechRequest;
import com.grocery.mcpclient.dto.response.TranscriptionResponse;
import com.grocery.mcpclient.service.VoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Voice for the assistant (guests too, like the chat). A spoken question takes three calls:
 *   1. POST /transcriptions  the recording   -> its words
 *   2. POST /messages        the words       -> the answer (ChatController, exactly like a typed question)
 *   3. POST /speech          the answer      -> mp3 to play
 */
@Tag(name = "Shopping assistant - voice", description = "Turn a spoken question into text, and an answer into speech "
        + "(local speech server, English)")
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class VoiceController {

    private static final MediaType AUDIO_MPEG = MediaType.parseMediaType("audio/mpeg");

    private final VoiceService voiceService;

    @Operation(summary = "Turn a recorded question into text",
            description = "Multipart form with the part 'audio' (webm, ogg, mp4, wav ...; at most 2 MB, about 30 s of "
                    + "speech). Send the text with POST /api/v1/chat/messages.")
    @PostMapping(path = "/transcriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<TranscriptionResponse> transcribe(@RequestPart("audio") MultipartFile audio) {
        return ApiResponse.success("Question heard", voiceService.transcribe(audio));
    }

    /**
     * Answers with mp3 bytes, not the usual JSON (like the invoice PDF). No "produces" on purpose: errors must still
     * be written as JSON, so clients send "Accept: audio/mpeg, application/json".
     */
    @Operation(summary = "Read a text aloud",
            description = "Returns audio/mpeg. Errors are JSON, so send 'Accept: audio/mpeg, application/json'.")
    @PostMapping("/speech")
    public ResponseEntity<byte[]> speak(@Valid @RequestBody SpeechRequest request) {
        return ResponseEntity.ok().contentType(AUDIO_MPEG).body(voiceService.speak(request.text()));
    }
}
