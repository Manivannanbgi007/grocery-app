package com.grocery.mcpserver.tool;

import com.grocery.mcpserver.client.CatalogClient;
import com.grocery.mcpserver.client.ShopInfoClient;
import com.grocery.mcpserver.client.dto.CartData;
import com.grocery.mcpserver.client.dto.PageData;
import com.grocery.mcpserver.client.dto.ProductData;
import com.grocery.mcpserver.tool.ToolResult.Status;
import com.grocery.mcpserver.tool.result.ProductSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Which product the customer means: decided in code from their words, never guessed. */
@ExtendWith(MockitoExtension.class)
class ProductChoiceTest {

    @Mock
    private CatalogClient catalog;
    @Mock
    private ShopInfoClient shopInfo;

    private ProductChoice choice;

    @BeforeEach
    void setUp() {
        choice = new ProductChoice(catalog, shopInfo);
        lenient().when(shopInfo.stores()).thenReturn(TestData.stores());
    }

    private void searchFinds(ProductData... products) {
        when(catalog.searchProducts(any(), any(), any(), anyBoolean(), anyInt())).thenReturn(new PageData<>(List.of(products), products.length));
    }

    @Test
    void aPluralFindsTheProductCalledThat() {
        // product-read-service does not know plurals: the search also asks for the singular forms
        when(catalog.searchProducts(eq("tomatoes tomatoe tomato"), any(), any(), anyBoolean(), anyInt()))
                .thenReturn(new PageData<>(List.of(TestData.tomato()), 1));

        assertThat(choice.toAdd("tomatoes", TestData.emptyCart()).chosen()).isEqualTo(TestData.tomato());
    }

    @Test
    void brandWordsMayBeLeftOut() {
        searchFinds(TestData.amulButter());

        assertThat(choice.toAdd("butter", TestData.emptyCart()).chosen()).isEqualTo(TestData.amulButter());
        assertThat(choice.toAdd("Amul Butter", TestData.emptyCart()).chosen()).isEqualTo(TestData.amulButter());
    }

    @Test
    void aProductThatIsMoreThanTheWordsIsAskedAbout() {
        searchFinds(TestData.amulTaazaMilk());

        var result = choice.toAdd("milk", TestData.emptyCart());

        assertThat(result.chosen()).isNull();
        assertThat(result.instead().status()).isEqualTo(Status.CHOOSE);
        assertThat(result.instead().message()).startsWith("No product is called exactly \"milk\"; these have all its words.")
                .contains("Ask the customer which one they mean");
        assertThat(result.instead().data().options()).extracting(ProductSummary::name).containsExactly("Amul Taaza Toned Milk");
    }

    @Test
    void twoPackSizesAreAskedAboutUnlessTheCustomerSaidOne() {
        searchFinds(TestData.amulButter(), TestData.amulButter500());

        var unclear = choice.toAdd("amul butter", TestData.emptyCart());
        assertThat(unclear.instead().status()).isEqualTo(Status.CHOOSE);
        assertThat(unclear.instead().data().options()).extracting(ProductSummary::packSize).containsExactly("100 g", "500 g");

        assertThat(choice.toAdd("amul butter 500 g", TestData.emptyCart()).chosen()).isEqualTo(TestData.amulButter500());
        assertThat(choice.toAdd("Amul Butter (100 g)", TestData.emptyCart()).chosen()).isEqualTo(TestData.amulButter());
    }

    @Test
    void theCartsStoreIsPreferredAndAStoreNameDecides() {
        searchFinds(TestData.greenBasketTomato(), TestData.tomato());

        CartData freshMartCart = TestData.cartWith(TestData.amulButter(), 1);
        assertThat(choice.toAdd("tomato", freshMartCart).chosen()).isEqualTo(TestData.tomato());

        var emptyCart = choice.toAdd("tomato", TestData.emptyCart());
        assertThat(emptyCart.instead().status()).isEqualTo(Status.CHOOSE);
        assertThat(emptyCart.instead().data().options()).extracting(ProductSummary::store).containsExactly("Green Basket", "Fresh Mart");

        assertThat(choice.toAdd("tomato from Green Basket", TestData.emptyCart()).chosen()).isEqualTo(TestData.greenBasketTomato());
    }

