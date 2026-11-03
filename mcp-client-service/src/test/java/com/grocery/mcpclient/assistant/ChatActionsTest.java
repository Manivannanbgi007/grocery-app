package com.grocery.mcpclient.assistant;

import com.grocery.mcpclient.dto.response.ChatAction;
import com.grocery.mcpclient.tool.ToolCallRecorder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The Confirm card comes from prepareMyOrder's result only, never from the model's text. */
public class ChatActionsTest {

    /** A real prepareMyOrder result (also used by ChatServiceImplTest) */
    public static final String SUMMARY = """
            {"status":"OK","message":"Order summary prepared; NOTHING is ordered yet.","data":{"store":"Fresh Mart",\
            "items":[{"productId":1,"name":"Tomato","packSize":"1 kg","quantity":2,"unitPrice":"₹36","lineTotal":"₹72"}],\
            "itemsTotal":"₹72","deliveryFee":"₹30","payable":"₹102",\
            "deliverTo":"Home: Priya Sharma, 12, 5th Cross, Indiranagar, Bengaluru 560038","payment":"Cash on delivery",\
            "confirm":{"addressId":3,"paymentMethod":"COD","checkoutFingerprint":"fp-ready-cart-001"}}}""";

    @Test
    void anOrderSummaryBecomesAPlaceOrderCard() {
        var recorder = new ToolCallRecorder();
        recorder.recordResult("prepareMyOrder", "{\"paymentMethod\":\"cash\"}", SUMMARY);

        ChatAction action = ChatActions.of(recorder);

        assertThat(action.kind()).isEqualTo(ChatAction.PLACE_ORDER);
        assertThat(action.store()).isEqualTo("Fresh Mart");
        assertThat(action.items()).containsExactly(new ChatAction.Line("Tomato", "1 kg", 2, "₹72"));
        assertThat(action.payable()).isEqualTo("₹102");
        assertThat(action.couponDiscount()).isNull();
        assertThat(action.deliverTo()).startsWith("Home: Priya Sharma");
        assertThat(action.addressId()).isEqualTo(3L);
        assertThat(action.paymentMethod()).isEqualTo("COD");
        assertThat(action.checkoutFingerprint()).isEqualTo("fp-ready-cart-001");
    }

    /** A real prepareMyOrderCancellation result (also used by ChatServiceImplTest) */
    public static final String CANCELLATION = """
            {"status":"OK","message":"Cancellation summary prepared; NOTHING is cancelled yet.","data":{"orderNumber":198,\
            "store":"Fresh Mart","status":"Packed by the store",\
            "items":[{"name":"Tomato","packSize":"1 kg","quantity":3,"unitPrice":"₹36","lineTotal":"₹108"}],\
            "total":"₹138","refund":"Nothing was paid yet (cash on delivery).",\
            "confirm":{"orderId":198,"reason":"Ordered by mistake"}}}""";

    @Test
    void aCancellationSummaryBecomesACancelOrderCard() {
        var recorder = new ToolCallRecorder();
        recorder.recordResult("prepareMyOrderCancellation", "{\"orderNumber\":198}", CANCELLATION);

        ChatAction action = ChatActions.of(recorder);

        assertThat(action.kind()).isEqualTo(ChatAction.CANCEL_ORDER);
        assertThat(action.orderId()).isEqualTo(198L);
        assertThat(action.status()).isEqualTo("Packed by the store");
        assertThat(action.payable()).isEqualTo("₹138");
        assertThat(action.refund()).isEqualTo("Nothing was paid yet (cash on delivery).");
        assertThat(action.reason()).isEqualTo("Ordered by mistake");
        assertThat(action.items()).containsExactly(new ChatAction.Line("Tomato", "1 kg", 3, "₹108"));
        assertThat(action.checkoutFingerprint()).isNull();

        var withoutOrderId = new ToolCallRecorder();
        withoutOrderId.recordResult("prepareMyOrderCancellation", "{}", CANCELLATION.replace("\"orderId\":198", "\"other\":198"));
        assertThat(ChatActions.of(withoutOrderId)).isNull();
    }

