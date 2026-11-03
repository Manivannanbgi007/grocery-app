package com.grocery.mcpclient.assistant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class SpeechTextCleanerTest {

    /** Measured before: "₹58.00" was read as "rupee 58.00", "1 kg, Rs. 28" as "1K-G-R's 28". */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Amul Butter costs ₹58.00.           | Amul Butter costs 58 rupees.",
            "It costs ₹58.50 now.                | It costs 58 rupees 50 paise now.",
            "Only ₹1 more.                       | Only 1 rupee more.",
            "The TV costs ₹1,299.                | The TV costs 1299 rupees.",
            "Tata Salt, 1 kg, Rs. 28             | Tata Salt, 1 kilogram, 28 rupees",
            "Free delivery above INR 299         | Free delivery above 299 rupees",
            "Amul Butter 100 g and Milk 500 ml   | Amul Butter 100 grams and Milk 500 millilitres",
            "Oil 2 L, Atta 5kg, Ghee 1 ltr       | Oil 2 litres, Atta 5 kilograms, Ghee 1 litre",
            "Save 10% with SAVE10.               | Save 10% with SAVE10.",
            "See https://fresh-grocery.in/offers now | See now",
    })
    void writesOutWhatTheVoiceWouldReadBadly(String answer, String spoken) {
        assertThat(SpeechTextCleaner.clean(answer)).isEqualTo(spoken);
    }

    @Test
    void listLinesBecomeSentences_soTheVoicePausesAfterEach() {
        String answer = """
                Fresh Mart has these offers:
                - Amul Butter 100 g, ₹56
                - Tata Salt 1 kg, ₹28
                1. Aashirvaad Atta""";

        assertThat(SpeechTextCleaner.clean(answer)).isEqualTo(
                "Fresh Mart has these offers: Amul Butter 100 grams, 56 rupees. Tata Salt 1 kilogram, 28 rupees. Aashirvaad Atta.");
    }

    @Test
    void wordsThatOnlyLookLikeUnits_stayAsTheyAre() {
        assertThat(SpeechTextCleaner.clean("2 lakh customers, 3 large bags, gate 4 Lane"))
                .isEqualTo("2 lakh customers, 3 large bags, gate 4 Lane");
    }
}
