package com.grocery.mcpclient.assistant;

import com.grocery.mcpclient.tool.ChangeRequest;
import com.grocery.mcpclient.tool.ToolCallRecorder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The answer about adding products, written from the prepareMyCartAdd results: the products on the card first, then the
 * shop's own sentence (or the question) for each product that is not on it. Nothing is added by it: the card's button adds.
 */
class AddReportTest {

    /** prepareMyCartAdd's OK for one product (mcp-server-service's CartAddPreview) */
    static String added(long productId, String name, String packSize, int packs, String price, int newQuantity) {
        return "{\"status\":\"OK\",\"message\":\"Adding prepared; NOTHING is added yet.\",\"data\":{\"store\":\"Fresh Mart\","
                + "\"items\":[{\"productId\":" + productId + ",\"name\":\"" + name + "\",\"packSize\":\"" + packSize
                + "\",\"quantity\":" + packs + ",\"lineTotal\":\"" + price + "\"}],\"confirm\":{\"productId\":" + productId
                + ",\"quantity\":" + packs + ",\"newQuantity\":" + newQuantity + "}}}";
    }

    private static final String CARROT_REFUSED = "{\"status\":\"REFUSED\",\"message\":\"The cart has products from Fresh Mart, and "
            + "Organic Carrot is sold by Green Basket. A cart can hold the products of one store only.\"}";

    /** prepareMyCartAdd without an amount: it found the product and asks how much */
    static String howMuch(long productId, String name, String packSize, String price) {
        return "{\"status\":\"CHOOSE\",\"message\":\"How much " + name + " does the customer want?\",\"data\":{\"amountFor\":"
                + "{\"productId\":" + productId + ",\"name\":\"" + name + "\",\"packSize\":\"" + packSize + "\",\"price\":\""
                + price + "\"}}}";
    }

    @Test
    void noAmount_theQuestionNamesTheCatalogsProduct_once() {
        // "tomato" by the model and "tomoto" by the code: both found Tomato, and the customer gets one question
        ToolCallRecorder recorder = recorder("Add tomoto in my cart?");
        recorder.recordResult("prepareMyCartAdd", "{\"product\":\"tomato\",\"quantity\":1,\"unit\":\"kg\"}", howMuch(1, "Tomato", "1 kg", "₹36"));
        recorder.recordResult("prepareMyCartAdd", "{\"product\":\"tomoto\"}", howMuch(1, "Tomato", "1 kg", "₹36"));

        assertThat(AddReport.of(recorder)).isEqualTo("How much Tomato would you like to add? One pack is 1 kg for ₹36. Nothing is added yet.");
        assertThat(PendingAdd.askedIn(recorder)).isEqualTo(new PendingAdd(1, "Tomato"));
    }

    @Test
    void aCardForTheProduct_isNoQuestionAboutIt_comparedByItsId() {
        // the model asked for "tomoto" without an amount, the code then prepared "tomato 2 kg": Tomato is on the card
        ToolCallRecorder recorder = recorder("add 2 kg tomoto");
        recorder.recordResult("prepareMyCartAdd", "{\"product\":\"tomoto\"}", howMuch(1, "Tomato", "1 kg", "₹36"));
        recorder.recordResult("prepareMyCartAdd", "{\"product\":\"tomoto\",\"quantity\":2,\"unit\":\"kg\"}", added(1, "Tomato", "1 kg", 2, "₹72", 2));

        assertThat(AddReport.of(recorder)).isEqualTo("Here is the product to add to your cart: 2 x Tomato (1 kg) for ₹72. "
                + "Please check it and press Confirm to add it; nothing is added before that.");
        assertThat(PendingAdd.askedIn(recorder)).isNull();      // nothing waits: it is on the card
    }

    @Test
    void questionsAboutTwoProducts_leaveNothingPending() {
        // "add tomato and onion": an answer like "2 kg" would not say which one
        ToolCallRecorder recorder = recorder("add tomato and onion");
        recorder.recordResult("prepareMyCartAdd", "{\"product\":\"tomato\"}", howMuch(1, "Tomato", "1 kg", "₹36"));
        recorder.recordResult("prepareMyCartAdd", "{\"product\":\"onion\"}", howMuch(2, "Onion", "1 kg", "₹40"));

        assertThat(AddReport.of(recorder)).isEqualTo("""
                How much Tomato would you like to add? One pack is 1 kg for ₹36. Nothing is added yet.
                How much Onion would you like to add? One pack is 1 kg for ₹40. Nothing is added yet.""");
        assertThat(PendingAdd.askedIn(recorder)).isNull();
    }

    @Test
    void oneProduct_theCardsOwnWords() {
        ToolCallRecorder recorder = recorder("add 2 kg tomatoes");
        recorder.recordResult("prepareMyCartAdd", "{\"product\":\"tomatoes\",\"quantity\":2,\"unit\":\"kg\"}",
                added(1, "Tomato", "1 kg", 2, "₹72", 2));

        assertThat(AddReport.needed(recorder)).isFalse();       // one product: the model's good answer may stay
        assertThat(AddReport.of(recorder)).isEqualTo("Here is the product to add to your cart: 2 x Tomato (1 kg) for ₹72. "
                + "Please check it and press Confirm to add it; nothing is added before that.");
    }