    /** A real prepareMyCartRemoval result (also used by ChatServiceImplTest and AnswerValidatorTest) */
    public static final String REMOVAL = """
            {"status":"OK","message":"Removal prepared; NOTHING is removed yet. The website shows it under your answer with \
            a Confirm button: tell the customer to check it and press Confirm to take Harpic Toilet Cleaner (500 ml) out of \
            the cart.","data":{"store":"Fresh Mart","items":[{"productId":119,"name":"Harpic Toilet Cleaner",\
            "packSize":"500 ml","quantity":1,"unitPrice":"₹99.75","lineTotal":"₹99.75"}],"confirm":{"productId":119}}}""";

    @Test
    void aRemovalSummaryBecomesARemoveFromCartCard_withTheProductTheButtonRemoves() {
        var recorder = new ToolCallRecorder();
        recorder.recordResult("prepareMyCartRemoval", "{\"product\":\"Harpic Toilet Cleaner\"}", REMOVAL);

        ChatAction action = ChatActions.of(recorder);

        assertThat(action.kind()).isEqualTo(ChatAction.REMOVE_FROM_CART);
        assertThat(action.store()).isEqualTo("Fresh Mart");
        assertThat(action.items()).containsExactly(new ChatAction.Line("Harpic Toilet Cleaner", "500 ml", 1, "₹99.75", 119L));
        assertThat(action.payable()).isNull();
        assertThat(action.orderId()).isNull();

        // without the product to remove, or with more than one line, the button would not know what to remove
        var withoutProduct = new ToolCallRecorder();
        withoutProduct.recordResult("prepareMyCartRemoval", "{}", REMOVAL.replace("\"confirm\"", "\"other\""));
        var twoLines = new ToolCallRecorder();
        twoLines.recordResult("prepareMyCartRemoval", "{}", REMOVAL.replace("\"items\":[{", "\"items\":[{\"name\":\"Tomato\"},{"));
        assertThat(ChatActions.of(withoutProduct)).isNull();
        assertThat(ChatActions.of(twoLines)).isNull();
    }

    /** REMOVAL for 2 x Tomato (1 kg), product 1 */
    static final String TOMATO_REMOVAL = REMOVAL.replace("119", "1").replace("Harpic Toilet Cleaner", "Tomato")
            .replace("500 ml", "1 kg").replace("\"quantity\":1", "\"quantity\":2").replace("₹99.75", "₹72");

    @Test
    void severalRemovalsBecomeOneCard_withEachProductOnce() {
        // "Remove Harpic and Tomato from my cart": one prepareMyCartRemoval per product
        var recorder = new ToolCallRecorder();
        recorder.recordResult("prepareMyCartRemoval", "{\"product\":\"Harpic Toilet Cleaner\"}", REMOVAL);
        recorder.recordResult("prepareMyCartRemoval", "{\"product\":\"tomato\"}", TOMATO_REMOVAL);
        recorder.recordResult("prepareMyCartRemoval", "{\"product\":\"harpic\"}", REMOVAL);      // the same product again
        recorder.recordResult("prepareMyCartRemoval", "{\"product\":\"mango\"}",
                "{\"status\":\"NOT_FOUND\",\"message\":\"The cart has no product called \\\"mango\\\".\"}");

        ChatAction action = ChatActions.of(recorder);

        assertThat(action.kind()).isEqualTo(ChatAction.REMOVE_FROM_CART);
        assertThat(action.store()).isEqualTo("Fresh Mart");
        assertThat(action.items()).containsExactly(
                new ChatAction.Line("Harpic Toilet Cleaner", "500 ml", 1, "₹99.75", 119L),
                new ChatAction.Line("Tomato", "1 kg", 2, "₹72", 1L));
    }

