package com.grocery.product.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class SlugUtilsTest {

    @ParameterizedTest
    @CsvSource({
            "Dairy & Eggs, dairy-eggs",
            "Snacks & Beverages, snacks-beverages",
            "'  Fresh   Fruits!! ', fresh-fruits",
            "Café Specials, cafe-specials"
    })
    void toSlug_makesUrlFriendlyNames(String name, String expectedSlug) {
        assertThat(SlugUtils.toSlug(name)).isEqualTo(expectedSlug);
    }
}
