package com.grocery.mcpclient.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Only the customer's own words can allow a change of the cart, never a text in a tool result. */
class ChangeRequestTest {

    @Test
    void wordsThatAskForAChange() {
        assertThat(ChangeRequest.asked("Add 2 kg tomatoes", null)).isTrue();
        assertThat(ChangeRequest.asked("I want 3 packets of milk", null)).isTrue();
        assertThat(ChangeRequest.asked("please remove the onions", null)).isTrue();
        assertThat(ChangeRequest.asked("Take out the butter", null)).isTrue();
        assertThat(ChangeRequest.asked("make it 3", null)).isTrue();
        assertThat(ChangeRequest.asked("Yes", null)).isTrue();
        assertThat(ChangeRequest.asked("do kilo tamatar dalo", null)).isTrue();
        assertThat(ChangeRequest.asked("दो किलो टमाटर डालो", null)).isTrue();
    }

    @Test
    void questionsThatAskForNoChange() {
        assertThat(ChangeRequest.asked("What do customers say about Amul Butter?", null)).isFalse();
        assertThat(ChangeRequest.asked("Is milk in stock?", null)).isFalse();
        assertThat(ChangeRequest.asked("What is in my cart?", null)).isFalse();
        assertThat(ChangeRequest.asked("Show my notifications", null)).isFalse();
        // a word that only contains a change word is no change word
        assertThat(ChangeRequest.asked("Is there an address for the store?", null)).isFalse();
    }