    @Test
    void aProductOfAnotherStoreThanTheCartsIsExplained() {
        searchFinds(TestData.amulButter());
        CartData greenBasketCart = TestData.cartWith(TestData.greenBasketTomato(), 1);

        var result = choice.toAdd("butter", greenBasketCart);

        assertThat(result.instead().status()).isEqualTo(Status.REFUSED);
        assertThat(result.instead().message()).isEqualTo("The cart has products from Green Basket, and Amul Butter is sold by "
                + "Fresh Mart. A cart can hold the products of one store only: place the order for this cart first, or empty the cart.");
    }

    @Test
    void aProductIdFromAnEarlierListIsUsedAsItIs() {
        when(catalog.findProduct(51L)).thenReturn(Optional.of(TestData.greenBasketTomato()));
        when(catalog.findProduct(99L)).thenReturn(Optional.empty());

        assertThat(choice.toAdd("51", TestData.emptyCart()).chosen()).isEqualTo(TestData.greenBasketTomato());
        assertThat(choice.toAdd("51", TestData.cartWith(TestData.tomato(), 1)).instead().status()).isEqualTo(Status.REFUSED);
        assertThat(choice.toAdd("99", TestData.emptyCart()).instead().message()).isEqualTo("The shop has no product with id 99.");
    }

    @Test
    void nothingFoundIsSaid() {
        searchFinds();

        var result = choice.toAdd("dragon fruit", TestData.emptyCart());

        assertThat(result.instead().status()).isEqualTo(Status.NOT_FOUND);
        assertThat(result.instead().message()).isEqualTo("The shop has no product called \"dragon fruit\".");
    }

    @Test
    void aProductThatSharesOnlySomeWordsIsNoOption() {
        searchFinds(TestData.tomato());     // the search finds any of the words: "cherry tomatoes" -> Tomato

        var result = choice.toAdd("cherry tomatoes", TestData.emptyCart());

        assertThat(result.instead().status()).isEqualTo(Status.NOT_FOUND);
        assertThat(result.instead().message()).isEqualTo("The shop has no product called \"cherry tomatoes\".");
    }

    @Test
    void aSpellingMistakeFindsTheOneProductCalledAlmostThat() {
        // the user's words, 2026-10-31: the search finds nothing for "tomoto", the search box's suggestions find Tomato
        // and the ketchup (which has more words than the customer said)
        searchFinds();
        when(catalog.suggestProducts(eq("tomoto"), any(), anyInt())).thenReturn(List.of(TestData.tomato(), TestData.tomatoKetchup()));

        assertThat(choice.toAdd("tomoto", TestData.emptyCart()).chosen()).isEqualTo(TestData.tomato());
    }

    @Test
    void aSpellingMistakeInOneOfSeveralWordsIsFoundToo() {
        searchFinds(TestData.amulTaazaMilk());     // "buter" finds nothing; "amul" finds the milk, which is no option
        when(catalog.suggestProducts(eq("amul buter"), any(), anyInt())).thenReturn(List.of(TestData.amulButter(), TestData.amulTaazaMilk()));

        assertThat(choice.toAdd("amul buter", TestData.emptyCart()).chosen()).isEqualTo(TestData.amulButter());
    }