    @Test
    void aNewQuantityBecomesASetCartQuantityCard_andTheWholeCartAClearCartCard() {
        var quantity = new ToolCallRecorder();
        quantity.recordResult("prepareMyCartQuantity", "{\"product\":\"tomatoes\",\"quantity\":3,\"unit\":\"kg\"}", """
                {"status":"OK","message":"Change prepared; NOTHING is changed yet.","data":{"store":"Fresh Mart",\
                "items":[{"productId":1,"name":"Tomato","packSize":"1 kg","quantity":2,"unitPrice":"₹36","lineTotal":"₹72"}],\
                "confirm":{"productId":1,"quantity":3}}}""");

        ChatAction change = ChatActions.of(quantity);

        assertThat(change.kind()).isEqualTo(ChatAction.SET_CART_QUANTITY);
        assertThat(change.items()).containsExactly(new ChatAction.Line("Tomato", "1 kg", 2, "₹72", 1L));      // as it is now
        assertThat(change.newQuantity()).isEqualTo(3);

        var clear = new ToolCallRecorder();
        clear.recordResult("prepareMyCartClear", "{}", """
                {"status":"OK","message":"Emptying prepared; NOTHING is removed yet.","data":{"store":"Fresh Mart",\
                "items":[{"productId":1,"name":"Tomato","packSize":"1 kg","quantity":2,"unitPrice":"₹36","lineTotal":"₹72"}],\
                "itemsTotal":"₹72","deliveryFee":"₹30","payable":"₹102"}}""");

        ChatAction empty = ChatActions.of(clear);

        assertThat(empty.kind()).isEqualTo(ChatAction.CLEAR_CART);
        assertThat(empty.items()).containsExactly(new ChatAction.Line("Tomato", "1 kg", 2, "₹72"));
        assertThat(empty.payable()).isEqualTo("₹102");
        assertThat(empty.newQuantity()).isNull();

        // without the new quantity, or with an empty cart, there is nothing to confirm
        var withoutQuantity = new ToolCallRecorder();
        withoutQuantity.recordResult("prepareMyCartQuantity", "{}", "{\"status\":\"OK\",\"message\":\"The cart already has 2 x "
                + "Tomato (1 kg); nothing to change.\"}");
        var emptyCart = new ToolCallRecorder();
        emptyCart.recordResult("prepareMyCartClear", "{}", "{\"status\":\"OK\",\"data\":{\"items\":[]}}");
        assertThat(ChatActions.of(withoutQuantity)).isNull();
        assertThat(ChatActions.of(emptyCart)).isNull();
    }

    @Test
    void additionsBecomeOneAddToCartCard_eachProductOnce_withTheCartsTotalAfterTheClick() {
        var recorder = new ToolCallRecorder();
        recorder.recordResult("prepareMyCartAdd", "{\"product\":\"tomato\"}", AddReportTest.added(1, "Tomato", "1 kg", 1, "₹36", 1));
        recorder.recordResult("prepareMyCartAdd", "{\"product\":\"onion\"}", AddReportTest.added(2, "Onion", "1 kg", 2, "₹80", 3));
        recorder.recordResult("prepareMyCartAdd", "{\"product\":\"Tomato\"}", AddReportTest.added(1, "Tomato", "1 kg", 1, "₹36", 1));
        // without the new total the button would not know what to set: left out
        recorder.recordResult("prepareMyCartAdd", "{\"product\":\"potato\"}",
                AddReportTest.added(3, "Potato", "1 kg", 1, "₹28.50", 1).replace(",\"newQuantity\":1", ""));

        ChatAction action = ChatActions.of(recorder);

        assertThat(action.kind()).isEqualTo(ChatAction.ADD_TO_CART);
        assertThat(action.items()).containsExactly(new ChatAction.Line("Tomato", "1 kg", 1, "₹36", 1L, 1),
                new ChatAction.Line("Onion", "1 kg", 2, "₹80", 2L, 3));
    }

    @Test
    void noSummaryNoCard() {
        var refused = new ToolCallRecorder();
        refused.recordResult("prepareMyOrder", "{}", "{\"status\":\"REFUSED\",\"message\":\"The cart cannot be ordered yet: "
                + "Tomato is out of stock.\"}");
        var otherTool = new ToolCallRecorder();
        otherTool.recordResult("getMyCart", "{}", SUMMARY);       // only prepareMyOrder makes a card
        var withoutConfirm = new ToolCallRecorder();
        withoutConfirm.recordResult("prepareMyOrder", "{}", SUMMARY.replace("\"confirm\"", "\"other\""));

        assertThat(ChatActions.of(refused)).isNull();
        assertThat(ChatActions.of(otherTool)).isNull();
        assertThat(ChatActions.of(withoutConfirm)).isNull();
        assertThat(ChatActions.of(new ToolCallRecorder())).isNull();
    }
}
