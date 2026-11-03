package com.grocery.mcpclient.assistant;

import com.grocery.mcpclient.tool.ToolCallRecorder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which product the code may add for "I want to buy X, can you order it for me?", or take out for "Can you remove X from
 * my cart?": only one the customer named in full.
 */
class PurchaseStepsTest {

    /** The user's own words (2026-10-29) */
    private static final String HARPIC_WORDS = "i want to buy this \"Harpic Toilet Cleaner\" product. can you order for me ?\n";
    private static final String HARPIC_FOUND = "{\"status\":\"OK\",\"message\":\"Found 1 product.\",\"data\":[{\"productId\":119,"
            + "\"name\":\"Harpic Toilet Cleaner\",\"packSize\":\"500 ml\",\"price\":\"₹99.75\"}]}";
    private static final String CART_WITH_HARPIC = "{\"status\":\"OK\",\"data\":{\"items\":["
            + "{\"productId\":119,\"name\":\"Harpic Toilet Cleaner\",\"packSize\":\"500 ml\",\"quantity\":1},"
            + "{\"productId\":42,\"name\":\"Aashirvaad Whole Wheat Atta\",\"packSize\":\"5 kg\",\"quantity\":2}]}}";

    @Test
    void theOneFoundProductTheCustomerNamedInFull() {
        ToolCallRecorder recorder = new ToolCallRecorder();
        recorder.recordResult("searchProducts", "{\"query\":\"Harpic Toilet Cleaner\"}", HARPIC_FOUND);

        assertThat(PurchaseSteps.productToAdd(HARPIC_WORDS, recorder)).isEqualTo("Harpic Toilet Cleaner");
        // the details of one product name it too; the previous message counts ("Cash on delivery" after the question)
        ToolCallRecorder details = new ToolCallRecorder();
        details.recordResult("getProductDetails", "{\"product\":\"Harpic\"}",
                "{\"status\":\"OK\",\"data\":{\"name\":\"Harpic Toilet Cleaner\",\"price\":\"₹99.75\"}}");
        assertThat(PurchaseSteps.productToAdd("Cash on delivery\n" + HARPIC_WORDS, details)).isEqualTo("Harpic Toilet Cleaner");
    }

    @Test
    void oneMoreOfAProductInTheCart() {
        // the user's words: the product is a line of the cart
        ToolCallRecorder recorder = new ToolCallRecorder();
        recorder.recordResult("getMyCart", "{}", "{\"status\":\"OK\",\"data\":{\"store\":\"Fresh Mart\",\"items\":["
                + "{\"productId\":1,\"name\":\"Tomato\",\"quantity\":2},"
                + "{\"productId\":42,\"name\":\"Aashirvaad Whole Wheat Atta\",\"packSize\":\"5 kg\",\"quantity\":1}]}}");

        assertThat(PurchaseSteps.productToAdd("can you add one more item for Aashirvaad Whole Wheat Atta in my cart ?\n", recorder))
                .isEqualTo("Aashirvaad Whole Wheat Atta");
    }

    @Test
    void nothingWhenTheNameIsNotInTheCustomersWords_orTwoProductsAre() {
        ToolCallRecorder recorder = new ToolCallRecorder();
        recorder.recordResult("searchProducts", "{\"query\":\"harpic\"}", HARPIC_FOUND);
        assertThat(PurchaseSteps.productToAdd("I want to buy harpic, order it for me\n", recorder)).isNull();

        ToolCallRecorder two = new ToolCallRecorder();
        two.recordResult("searchProducts", "{\"query\":\"amul\"}", "{\"status\":\"OK\",\"data\":[{\"name\":\"Amul Butter\"},"
                + "{\"name\":\"Amul Cheese\"}]}");
        assertThat(PurchaseSteps.productToAdd("Buy Amul Butter and Amul Cheese for me\n", two)).isNull();

        // a product only a refused tool or another tool mentioned does not count
        ToolCallRecorder other = new ToolCallRecorder();
        other.recordResult("getMyOrderDetails", "{}", "{\"status\":\"OK\",\"data\":{\"items\":[{\"name\":\"Harpic Toilet Cleaner\"}]}}");
        other.recordResult("searchProducts", "{}", "{\"status\":\"SERVICE_UNAVAILABLE\",\"data\":[{\"name\":\"Harpic Toilet Cleaner\"}]}");
        assertThat(PurchaseSteps.productToAdd(HARPIC_WORDS, other)).isNull();
    }

