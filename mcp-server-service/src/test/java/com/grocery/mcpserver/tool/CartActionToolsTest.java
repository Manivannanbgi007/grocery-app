package com.grocery.mcpserver.tool;

import com.grocery.mcpserver.client.CatalogClient;
import com.grocery.mcpserver.client.CustomerClient;
import com.grocery.mcpserver.client.ShopInfoClient;
import com.grocery.mcpserver.client.dto.PageData;
import com.grocery.mcpserver.config.McpTransportConfig;
import com.grocery.mcpserver.tool.ToolResult.Status;
import com.grocery.mcpserver.tool.result.CartAddPreview;
import com.grocery.mcpserver.tool.result.CartChange;
import com.grocery.mcpserver.tool.result.CartQuantityPreview;
import com.grocery.mcpserver.tool.result.CartRemovalPreview;
import com.grocery.mcpserver.tool.result.CartSummary;
import com.grocery.mcpserver.tool.result.ProductSummary;
import io.modelcontextprotocol.common.McpTransportContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartActionToolsTest {

    private static final McpTransportContext LOGGED_IN = McpTransportContext.create(Map.of(McpTransportConfig.USER_TOKEN, "customer-jwt"));

    @Mock
    private CustomerClient customers;
    @Mock
    private CatalogClient catalog;
    @Mock
    private ShopInfoClient shopInfo;

    private CartActionTools tools;

    @BeforeEach
    void setUp() {
        tools = new CartActionTools(customers, shopInfo, new ProductChoice(catalog, shopInfo), new ToolRunner());
        lenient().when(shopInfo.stores()).thenReturn(TestData.stores());
        lenient().when(catalog.searchProducts(any(), any(), any(), anyBoolean(), anyInt()))
                .thenReturn(new PageData<>(List.of(TestData.tomato()), 1));
    }

    @Test
    void withoutALoginNothingIsPrepared() {
        assertThat(tools.prepareMyCartAdd("tomato", 1.0, null, McpTransportContext.EMPTY).status()).isEqualTo(Status.LOGIN_REQUIRED);
        assertThat(tools.prepareMyCartQuantity("tomato", 2.0, null, McpTransportContext.EMPTY).status()).isEqualTo(Status.LOGIN_REQUIRED);
        assertThat(tools.prepareMyCartClear(McpTransportContext.EMPTY).status()).isEqualTo(Status.LOGIN_REQUIRED);
        assertThat(tools.prepareMyCartRemoval("tomato", McpTransportContext.EMPTY).message())
                .isEqualTo("The customer is not logged in. Ask them to log in on the website to change their cart.");
        verifyNoInteractions(customers);
    }

    @Test
    void addingIsOnlyPrepared_withThePacksAndTheirPrice() {
        when(customers.cart("customer-jwt")).thenReturn(TestData.emptyCart());

        var result = tools.prepareMyCartAdd("tomatoes", 2.0, "kg", LOGGED_IN);

        assertThat(result.status()).isEqualTo(Status.OK);
        assertThat(result.message()).isEqualTo("Adding prepared; NOTHING is added yet. The website shows it under your answer "
                + "with a Confirm button: tell the customer to check it and press Confirm to add 2 x Tomato (1 kg) (₹72) to the cart.");
        CartAddPreview preview = (CartAddPreview) result.data();
        assertThat(preview.store()).isEqualTo("Fresh Mart");
        assertThat(preview.items()).singleElement().satisfies(line -> {
            assertThat(line.name()).isEqualTo("Tomato");
            assertThat(line.packSize()).isEqualTo("1 kg");
            assertThat(line.quantity()).isEqualTo(2);           // the packs to add
            assertThat(line.unitPrice()).isEqualTo("₹36");
            assertThat(line.lineTotal()).isEqualTo("₹72");
        });
        assertThat(preview.confirm()).isEqualTo(new CartAddPreview.Confirm(1L, 2, 2));
    }

    @Test
    void addingToALineSaysHowManyThereAreThen_andTheCardKeepsThatTotal() {
        when(customers.cart("customer-jwt")).thenReturn(TestData.cartWith(TestData.tomato(), 3));

        var result = tools.prepareMyCartAdd("tomato", 1.0, null, LOGGED_IN);    // "one more tomato": one pack

        assertThat(result.message()).contains("press Confirm to add 1 x Tomato (1 kg) (₹36) to the cart.")
                .endsWith("The cart has 3 already, so that makes 4.");
        // the button sets 4 packs: a second click or a retry adds nothing more
        assertThat(((CartAddPreview) result.data()).confirm()).isEqualTo(new CartAddPreview.Confirm(1L, 1, 4));
    }

    @Test
    void withoutAnAmount_theCustomerIsAskedHowMuch_ofTheProductThatWasFound() {
        when(customers.cart("customer-jwt")).thenReturn(TestData.emptyCart());

        var result = tools.prepareMyCartAdd("tomatoes", null, null, LOGGED_IN);

        assertThat(result.status()).isEqualTo(Status.CHOOSE);
        assertThat(result.message()).isEqualTo("How much Tomato (1 kg) does the customer want? Ask them, and prepare nothing "
                + "until they answer.");
        // the product goes with the question: mcp-client-service adds exactly it when the customer answers "3 kg"
        ProductSummary asked = ((CartChange) result.data()).amountFor();
        assertThat(asked.productId()).isEqualTo(1L);
        assertThat(asked.name()).isEqualTo("Tomato");
        assertThat(asked.packSize()).isEqualTo("1 kg");
        assertThat(asked.price()).isEqualTo("₹36");
        assertThat(((CartChange) result.data()).options()).isNull();
    }

    @Test
    void aSpellingMistakeWithTheAmount_preparesTheRightProduct_andItsTotal() {
        when(catalog.searchProducts(any(), any(), any(), anyBoolean(), anyInt())).thenReturn(new PageData<>(List.of(), 0));
        when(catalog.suggestProducts(eq("tomoto"), any(), anyInt())).thenReturn(List.of(TestData.tomato()));
        when(customers.cart("customer-jwt")).thenReturn(TestData.cartWith(TestData.tomato(), 2));

        var result = tools.prepareMyCartAdd("tomoto", 3.0, "kg", LOGGED_IN);

        assertThat(result.status()).isEqualTo(Status.OK);
        CartAddPreview preview = (CartAddPreview) result.data();
        assertThat(preview.items()).singleElement().satisfies(line -> {
            assertThat(line.name()).isEqualTo("Tomato");
            assertThat(line.quantity()).isEqualTo(3);
            assertThat(line.lineTotal()).isEqualTo("₹108");       // 3 x ₹36
        });
        // 2 in the cart + 3: the button sets the line to 5 packs, so a second click adds nothing more
        assertThat(preview.confirm()).isEqualTo(new CartAddPreview.Confirm(1L, 3, 5));
    }

    @Test
    void moreThanTwentyPacksAreAskedAgain() {
        when(customers.cart("customer-jwt")).thenReturn(TestData.emptyCart());

        var result = tools.prepareMyCartAdd("tomato", 25.0, "kg", LOGGED_IN);

        assertThat(result.status()).isEqualTo(Status.CHOOSE);
        assertThat(result.message()).isEqualTo("That would be 25 packs of Tomato (1 kg a pack), and a cart takes at most 20 "
                + "packs of a product. How many packs should it be?");
    }

    @Test
    void moreThanTwentyPacksAreRefusedWithTheRoomLeft() {
        when(customers.cart("customer-jwt")).thenReturn(TestData.cartWith(TestData.tomato(), 19), TestData.cartWith(TestData.tomato(), 20));

        var result = tools.prepareMyCartAdd("tomato", 2.0, "kg", LOGGED_IN);

        assertThat(result.status()).isEqualTo(Status.REFUSED);
        assertThat(result.message()).isEqualTo("The cart already has 19 x Tomato (1 kg). A cart holds at most 20 packs of one "
                + "product, so at most 1 more can be added.");
        assertThat(result.data()).isNull();         // no card
        assertThat(tools.prepareMyCartAdd("tomato", null, null, LOGGED_IN).message())
                .isEqualTo("The cart already has 20 x Tomato (1 kg), the most a cart can hold of one product.");
    }

    @Test
    void anUnclearAmountIsAQuestion() {
        when(customers.cart("customer-jwt")).thenReturn(TestData.emptyCart());

        var result = tools.prepareMyCartAdd("tomato", 1.5, "kg", LOGGED_IN);

        assertThat(result.status()).isEqualTo(Status.CHOOSE);
        assertThat(result.message()).isEqualTo("1.5 kg is not a whole number of packs of Tomato (1 kg a pack). "
                + "1 kg (1 pack) or 2 kg (2 packs)?");
        assertThat(result.data()).isNull();
    }

    @Test
    void notEnoughStockIsSaidBeforeTheCard() {
        // Amul Butter: 3 packs in stock; Pomegranate: none
        when(catalog.searchProducts(any(), any(), any(), anyBoolean(), anyInt()))
                .thenReturn(new PageData<>(List.of(TestData.amulButter()), 1), new PageData<>(List.of(TestData.amulButter()), 1),
                        new PageData<>(List.of(TestData.pomegranate()), 1));
        when(customers.cart("customer-jwt")).thenReturn(TestData.emptyCart(), TestData.cartWith(TestData.amulButter(), 2),
                TestData.emptyCart());

        var tooMany = tools.prepareMyCartAdd("amul butter", 4.0, "packs", LOGGED_IN);
        assertThat(tooMany.status()).isEqualTo(Status.REFUSED);
        assertThat(tooMany.message()).isEqualTo("Only 3 packs of Amul Butter (100 g) are left.");
        assertThat(tools.prepareMyCartAdd("amul butter", 2.0, "packs", LOGGED_IN).message())
                .isEqualTo("Only 3 packs of Amul Butter (100 g) are left, and the cart has 2 already.");
        assertThat(tools.prepareMyCartAdd("pomegranate", null, null, LOGGED_IN).message())
                .isEqualTo("Pomegranate (1 kg) is out of stock.");
    }

    @Test
    void aProductOfAnotherStoreIsRefused_withTheWayOut() {
        when(customers.cart("customer-jwt")).thenReturn(TestData.cartWith(TestData.tomato(), 1));     // Fresh Mart
        when(catalog.searchProducts(any(), any(), any(), anyBoolean(), anyInt()))
                .thenReturn(new PageData<>(List.of(TestData.pomegranate()), 1));                      // Green Basket

        var result = tools.prepareMyCartAdd("pomegranate", null, null, LOGGED_IN);

        assertThat(result.status()).isEqualTo(Status.REFUSED);
        assertThat(result.message()).isEqualTo("The cart has products from Fresh Mart, and Pomegranate is sold by Green Basket. "
                + "A cart can hold the products of one store only: place the order for this cart first, or empty the cart.");
    }

    @Test
    void anUnknownOrAMissingProductIsSaid() {
        when(customers.cart("customer-jwt")).thenReturn(TestData.emptyCart());
        when(catalog.searchProducts(any(), any(), any(), anyBoolean(), anyInt())).thenReturn(new PageData<>(List.of(), 0));

        var unknown = tools.prepareMyCartAdd("dragon fruit", null, null, LOGGED_IN);
        assertThat(unknown.status()).isEqualTo(Status.NOT_FOUND);
        assertThat(unknown.message()).isEqualTo("The shop has no product called \"dragon fruit\".");
        assertThat(tools.prepareMyCartAdd(" ", null, null, LOGGED_IN).status()).isEqualTo(Status.INVALID_INPUT);
    }

    @Test
    void aNewQuantityIsOnlyPrepared_theCustomersClickChangesIt() {
        when(customers.cart("customer-jwt")).thenReturn(TestData.cartWith(TestData.tomato(), 3));

        var result = tools.prepareMyCartQuantity("tomatoes", 1.0, "kg", LOGGED_IN);

        assertThat(result.status()).isEqualTo(Status.OK);
        assertThat(result.message()).startsWith("Change prepared; NOTHING is changed yet.").endsWith("change Tomato (1 kg) from 3 to 1.");
        CartQuantityPreview preview = (CartQuantityPreview) result.data();
        assertThat(preview.items()).singleElement().satisfies(line -> assertThat(line.quantity()).isEqualTo(3));     // as it is now
        assertThat(preview.confirm().productId()).isEqualTo(1L);
        assertThat(preview.confirm().quantity()).isEqualTo(1);
    }

    @Test
    void theSameQuantityAnUnclearAmountAnEmptyCartOrZeroPrepareNothing() {
        when(customers.cart("customer-jwt")).thenReturn(TestData.cartWith(TestData.tomato(), 2), TestData.cartWith(TestData.tomato(), 2),
                TestData.emptyCart());

        var same = tools.prepareMyCartQuantity("tomato", 2.0, null, LOGGED_IN);
        assertThat(same.message()).isEqualTo("The cart already has 2 x Tomato (1 kg); nothing to change.");
        assertThat(same.data()).isNull();       // no card
        assertThat(tools.prepareMyCartQuantity("tomato", 1.5, "kg", LOGGED_IN).status()).isEqualTo(Status.CHOOSE);
        assertThat(tools.prepareMyCartQuantity("tomato", 2.0, null, LOGGED_IN).message()).isEqualTo("The cart is empty.");
        assertThat(tools.prepareMyCartQuantity("tomato", 0.0, null, LOGGED_IN).message())
                .isEqualTo("To take a product out of the cart, use prepareMyCartRemoval.");
    }

    @Test
    void emptyingTheCartIsOnlyPrepared_withTheWholeCart() {
        when(customers.cart("customer-jwt")).thenReturn(TestData.cartWith(TestData.tomato(), 2), TestData.emptyCart());

        var result = tools.prepareMyCartClear(LOGGED_IN);

        assertThat(result.status()).isEqualTo(Status.OK);
        assertThat(result.message()).startsWith("Emptying prepared; NOTHING is removed yet.").endsWith("empty the cart (1 product).");
        CartSummary cart = result.data();
        assertThat(cart.store()).isEqualTo("Fresh Mart");
        assertThat(cart.items()).singleElement().satisfies(line -> assertThat(line.name()).isEqualTo("Tomato"));
        assertThat(cart.payable()).isEqualTo("₹102");
        // an empty cart: nothing to empty, no card
        var empty = tools.prepareMyCartClear(LOGGED_IN);
        assertThat(empty.status()).isEqualTo(Status.NOT_FOUND);
        assertThat(empty.message()).isEqualTo("The cart is empty already, so there is nothing to remove.");
    }

    @Test
    void aRemovalIsOnlyPrepared_theCustomersClickRemoves() {
        when(customers.cart("customer-jwt")).thenReturn(TestData.cartWith(TestData.tomato(), 2));

        var result = tools.prepareMyCartRemoval("tomatoes", LOGGED_IN);

        assertThat(result.status()).isEqualTo(Status.OK);
        assertThat(result.message()).startsWith("Removal prepared; NOTHING is removed yet.").contains("Tomato (1 kg)");
        CartRemovalPreview preview = (CartRemovalPreview) result.data();
        assertThat(preview.items()).singleElement().satisfies(line -> {
            assertThat(line.name()).isEqualTo("Tomato");
            assertThat(line.quantity()).isEqualTo(2);
        });
        assertThat(preview.confirm().productId()).isEqualTo(1L);
    }

    @Test
    void anUnclearProductIsAskedAboutAndNothingIsRemoved() {
        when(customers.cart("customer-jwt")).thenReturn(TestData.cartWith(1L,
                List.of(TestData.line(TestData.amulButter(), 1), TestData.line(TestData.amulButter500(), 1))));

        var result = tools.prepareMyCartRemoval("butter", LOGGED_IN);

        assertThat(result.status()).isEqualTo(Status.CHOOSE);
        assertThat(((CartChange) result.data()).options()).hasSize(2);
        // a pack size the customer named picks one
        assertThat(((CartRemovalPreview) tools.prepareMyCartRemoval("amul butter 500 g", LOGGED_IN).data()).confirm().productId())
                .isEqualTo(TestData.amulButter500().id());
    }

    @Test
    void aProductThatIsNotInTheCart_orAnEmptyCart_isSaid() {
        when(customers.cart("customer-jwt")).thenReturn(TestData.cartWith(TestData.tomato(), 2), TestData.emptyCart());

        var notThere = tools.prepareMyCartRemoval("harpic", LOGGED_IN);
        assertThat(notThere.status()).isEqualTo(Status.NOT_FOUND);
        assertThat(notThere.message()).startsWith("The cart has no product called \"harpic\".");
        assertThat(tools.prepareMyCartRemoval("tomato", LOGGED_IN).message()).isEqualTo("The cart is empty.");
        assertThat(tools.prepareMyCartRemoval(" ", LOGGED_IN).status()).isEqualTo(Status.INVALID_INPUT);
    }
}
