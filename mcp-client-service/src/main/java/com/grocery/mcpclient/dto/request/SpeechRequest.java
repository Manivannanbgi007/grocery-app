package com.grocery.mcpclient.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A text to read aloud, usually an answer of the assistant (at most about 1600 characters: num-predict 400 tokens).
 */
public record SpeechRequest(
        @NotBlank(message = "Please give the text to read aloud")
        @Size(max = SpeechRequest.MAX_LENGTH, message = "At most " + SpeechRequest.MAX_LENGTH + " characters can be read aloud")
        String text) {

    public static final int MAX_LENGTH = 2000;
}
