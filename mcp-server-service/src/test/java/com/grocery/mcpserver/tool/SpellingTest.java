package com.grocery.mcpserver.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A small spelling mistake is the same word; anything more, or another first letter, is another word. (Short words that
 * differ in one letter, like "rice" and "rich", count as close too: that is why ProductChoice tries the words as they
 * are written first, and allows typos only when nothing fits them.)
 */
class SpellingTest {

    @ParameterizedTest
    @CsvSource({
            "tomoto, tomato",       // a letter replaced
            "tomatto, tomato",      // a letter too many
            "tomat, tomato",        // a letter left out
            "poteto, potato",
            "bananna, banana",
            "orgnic, organic",
            "buter, butter",
            "tomato, tomato"})
    void aSmallTypoIsTheSameWord(String said, String word) {
        assertThat(Spelling.isClose(said, word)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "milk, silk",           // another first letter
            "tea, pea",
            "tomoto, potato",
            "buter, bitter",        // 5 letters: only one may be wrong
            "ab, ac",               // two letters: no typo allowed
            "onion, orange",
            "carrot, cabbage"})
    void anotherWordIsNoTypo(String said, String word) {
        assertThat(Spelling.isClose(said, word)).isFalse();
    }

    @Test
    void theDistanceCountsTheLettersToChange() {
        assertThat(Spelling.distance("tomoto", "tomato")).isEqualTo(1);
        assertThat(Spelling.distance("bananna", "banana")).isEqualTo(1);
        assertThat(Spelling.distance("tomtao", "tomato")).isEqualTo(2);
        assertThat(Spelling.distance("", "tea")).isEqualTo(3);
    }
}