    @Test
    void theProductToRemoveIsALineOfTheCart_namedInFull() {
        ToolCallRecorder recorder = new ToolCallRecorder();
        recorder.recordResult("getMyCart", "{}", CART_WITH_HARPIC);

        assertThat(PurchaseSteps.cartLinesToRemove("Can you remove Harpic Toilet Cleaner from my cart?", recorder))
                .containsExactly("Harpic Toilet Cleaner");
        assertThat(PurchaseSteps.cartLinesToRemove("remove the harpic", recorder)).isEmpty();        // not the full name

        // a plural names a line too ("Remove the tomatoes from my cart": the model once only read the cart and claimed
        // the removal); adding stays with the exact name, as it changes the cart at once
        ToolCallRecorder tomatoes = new ToolCallRecorder();
        tomatoes.recordResult("getMyCart", "{}", "{\"status\":\"OK\",\"data\":{\"items\":[{\"productId\":1,\"name\":\"Tomato\","
                + "\"quantity\":2},{\"productId\":2,\"name\":\"Onion\",\"quantity\":1}]}}");
        assertThat(PurchaseSteps.cartLinesToRemove("Remove the tomatoes from my cart", tomatoes)).containsExactly("Tomato");
        assertThat(PurchaseSteps.cartLinesToRemove("please take the onions out", tomatoes)).containsExactly("Onion");
        assertThat(PurchaseSteps.cartLinesToRemove("i do not want tomato in my cart. can you remove it please", tomatoes))
                .containsExactly("Tomato");      // the user's words (2026-10-30)
        assertThat(PurchaseSteps.cartLinesToRemove("remove the tomatoes and the onions", tomatoes))
                .containsExactly("Tomato", "Onion");      // two lines: both go on the card
        assertThat(PurchaseSteps.productToAdd("add one more tomatoes\n", tomatoes)).isNull();

        // a product a search found is not in the cart, so it is never the one to remove
        ToolCallRecorder searched = new ToolCallRecorder();
        searched.recordResult("searchProducts", "{\"query\":\"Harpic Toilet Cleaner\"}", HARPIC_FOUND);
        assertThat(PurchaseSteps.cartLinesToRemove("Can you remove Harpic Toilet Cleaner from my cart?", searched)).isEmpty();
    }

    @Test
    void theProductsToKeepStay_andAShortNameInsideALongerOneIsNoGuess() {
        ToolCallRecorder cart = new ToolCallRecorder();
        cart.recordResult("getMyCart", "{}", "{\"status\":\"OK\",\"data\":{\"items\":[{\"productId\":1,\"name\":\"Tomato\"},"
                + "{\"productId\":216,\"name\":\"Kissan Fresh Tomato Ketchup\"},{\"productId\":3,\"name\":\"Potato\"}]}}");

        assertThat(PurchaseSteps.cartLinesToRemove("Remove Tomato and Potato from my cart, but keep the ketchup", cart))
                .containsExactly("Tomato", "Potato");
        // "tomato ketchup" is the ketchup, not the plain Tomato; the code does not guess the ketchup's full name either
        assertThat(PurchaseSteps.cartLinesToRemove("i do not want the tomato ketchup, remove it", cart)).isEmpty();
        assertThat(PurchaseSteps.cartLinesToRemove("remove tomato from my cart", cart)).containsExactly("Tomato");
        assertThat(PurchaseSteps.cartLinesToRemove("remove the kissan fresh tomato ketchup", cart))
                .containsExactly("Kissan Fresh Tomato Ketchup");
    }

    @Test
    void aRemovalIsPreparedForALineWhenAToolPreparedItsName() {
        ToolCallRecorder recorder = new ToolCallRecorder();
        recorder.recordResult("prepareMyCartRemoval", "{\"product\":\"harpic\"}", ChatActionsTest.REMOVAL);
        recorder.recordResult("prepareMyCartRemoval", "{\"product\":\"mango\"}", "{\"status\":\"NOT_FOUND\"}");

        assertThat(PurchaseSteps.removalPrepared("Harpic Toilet Cleaner", recorder)).isTrue();
        assertThat(PurchaseSteps.removalPrepared("harpic toilet cleaner", recorder)).isTrue();
        assertThat(PurchaseSteps.removalPrepared("Tomato", recorder)).isFalse();
    }

    @Test
    void twoPackSizesOfTheProductToRemoveGetAQuestion() {
        ToolCallRecorder recorder = new ToolCallRecorder();
        recorder.recordResult("prepareMyCartRemoval", "{\"product\":\"Harpic Toilet Cleaner\"}", "{\"status\":\"CHOOSE\","
                + "\"message\":\"The cart has several products that match.\",\"data\":{\"options\":["
                + "{\"productId\":119,\"name\":\"Harpic Toilet Cleaner\",\"packSize\":\"500 ml\"},"
                + "{\"productId\":120,\"name\":\"Harpic Toilet Cleaner\",\"packSize\":\"1 l\"}]}}");

        assertThat(PurchaseSteps.whichOneToRemove(recorder.calls().getFirst())).isEqualTo("Which one do you want to take out "
                + "of your cart: Harpic Toilet Cleaner (500 ml) or Harpic Toilet Cleaner (1 l)?");
    }
}
