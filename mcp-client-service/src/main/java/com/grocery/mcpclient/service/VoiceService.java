package com.grocery.mcpclient.service;

import com.grocery.mcpclient.dto.response.TranscriptionResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * Voice for the assistant: a recorded question becomes text, an answer becomes speech (local speech server).
 * The question itself is then sent like a typed one (ChatService), so the chat logic stays in one place.
 */
public interface VoiceService {

    /** The words of a recorded question (English); 400 when no question could be heard. */
    TranscriptionResponse transcribe(MultipartFile audio);

    /** The text read aloud, as mp3. */
    byte[] speak(String text);
}