    @Test
    void severalProducts_oneSentenceForTheCard_thenTheShopsReasonForTheOthers() {
        ToolCallRecorder recorder = recorder("add tomato 1kg, onion 2 kg and Organic Carrot 2 kg");
        recorder.recordResult("prepareMyCartAdd", "{\"product\":\"tomato\",\"quantity\":1,\"unit\":\"kg\"}",
                added(1, "Tomato", "1 kg", 1, "₹36", 1));
        recorder.recordResult("prepareMyCartAdd", "{\"product\":\"onion\",\"quantity\":2,\"unit\":\"kg\"}",
                added(2, "Onion", "1 kg", 2, "₹80", 3));
        recorder.recordResult("prepareMyCartAdd", "{\"product\":\"Organic Carrot\",\"quantity\":2,\"unit\":\"kg\"}", CARROT_REFUSED);

        assertThat(AddReport.needed(recorder)).isTrue();
        assertThat(AddReport.of(recorder)).isEqualTo("""
                Here are the products to add to your cart: 1 x Tomato (1 kg) for ₹36 and 2 x Onion (1 kg) for ₹80 (that makes \
                3 in your cart). Please check them and press Confirm to add them; nothing is added before that.
                The cart has products from Fresh Mart, and Organic Carrot is sold by Green Basket. A cart can hold the products \
                of one store only.""");
    }

    @Test
    void aProductThatNeedsTheCustomersAnswer_getsTheQuestion_andNothingIsAddedForIt() {
        ToolCallRecorder recorder = recorder("add milk, 2k tomatoes and some bread");
        recorder.recordResult("prepareMyCartAdd", "{\"product\":\"milk\"}", "{\"status\":\"CHOOSE\",\"message\":\"Several products "
                + "are called milk.\",\"data\":{\"options\":[{\"name\":\"Amul Taaza Toned Milk\",\"packSize\":\"500 ml\"},"
                + "{\"name\":\"Akshayakalpa Organic Milk\",\"packSize\":\"1 L\"}]}}");
        String unsaid = ChangeRequest.unsaidAmount("add 2k tomatoes", 2, "kg").replace("\"", "\\\"");
        recorder.recordResult("prepareMyCartAdd", "{\"product\":\"tomatoes\",\"quantity\":2,\"unit\":\"kg\"}",
                "{\"status\":\"CHOOSE\",\"message\":\"" + unsaid + "\"}");
        recorder.recordFailure("prepareMyCartAdd", "{\"product\":\"bread\"}");

        assertThat(AddReport.of(recorder)).isEqualTo("""
                Which milk do you mean: Amul Taaza Toned Milk (500 ml) or Akshayakalpa Organic Milk (1 L)? Nothing is added for it yet.
                How much tomatoes do you want (for example 2 kg or 2 packs)? Nothing is added for it yet.
                I could not check bread just now. Please ask me again in a few minutes.""");
        assertThat(ChatActions.of(recorder)).isNull();       // no card
    }

    @Test
    void productsOfTwoStores_theCardKeepsTheFirstStore_andSaysWhyTheOtherIsNotOnIt() {
        // an empty cart: each product alone could go in, but a cart holds the products of one store only
        ToolCallRecorder recorder = recorder("Can you add Tomato 1 kg and Organic Carrot 2 kg to my cart?");
        recorder.recordResult("prepareMyCartAdd", "{\"product\":\"Tomato\",\"quantity\":1,\"unit\":\"kg\"}",
                added(1, "Tomato", "1 kg", 1, "₹36", 1));
        recorder.recordResult("prepareMyCartAdd", "{\"product\":\"Organic Carrot\",\"quantity\":2,\"unit\":\"kg\"}",
                added(15, "Organic Carrot", "500 g", 4, "₹171", 4).replace("Fresh Mart", "Green Basket"));

        assertThat(ChatActions.of(recorder).items()).extracting(line -> line.name()).containsExactly("Tomato");
        assertThat(AddReport.of(recorder)).isEqualTo("""
                Here is the product to add to your cart: 1 x Tomato (1 kg) for ₹36. Please check it and press Confirm to add \
                it; nothing is added before that.
                Organic Carrot is sold by Green Basket, and the card's products by Fresh Mart. A cart holds the products of \
                one store only, so it is not on the card: please order it separately.""");
    }

    @Test
    void buyAndOrder_theOrderComesAfterTheClick() {
        // prepareMyOrder reads the cart as it is, so the order summary can only follow the click that adds
        ToolCallRecorder recorder = recorder("I want to buy Harpic Toilet Cleaner, can you order it for me?");
        recorder.recordResult("prepareMyCartAdd", "{\"product\":\"Harpic Toilet Cleaner\"}",
                added(119, "Harpic Toilet Cleaner", "500 ml", 1, "₹99.75", 1));

        assertThat(AddReport.of(recorder)).endsWith("nothing is added before that.\nAfter you confirm, ask me to place your order.");
    }

    private static ToolCallRecorder recorder(String message) {
        return new ToolCallRecorder(message, message, true);
    }
}