    @Test
    void theAmountMustBeInTheCustomersWords() {
        assertThat(ChangeRequest.unsaidAmount("add 2 kg tomatoes", 2.0, "kg")).isNull();
        assertThat(ChangeRequest.unsaidAmount("add 2kg tomatoes", 2, "KG")).isNull();
        assertThat(ChangeRequest.unsaidAmount("add two kilos of tomatoes", 2, "kg")).isNull();
        assertThat(ChangeRequest.unsaidAmount("do kilo tamatar dalo", 2, "kg")).isNull();
        assertThat(ChangeRequest.unsaidAmount("२ किलो टमाटर डालो", 2, "kg")).isNull();
        assertThat(ChangeRequest.unsaidAmount("add 500g butter", 500, "g")).isNull();
        assertThat(ChangeRequest.unsaidAmount("add half a kilo of onions", 0.5, "kg")).isNull();
        assertThat(ChangeRequest.unsaidAmount("add 12 eggs", 12, "pieces")).isNull();     // counts need no unit word
        assertThat(ChangeRequest.unsaidAmount("add a milk", 1, null)).isNull();            // one pack: "a", "one", "1"
        assertThat(ChangeRequest.unsaidAmount("add one more milk", 1, null)).isNull();
        assertThat(ChangeRequest.unsaidAmount("add 1 kg tomatoes", 1, "kg")).isNull();
        assertThat(ChangeRequest.unsaidAmount("remove the onions", null, null)).isNull();
        // "add milk" says no amount: the add tool asks how much instead of choosing one pack
        assertThat(ChangeRequest.unsaidAmount("add milk", 1, null)).isNotNull();

        assertThat(ChangeRequest.unsaidAmount("add 2k tomatoes", 2, "kg"))
                .startsWith("The amount \"2 kg\" is not in the customer's words. Ask the customer how much they want");
        assertThat(ChangeRequest.unsaidAmount("add 12 eggs", 2, null)).isNotNull();        // 2 is not in 12
        assertThat(ChangeRequest.unsaidAmount("add 2.5 kg onions", 2, "kg")).isNotNull();
        assertThat(ChangeRequest.unsaidAmount("add some tomatoes", 3, null)).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"3kg", "3 kg", "Three kilograms", "I need 3 kilos", "Just 3 kg, please", "yes, 3 kg of tomatoes",
            "make it three kilo"})
    void theAnswerToHowMuchIsReadInCode(String answer) {
        assertThat(ChangeRequest.isAmountReply(answer, "Tomato")).isTrue();
        assertThat(ChangeRequest.amountReply(answer, "Tomato")).isEqualTo(new ChangeRequest.ListedProduct("Tomato", new BigDecimal("3"), "kg"));
    }

    @Test
    void anAnswerWithoutAUnitCountsPacks_andHalfAKiloIsHalfAKilo() {
        assertThat(ChangeRequest.amountReply("2", "Amul Butter")).isEqualTo(new ChangeRequest.ListedProduct("Amul Butter", new BigDecimal("2"), "packs"));
        assertThat(ChangeRequest.amountReply("two packets please", "Amul Butter"))
                .isEqualTo(new ChangeRequest.ListedProduct("Amul Butter", new BigDecimal("2"), "packs"));
        assertThat(ChangeRequest.amountReply("one", "Amul Butter")).isEqualTo(new ChangeRequest.ListedProduct("Amul Butter", BigDecimal.ONE, "packs"));
        assertThat(ChangeRequest.amountReply("a dozen", "Banana Robusta"))
                .isEqualTo(new ChangeRequest.ListedProduct("Banana Robusta", BigDecimal.ONE, "dozen"));
        assertThat(ChangeRequest.amountReply("half a kilo", "Onion")).isEqualTo(new ChangeRequest.ListedProduct("Onion", new BigDecimal("0.5"), "kg"));
    }

    @Test
    void anAmountThatCannotBeRightIsNoAmount_theQuestionIsAskedAgain() {
        // these are answers about the amount (the question stays open) ...
        assertThat(ChangeRequest.isAmountReply("0 kg", "Tomato")).isTrue();
        assertThat(ChangeRequest.isAmountReply("2 or 3 kg", "Tomato")).isTrue();
        // ... but without one amount above zero
        assertThat(ChangeRequest.amountReply("0 kg", "Tomato")).isNull();
        assertThat(ChangeRequest.amountReply("-2 kg", "Tomato")).isNull();
        assertThat(ChangeRequest.amountReply("2 or 3 kg", "Tomato")).isNull();
    }

    @Test
    void aMessageAboutSomethingElseIsNoAnswerToHowMuch() {
        assertThat(ChangeRequest.isAmountReply("3 kg onions", "Tomato")).isFalse();          // another product: a new request
        assertThat(ChangeRequest.isAmountReply("What is the price of milk?", "Tomato")).isFalse();
        assertThat(ChangeRequest.isAmountReply("cancel", "Tomato")).isFalse();
        assertThat(ChangeRequest.isAmountReply("Where is my order 207?", "Tomato")).isFalse();
    }

    @Test
    void wordsThatCallOffTheQuestion() {
        assertThat(ChangeRequest.cancelAsked("cancel", "Tomato")).isTrue();
        assertThat(ChangeRequest.cancelAsked("No thanks", "Tomato")).isTrue();
        assertThat(ChangeRequest.cancelAsked("never mind", "Tomato")).isTrue();
        assertThat(ChangeRequest.cancelAsked("Don't add it", "Tomato")).isTrue();
        assertThat(ChangeRequest.cancelAsked("forget it", "Tomato")).isTrue();
        assertThat(ChangeRequest.cancelAsked("no, I don't want the tomatoes anymore", "Tomato")).isTrue();
        assertThat(ChangeRequest.cancelAsked("What is the price of milk?", "Tomato")).isFalse();
        assertThat(ChangeRequest.cancelAsked("3 kg", "Tomato")).isFalse();
        // a "no" with a new request is the new request
        assertThat(ChangeRequest.cancelAsked("I don't need tomato, add 2 kg onions instead", "Tomato")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Proceed to pay.", "proceed to pay it", "Pay online.", "I want to pay online.",
            "Proceed with online payment.", "I want to purchase everything in my cart using online payment.",
            "Pay for all my cart items online.", "Let's complete the payment online.", "Proceed to pay via online.",
            "i want to buy them by online payment. can you proceed payment",
            "i want to by all of them which is in my cart by online payment", "Pay when the order is delivered.",
            "I want to pay after delivery.", "Place my order", "checkout please"})
    void requestsToPayForTheCart(String message) {
        assertThat(ChangeRequest.checkoutAsked(message)).isTrue();
        assertThat(ChangeRequest.onlyAboutCheckout(message)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"How can I pay online?", "Which payment methods do you accept?", "Where is my order?",
            "Add 2 kg tomatoes", "I want to buy Harpic", "What is in my cart?", "Cancel my last order",
            "No, don't place the order", "Online.", "COD"})
    void noRequestToPayForTheCart(String message) {
        // "Online." and "COD" answer the question how to pay (CheckoutStep), but ask for nothing on their own
        assertThat(ChangeRequest.checkoutAsked(message)).isFalse();
    }

    @Test
    void aCheckoutWithSomethingElseIsNotOnlyACheckout() {
        assertThat(ChangeRequest.onlyAboutCheckout("proceed to pay for the tomatoes")).isFalse();
        assertThat(ChangeRequest.onlyAboutCheckout("add 2 kg onions and checkout")).isFalse();
        assertThat(ChangeRequest.onlyAboutCheckout("Online.")).isTrue();
        assertThat(ChangeRequest.onlyAboutCheckout("Cash on delivery instead")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Online.", "Pay online.", "I want to pay online.", "Proceed with online payment.",
            "I want to purchase everything in my cart using online payment.", "Pay for all my cart items online.",
            "Let's complete the payment online.", "Proceed to pay via online.", "UPI please"})
    void theWaysToSayOnline(String message) {
        assertThat(ChangeRequest.paymentOf(message)).isEqualTo("online");
    }

    @ParameterizedTest
    @ValueSource(strings = {"COD.", "Cash on delivery.", "I prefer cash payment.", "Pay when the order is delivered.",
            "I want to pay after delivery.", "pay on delivery"})
    void theWaysToSayCashOnDelivery(String message) {
        assertThat(ChangeRequest.paymentOf(message)).isEqualTo("cash");
    }

    @Test
    void callingOffTheCheckout() {
        assertThat(ChangeRequest.checkoutCancelAsked("never mind")).isTrue();
        assertThat(ChangeRequest.checkoutCancelAsked("cancel")).isTrue();
        assertThat(ChangeRequest.checkoutCancelAsked("No, don't place the order")).isTrue();
        assertThat(ChangeRequest.checkoutCancelAsked("Cancel my last order")).isFalse();     // an order that exists
        assertThat(ChangeRequest.checkoutCancelAsked("No, pay online")).isFalse();
    }

    @Test
    void aWayToPayIsNoProductName() {
        assertThat(ChangeRequest.productsToAdd("i want to buy them by online payment. can you proceed payment")).isEmpty();
        assertThat(ChangeRequest.productsToAdd("I want to buy Harpic Toilet Cleaner by online payment"))
                .containsExactly(new ChangeRequest.ListedProduct("Harpic Toilet Cleaner", null, null));
    }

    @Test
    void aCoupleOfKilosKeepsItsUnit() {
        assertThat(ChangeRequest.amountReply("a couple of kilos", "Onion")).isEqualTo(new ChangeRequest.ListedProduct("Onion", new BigDecimal("2"), "kg"));
    }

    @Test
    void oneMoreAndAPackAreOnePack_noAmountIsNone() {
        assertThat(ChangeRequest.productsToAdd("can you add one more item for Aashirvaad Whole Wheat Atta in my cart ?"))
                .containsExactly(new ChangeRequest.ListedProduct("Aashirvaad Whole Wheat Atta", BigDecimal.ONE, null));
        assertThat(ChangeRequest.productsToAdd("add a tomato"))
                .containsExactly(new ChangeRequest.ListedProduct("tomato", BigDecimal.ONE, null));
        assertThat(ChangeRequest.productsToAdd("Add tomoto in my cart?"))
                .containsExactly(new ChangeRequest.ListedProduct("tomoto", null, null));
    }

    @Test
    void aWayToPayCountsOnlyWhenTheCustomerNamedOne() {
        assertThat(ChangeRequest.paymentSaid("Place my order, cash on delivery\n")).isTrue();
        assertThat(ChangeRequest.paymentSaid("I'll pay by UPI\nplace my order")).isTrue();
        assertThat(ChangeRequest.paymentSaid("order karo, nakad\n")).isTrue();
        assertThat(ChangeRequest.paymentSaid("Place my order\nadd 3 kg tomatoes")).isFalse();
        assertThat(ChangeRequest.paymentSaid(null)).isFalse();
        // which way, in prepareMyOrder's words
        assertThat(ChangeRequest.paymentOf("Cash on delivery\ncan you order for me?")).isEqualTo("cash");
        assertThat(ChangeRequest.paymentOf("I'll pay by UPI\nplace my order")).isEqualTo("online");
        assertThat(ChangeRequest.paymentOf("Place my order\nadd 3 kg tomatoes")).isNull();
    }

    @Test
    void wordsThatAskToBuyOrOrder() {
        assertThat(ChangeRequest.orderAsked("i want to buy this \"Harpic Toilet Cleaner\" product. can you order for me ?\n")).isTrue();
        assertThat(ChangeRequest.orderAsked("Cash on delivery\nI want to buy Harpic")).isTrue();
        assertThat(ChangeRequest.orderAsked("Place my order\n")).isTrue();
        assertThat(ChangeRequest.orderAsked("Add 2 kg of tomatoes to my cart\n")).isFalse();
        assertThat(ChangeRequest.orderAsked(null)).isFalse();

        // to add, and whether an amount other than one was named (then only the model's tool call carries it)
        assertThat(ChangeRequest.addAsked("can you add one more item for Aashirvaad Whole Wheat Atta in my cart ?")).isTrue();
        assertThat(ChangeRequest.addAsked("What is in my cart?")).isFalse();
        assertThat(ChangeRequest.namesAnAmount("can you add one more item for Aashirvaad Whole Wheat Atta in my cart ?")).isFalse();
        assertThat(ChangeRequest.namesAnAmount("add 1 more Harpic")).isFalse();
        assertThat(ChangeRequest.namesAnAmount("I want to buy 2 kg tomatoes, order them for me")).isTrue();
        assertThat(ChangeRequest.namesAnAmount("add two packs of milk")).isTrue();
        assertThat(ChangeRequest.namesAnAmount("add 500g butter")).isTrue();
        assertThat(ChangeRequest.namesAnAmount("add a kilo of onions")).isTrue();

        // the amount check's own question: that cart call changed nothing
        assertThat(ChangeRequest.isUnsaidAmountQuestion(ChangeRequest.unsaidAmount("can you add one more item for X?", 2, null))).isTrue();
        assertThat(ChangeRequest.isUnsaidAmountQuestion("Which milk? Amul Taaza or Akshayakalpa?")).isFalse();
        assertThat(ChangeRequest.isUnsaidAmountQuestion(null)).isFalse();

        // to order now: not every "order"
        assertThat(ChangeRequest.placeOrderAsked("Place my order")).isTrue();
        assertThat(ChangeRequest.placeOrderAsked("Place my order, cash on delivery")).isTrue();
        assertThat(ChangeRequest.placeOrderAsked("checkout please")).isTrue();
        // ordering a product is buying it: into the cart first (orderAsked), never the cart that happens to be there
        assertThat(ChangeRequest.placeOrderAsked("i want to buy this \"Harpic Toilet Cleaner\" product. can you order for me ?")).isFalse();
        assertThat(ChangeRequest.placeOrderAsked("I want to buy Harpic Toilet Cleaner. Order it for me.")).isFalse();
        assertThat(ChangeRequest.placeOrderAsked("Where is my order?")).isFalse();
        assertThat(ChangeRequest.placeOrderAsked("Cancel my last order")).isFalse();
        assertThat(ChangeRequest.placeOrderAsked("Show my orders")).isFalse();
    }

    @Test
    void wordsThatAskToTakeAProductOut() {
        assertThat(ChangeRequest.removeAsked("Can you remove Harpic Toilet Cleaner from my cart?")).isTrue();     // the user's words
        assertThat(ChangeRequest.removeAsked("delete the amul butter from my cart")).isTrue();
        assertThat(ChangeRequest.removeAsked("Please take the Harpic Toilet Cleaner out of my cart")).isTrue();
        assertThat(ChangeRequest.removeAsked("pyaaz hatao")).isTrue();
        assertThat(ChangeRequest.removeAsked("pyaaz hata do")).isTrue();       // "do" here is no "two"
        assertThat(ChangeRequest.removeAsked("do pyaaz hata do")).isFalse();   // two onions
        assertThat(ChangeRequest.removeAsked("प्याज़ हटा दो")).isTrue();

        // an amount may mean less of it, not all of it: the model's tools handle that
        assertThat(ChangeRequest.removeAsked("remove 1 Harpic Toilet Cleaner")).isFalse();
        assertThat(ChangeRequest.removeAsked("remove one Harpic Toilet Cleaner")).isFalse();
        assertThat(ChangeRequest.removeAsked("remove 2 kg tomatoes")).isFalse();
        // no request to take something out
        assertThat(ChangeRequest.removeAsked("What is in my cart?")).isFalse();
        assertThat(ChangeRequest.removeAsked("Can you take me to the cart?")).isFalse();
        assertThat(ChangeRequest.removeAsked(null)).isFalse();
    }

    @Test
    void theEnglishDoIsNoAmount_theHindiDoStillIsTwo() {
        // the user's words (2026-10-30): the "do" of "i do not want" was read as Hindi "do" (two), so the code took it for
        // "remove two of something" and did not prepare the removal
        assertThat(ChangeRequest.namesAnAmount("i do not want tomato in my cart. can you remove it please")).isFalse();
        assertThat(ChangeRequest.removeAsked("i do not want tomato in my cart. can you remove it please")).isTrue();
        assertThat(ChangeRequest.removeAsked("I do not need potato anymore, take it out")).isTrue();
        assertThat(ChangeRequest.namesAnAmount("Do you have Harpic? I want to buy it")).isFalse();
        assertThat(ChangeRequest.namesAnAmount("please do add the tomatoes")).isFalse();
        // and the amount check no longer finds a "2" in it: an amount the model made up is still asked about
        assertThat(ChangeRequest.unsaidAmount("do you have tomatoes? add them to my cart", 2, null)).isNotNull();

        // Hindi: "do" before a product or a unit is two
        assertThat(ChangeRequest.namesAnAmount("do kilo tamatar dalo")).isTrue();
        assertThat(ChangeRequest.unsaidAmount("do kilo tamatar dalo", 2, "kg")).isNull();
        assertThat(ChangeRequest.unsaidAmount("do tamatar dalo", 2, null)).isNull();
    }

    @Test
    void dontRemoveAsksForNoRemoval_andTheProductsToKeepAreLeftOut() {
        assertThat(ChangeRequest.removeAsked("Please don't remove the tomatoes, I still need them")).isFalse();
        assertThat(ChangeRequest.removeAsked("do not delete the milk")).isFalse();
        assertThat(ChangeRequest.removeAsked("I don’t want tomato, remove it")).isTrue();      // "don't want" is no "don't remove"

        assertThat(ChangeRequest.withoutKeptProducts("Remove Tomato and Potato from my cart, but keep Organic Carrot."))
                .isEqualTo("Remove Tomato and Potato from my cart, but ");
        assertThat(ChangeRequest.withoutKeptProducts("Remove everything except the milk")).isEqualTo("Remove everything ");
        assertThat(ChangeRequest.withoutKeptProducts("Remove the tomatoes")).isEqualTo("Remove the tomatoes");
        assertThat(ChangeRequest.withoutKeptProducts(null)).isEmpty();
    }

    @Test
    void wordsThatAskToEmptyTheWholeCart() {
        assertThat(ChangeRequest.clearAsked("Empty my cart")).isTrue();
        assertThat(ChangeRequest.clearAsked("can you clear the cart please?")).isTrue();
        assertThat(ChangeRequest.clearAsked("Remove everything from my cart")).isTrue();
        assertThat(ChangeRequest.clearAsked("delete all items from the cart")).isTrue();
        assertThat(ChangeRequest.clearAsked("mera cart khali karo")).isTrue();

        assertThat(ChangeRequest.clearAsked("remove all the tomatoes from my cart")).isFalse();     // one product
        assertThat(ChangeRequest.clearAsked("empty my cart except the milk")).isFalse();             // not all of it
        assertThat(ChangeRequest.clearAsked("Is my cart empty?")).isFalse();
        assertThat(ChangeRequest.clearAsked(null)).isFalse();
    }

    @Test
    void theAnswerToTheAssistantsQuestionCountsWithThePreviousMessage() {
        assertThat(ChangeRequest.asked("The Amul one", "add milk")).isTrue();
        assertThat(ChangeRequest.asked("The Amul one", "which milk is the cheapest?")).isFalse();
        assertThat(ChangeRequest.asked("The Amul one", null)).isFalse();
    }

    @Test
    void aProductTheCustomerNamed_singularOrPlural_butNotOneTakenFromAList() {
        String words = "Add milk and 1 kg tomatoes to my cart";
        assertThat(ChangeRequest.productSaid(words, "tomato")).isTrue();
        assertThat(ChangeRequest.productSaid(words, "Tomatoes 1 kg")).isTrue();        // the amount is no part of the name
        assertThat(ChangeRequest.productSaid(words, "milk")).isTrue();
        assertThat(ChangeRequest.productSaid(words, "Amul Taaza Toned Milk")).isFalse();
        assertThat(ChangeRequest.productSaid(words, null)).isFalse();
        assertThat(ChangeRequest.productSaid("दो किलो टमाटर डालो", "टमाटर")).isTrue();

        assertThat(ChangeRequest.sameProduct("milk", "Amul Taaza Toned Milk")).isTrue();
        assertThat(ChangeRequest.sameProduct("tomatoes", "Tomato")).isTrue();
        assertThat(ChangeRequest.sameProduct("milk", "tomatoes")).isFalse();
        // one word in common is not the same product (the user's message of 2026-10-30)
        assertThat(ChangeRequest.sameProduct("Akshayakalpa Organic Milk", "Organic Carrot")).isFalse();
        assertThat(ChangeRequest.sameProduct(null, "Organic Carrot")).isFalse();
    }

    @Test
    void theProductsOfAClearList_withTheAmountSaidForEach() {
        assertThat(ChangeRequest.listedProducts("can you add Akshayakalpa Organic Milk 1L and Organic Carrot 2 kg in my cart?"))
                .containsExactly(new ChangeRequest.ListedProduct("Akshayakalpa Organic Milk", new BigDecimal("1"), "l"),
                        new ChangeRequest.ListedProduct("Organic Carrot", new BigDecimal("2"), "kg"));
        assertThat(ChangeRequest.listedProducts("Add Tomato 1 kg, Onion 2 kilos, 2 packets of Amul Taaza Toned Milk and Amul Butter"))
                .containsExactly(new ChangeRequest.ListedProduct("Tomato", new BigDecimal("1"), "kg"),
                        new ChangeRequest.ListedProduct("Onion", new BigDecimal("2"), "kg"),
                        new ChangeRequest.ListedProduct("Amul Taaza Toned Milk", new BigDecimal("2"), "packs"));
        // a part without an amount is left to the model: a product without one, or no product at all
        assertThat(ChangeRequest.listedProducts("can you order tomato 1kg and potato 3 kg for me?"))
                .extracting(ChangeRequest.ListedProduct::product).containsExactly("tomato", "potato");
        assertThat(ChangeRequest.listedProducts("I want to buy 2 kg Tomato, order them for me"))
                .extracting(ChangeRequest.ListedProduct::product).containsExactly("Tomato");
    }

    @Test
    void theAmountTheCustomerSaidForAProduct_inThisMessage() {
        String message = "can you add Akshayakalpa Organic Milk 1L and Organic Carrot 2 kg in my cart?";
        ChangeRequest.ListedProduct carrot = ChangeRequest.amountSaidFor(message, "Organic Carrot");
        assertThat(carrot.quantity()).isEqualByComparingTo("2");
        assertThat(carrot.unit()).isEqualTo("kg");
        assertThat(ChangeRequest.amountSaidFor(message, "Akshayakalpa Organic Milk").unit()).isEqualTo("l");
        // no amount, two different ones, another product
        assertThat(ChangeRequest.amountSaidFor("add tomatoes and 2 kg onions", "tomatoes")).isNull();
        assertThat(ChangeRequest.amountSaidFor("add 1 kg tomatoes and 2 kg tomatoes", "tomatoes")).isNull();
        assertThat(ChangeRequest.amountSaidFor(message, "Potato")).isNull();

        // live, 2026-10-30: the carrots were sent as 2 packs (of 500 g), and as 1 kg (the milk's "1L"): neither is 2 kg
        assertThat(ChangeRequest.sameAmount(carrot, 2, "packs")).isFalse();
        assertThat(ChangeRequest.sameAmount(carrot, 1, "kg")).isFalse();
        assertThat(ChangeRequest.sameAmount(carrot, 2.0, null)).isFalse();         // no unit: packs
        assertThat(ChangeRequest.sameAmount(carrot, 2, "kg")).isTrue();
        assertThat(ChangeRequest.sameAmount(carrot, 2, "Kilograms")).isTrue();
        assertThat(ChangeRequest.sameAmount(new ChangeRequest.ListedProduct("milk", BigDecimal.TWO, "packs"), 2, null)).isTrue();

        assertThat(ChangeRequest.withoutAmount("Akshayakalpa Organic Milk 1L")).isEqualTo("Akshayakalpa Organic Milk");
        assertThat(ChangeRequest.withoutAmount("Organic Carrot")).isEqualTo("Organic Carrot");
    }

    @Test
    void aListWithAnUnclearAmountIsNotReadInCode() {
        assertThat(ChangeRequest.listedProducts("Add 2k tomatoes and 1 kg onions")).isEmpty();
        assertThat(ChangeRequest.listedProducts("Add 6 eggs and 1 kg onions")).isEmpty();          // eggs or packs?
        assertThat(ChangeRequest.listedProducts("Add two kilos of tomatoes and 1 kg onions")).isEmpty();
        assertThat(ChangeRequest.listedProducts("Add 2 kg 500 g tomatoes and 1 kg onions")).isEmpty();   // two amounts
        assertThat(ChangeRequest.listedProducts("Add 2 kg of it and 1 kg onions")).isEmpty();            // which product?
        assertThat(ChangeRequest.listedProducts(null)).isEmpty();
    }

    @Test
    void eachProductOfAListHasItsOwnAmount() {
        String words = "Add Tomato 1 kg, Onion 2 kg and Potato 3 kg to my cart";
        assertThat(ChangeRequest.wordsAbout(words, words, "tomatoes")).isEqualTo("Add Tomato 1 kg");
        assertThat(ChangeRequest.wordsAbout(words, words, "Potato")).isEqualTo(" Potato 3 kg to my cart");
        // so 3 kg of tomatoes is no amount the customer said
        assertThat(ChangeRequest.unsaidAmount(ChangeRequest.wordsAbout(words, words, "tomato"), 3, "kg")).contains("\"3 kg\"");
        assertThat(ChangeRequest.unsaidAmount(ChangeRequest.wordsAbout(words, words, "potato"), 3, "kg")).isNull();

        // all the words when no part of THIS message names the product with an amount: the answer to "how much?" ...
        assertThat(ChangeRequest.wordsAbout("2 kg", "2 kg\nadd tomatoes", "tomatoes")).isEqualTo("2 kg\nadd tomatoes");
        // ... a new amount after an earlier one ("make it 3 kg" after "add 2 kg tomatoes": 3 kg is said) ...
        String newAmount = ChangeRequest.wordsAbout("make it 3 kg", "make it 3 kg\nadd 2 kg tomatoes", "tomatoes");
        assertThat(ChangeRequest.unsaidAmount(newAmount, 3, "kg")).isNull();
        // ... or a name the model wrote in English, or with "and" in it
        assertThat(ChangeRequest.wordsAbout("do kilo tamatar aur ek kilo pyaaz", "do kilo tamatar aur ek kilo pyaaz", "tomatoes"))
                .isEqualTo("do kilo tamatar aur ek kilo pyaaz");
    }
}
