package com.grocery.mcpclient.assistant;

import com.grocery.mcpclient.tool.ToolCallRecorder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The answer about several products to take out of the cart: the products on the card, then each product that could not
 * be prepared, in the shop's own words.
 */
class RemovalReportTest {

    /** prepareMyCartRemoval's NOT_FOUND, with the list of the cart's products that follows its first sentence */
    private static final String MANGO_NOT_IN_THE_CART = "{\"status\":\"NOT_FOUND\",\"message\":\"The cart has no product called "
            + "\\\"mango\\\". The cart has: Tomato (1 kg), Harpic Toilet Cleaner (500 ml).\"}";

    @Test
    void oneProductIsNoReport() {
        ToolCallRecorder recorder = new ToolCallRecorder();
        recorder.recordResult("prepareMyCartRemoval", "{\"product\":\"tomato\"}", ChatActionsTest.TOMATO_REMOVAL);

        assertThat(RemovalReport.needed(recorder)).isFalse();      // the model's answer (or the fixed one) stays
    }

    @Test
    void twoProductsOnTheCard() {
        ToolCallRecorder recorder = new ToolCallRecorder();
        recorder.recordResult("prepareMyCartRemoval", "{\"product\":\"Harpic Toilet Cleaner\"}", ChatActionsTest.REMOVAL);
        recorder.recordResult("prepareMyCartRemoval", "{\"product\":\"tomato\"}", ChatActionsTest.TOMATO_REMOVAL);

        assertThat(RemovalReport.needed(recorder)).isTrue();
        assertThat(RemovalReport.of(recorder)).isEqualTo("Here are the products you want to take out of your cart: Harpic "
                + "Toilet Cleaner (500 ml) and Tomato (1 kg). Please check them and press Confirm to remove them; nothing is "
                + "removed before that.");
    }

    @Test
    void aProductThatIsNotInTheCart_isSaidApartFromTheCard() {
        // "Remove Tomato and Mango from my cart": never "press Confirm to remove Tomato and Mango"
        ToolCallRecorder recorder = new ToolCallRecorder();
        recorder.recordResult("prepareMyCartRemoval", "{\"product\":\"tomato\"}", ChatActionsTest.TOMATO_REMOVAL);
        recorder.recordResult("prepareMyCartRemoval", "{\"product\":\"mango\"}", MANGO_NOT_IN_THE_CART);

        assertThat(RemovalReport.of(recorder)).isEqualTo("Here is the product you want to take out of your cart: Tomato "
                + "(1 kg). Please check it and press Confirm to remove it; nothing is removed before that.\n"
                + "The cart has no product called \"mango\".");
    }

    @Test
    void nothingOnTheCard_onlyTheShopsSentences() {
        ToolCallRecorder recorder = new ToolCallRecorder();
        recorder.recordResult("prepareMyCartRemoval", "{\"product\":\"mango\"}", MANGO_NOT_IN_THE_CART);
        recorder.recordResult("prepareMyCartRemoval", "{\"product\":\"kiwi\"}", MANGO_NOT_IN_THE_CART.replace("mango", "kiwi"));

        assertThat(RemovalReport.of(recorder)).isEqualTo("The cart has no product called \"mango\".\n"
                + "The cart has no product called \"kiwi\".");
    }

    @Test
    void wordsForSeveralProducts_preparedOneByOneAfterwards_areNoProblem() {
        // the model sent both names in one call (NOT_FOUND), then the code prepared each product
        ToolCallRecorder recorder = new ToolCallRecorder();
        recorder.recordResult("prepareMyCartRemoval", "{\"product\":\"tomato and harpic\"}",
                "{\"status\":\"NOT_FOUND\",\"message\":\"The cart has no product called \\\"tomato and harpic\\\".\"}");
        recorder.recordResult("prepareMyCartRemoval", "{\"product\":\"Tomato\"}", ChatActionsTest.TOMATO_REMOVAL);
        recorder.recordResult("prepareMyCartRemoval", "{\"product\":\"Harpic Toilet Cleaner\"}", ChatActionsTest.REMOVAL);

        assertThat(RemovalReport.of(recorder)).doesNotContain("no product called")
                .startsWith("Here are the products you want to take out of your cart: Tomato (1 kg) and Harpic Toilet Cleaner");
    }

    @Test
    void twoPackSizesOfAProductGetTheWhichOneQuestion() {
        ToolCallRecorder recorder = new ToolCallRecorder();
        recorder.recordResult("prepareMyCartRemoval", "{\"product\":\"tomato\"}", ChatActionsTest.TOMATO_REMOVAL);
        recorder.recordResult("prepareMyCartRemoval", "{\"product\":\"Amul Butter\"}", "{\"status\":\"CHOOSE\",\"message\":\"The "
                + "cart has several products that match.\",\"data\":{\"options\":[{\"productId\":5,\"name\":\"Amul Butter\","
                + "\"packSize\":\"100 g\"},{\"productId\":7,\"name\":\"Amul Butter\",\"packSize\":\"500 g\"}]}}");

        assertThat(RemovalReport.of(recorder)).endsWith("\nWhich one do you want to take out of your cart: Amul Butter (100 g) "
                + "or Amul Butter (500 g)?");
    }

    @Test
    void noReportNextToACardOfAnotherKind() {
        ToolCallRecorder recorder = new ToolCallRecorder();
        recorder.recordResult("prepareMyCartRemoval", "{\"product\":\"tomato\"}", ChatActionsTest.TOMATO_REMOVAL);
        recorder.recordResult("prepareMyCartRemoval", "{\"product\":\"harpic\"}", ChatActionsTest.REMOVAL);
        recorder.recordResult("prepareMyCartClear", "{}", "{\"status\":\"OK\",\"data\":{\"items\":[{\"name\":\"Tomato\","
                + "\"quantity\":2}]}}");

        assertThat(RemovalReport.needed(recorder)).isFalse();      // the card is about emptying the cart
    }
}