    @Test
    void severalProductsWithASimilarNameAreAskedAbout() {
        searchFinds();
        when(catalog.suggestProducts(any(), any(), anyInt())).thenReturn(List.of(TestData.tomato(), TestData.greenBasketTomato()));

        var result = choice.toAdd("tomoto", TestData.emptyCart());

        assertThat(result.chosen()).isNull();
        assertThat(result.instead().status()).isEqualTo(Status.CHOOSE);
        assertThat(result.instead().message()).startsWith("Several products have a name like \"tomoto\".");
        assertThat(result.instead().data().options()).extracting(ProductSummary::store).containsExactly("Fresh Mart", "Green Basket");
    }

    @Test
    void aSimilarProductWithMoreWordsIsOnlyOffered() {
        searchFinds();
        when(catalog.suggestProducts(any(), any(), anyInt())).thenReturn(List.of(TestData.amulTaazaMilk()));

        var result = choice.toAdd("milc", TestData.emptyCart());

        assertThat(result.instead().status()).isEqualTo(Status.CHOOSE);
        assertThat(result.instead().message()).startsWith("No product is called \"milc\"; these have similar names.");
        assertThat(result.instead().data().options()).extracting(ProductSummary::name).containsExactly("Amul Taaza Toned Milk");
    }

    @Test
    void aSuggestionThatIsNotSpelledAlikeIsNoOption_nothingIsInvented() {
        searchFinds();
        when(catalog.suggestProducts(any(), any(), anyInt())).thenReturn(List.of(TestData.tomato()));

        var result = choice.toAdd("potatos", TestData.emptyCart());

        assertThat(result.instead().status()).isEqualTo(Status.NOT_FOUND);
        assertThat(result.instead().message()).isEqualTo("The shop has no product called \"potatos\".");
    }

    @Test
    void theWordsAsWrittenComeFirst_theSuggestionsAreNotAsked() {
        searchFinds(TestData.tomato());

        assertThat(choice.toAdd("tomato", TestData.emptyCart()).chosen()).isEqualTo(TestData.tomato());
        verify(catalog, never()).suggestProducts(any(), any(), anyInt());
    }

    @Test
    void inTheCartASingleProductWithTheWordsIsTheOne() {
        CartData cart = TestData.cartWith(1L, List.of(TestData.line(TestData.amulButter(), 1), TestData.line(TestData.tomato(), 2)));

        assertThat(choice.inCart("the butter", cart).chosen().productName()).isEqualTo("Amul Butter");
        assertThat(choice.inCart("tomatoes", cart).chosen().productName()).isEqualTo("Tomato");
        assertThat(choice.inCart("1", cart).chosen().productName()).isEqualTo("Tomato");

        var missing = choice.inCart("onions", cart);
        assertThat(missing.instead().status()).isEqualTo(Status.NOT_FOUND);
        assertThat(missing.instead().message())
                .isEqualTo("The cart has no product called \"onions\". The cart has: Amul Butter (100 g), Tomato (1 kg).");
    }

    @Test
    void inTheCartTwoPacksOfTheSameNameAreAskedAbout() {
        CartData cart = TestData.cartWith(1L, List.of(TestData.line(TestData.amulButter(), 1), TestData.line(TestData.amulButter500(), 1)));

        var unclear = choice.inCart("butter", cart);
        assertThat(unclear.instead().status()).isEqualTo(Status.CHOOSE);
        assertThat(unclear.instead().data().options()).extracting(ProductSummary::packSize).containsExactly("100 g", "500 g");

        assertThat(choice.inCart("butter 500g", cart).chosen().productId()).isEqualTo(8L);
    }

    @Test
    void singularAndPluralAreTheSameWord() {
        assertThat(ProductChoice.sameWord("tomatoes", "tomato")).isTrue();
        assertThat(ProductChoice.sameWord("berries", "berry")).isTrue();
        assertThat(ProductChoice.sameWord("cookies", "cookie")).isTrue();
        assertThat(ProductChoice.sameWord("peaches", "peach")).isTrue();
        assertThat(ProductChoice.sameWord("glass", "glasses")).isTrue();
        assertThat(ProductChoice.sameWord("milk", "silk")).isFalse();
    }
}
