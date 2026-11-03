package com.grocery.mcpclient.assistant;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TranscriptCleanerTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            " which offers are running this week. | Which offers are running this week.",
            "do you have   fresh\tmangoes?          | Do you have fresh mangoes?",
            "[music] where is my last order?      | Where is my last order?",
            "is paneer in stock (laughs)          | Is paneer in stock",
    })
    void keepsTheQuestion_startingWithACapitalLetter(String heard, String question) {
        assertThat(TranscriptCleaner.clean(heard)).isEqualTo(question);
    }

    /** What Whisper "hears" in silence or noise (measured: 3 s of silence gave "you"). */
    @ParameterizedTest
    @ValueSource(strings = {"you", "You.", "Thank you.", "Thanks for watching!", "  bye  ", "[music]", "...", "a", "1 2 3"})
    @NullAndEmptySource
    void wordsInventedFromSilence_areNoQuestion(String heard) {
        assertThat(TranscriptCleaner.clean(heard)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Thank you, where is my order?", "Do you have bread?"})
    void aRealQuestionWithTheseWords_isKept(String heard) {
        assertThat(TranscriptCleaner.clean(heard)).isEqualTo(heard);
    }
}
