package com.grocery.mcpserver.tool;

import com.grocery.mcpserver.client.CatalogClient;
import com.grocery.mcpserver.client.ShopInfoClient;
import com.grocery.mcpserver.client.dto.PageData;
import com.grocery.mcpserver.client.dto.ReviewData;
import com.grocery.mcpserver.exception.ExternalServiceException;
import com.grocery.mcpserver.tool.ToolResult.Status;
import com.grocery.mcpserver.tool.result.ProductSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogToolsTest {

    @Mock
    private CatalogClient catalog;
    @Mock
    private ShopInfoClient shopInfo;

    private CatalogTools tools;

    @BeforeEach
    void setUp() {
        tools = new CatalogTools(catalog, shopInfo, new ToolRunner());
        lenient().when(shopInfo.stores()).thenReturn(TestData.stores());
        lenient().when(catalog.categories()).thenReturn(TestData.categories());
    }

    @Test
    void searchReturnsShortProductsWithFormattedPricesAndStoreNames() {
        when(catalog.searchProducts("amul butter", null, null, false, CatalogTools.SEARCH_RESULTS))
                .thenReturn(new PageData<>(List.of(TestData.amulButter()), 1));

        ToolResult<List<ProductSummary>> result = tools.searchProducts("  amul butter ", null, null, null);

        assertThat(result.status()).isEqualTo(Status.OK);
        assertThat(result.message()).isEqualTo("Found 1 product.");
        ProductSummary butter = result.data().getFirst();
        assertThat(butter.price()).isEqualTo("₹56");
        assertThat(butter.priceBeforeDiscount()).isNull();
        assertThat(butter.stock()).isEqualTo("Only 3 left");
        assertThat(butter.store()).isEqualTo("Fresh Mart");
        assertThat(butter.rating()).isEqualTo("4.5 out of 5 (2 reviews)");
    }

    @Test
    void searchShowsTheDiscountAndSaysWhenThereAreMoreMatches() {
        when(catalog.searchProducts("tomato", null, null, false, CatalogTools.SEARCH_RESULTS))
                .thenReturn(new PageData<>(List.of(TestData.tomato()), 12));

        ToolResult<List<ProductSummary>> result = tools.searchProducts("tomato", null, null, null);

        assertThat(result.message()).isEqualTo("Found 12 products; these are the 1 best matches.");
        ProductSummary tomato = result.data().getFirst();
        assertThat(tomato.price()).isEqualTo("₹36");
        assertThat(tomato.priceBeforeDiscount()).isEqualTo("₹40");
        assertThat(tomato.discount()).isEqualTo("10% off");
        assertThat(tomato.rating()).isEqualTo("No reviews yet");
    }

    @Test
    void searchFindsTheCategoryLooselyAndPassesTheFilters() {
        when(catalog.searchProducts(isNull(), eq(2L), eq(new BigDecimal("100.0")), eq(true), anyInt()))
                .thenReturn(new PageData<>(List.of(TestData.pomegranate()), 1));

        ToolResult<List<ProductSummary>> result = tools.searchProducts(null, "fruit", 100.0, true);

        assertThat(result.status()).isEqualTo(Status.OK);
        assertThat(result.data().getFirst().stock()).isEqualTo("Out of stock");
        assertThat(result.data().getFirst().store()).isEqualTo("Green Basket");
    }

    @Test
    void searchSaysNotFoundInsteadOfGuessing() {
        when(catalog.searchProducts(eq("iphone 15"), any(), any(), anyBoolean(), anyInt())).thenReturn(new PageData<>(List.of(), 0));

        ToolResult<List<ProductSummary>> result = tools.searchProducts("iphone 15", null, null, null);

        assertThat(result.status()).isEqualTo(Status.NOT_FOUND);
        assertThat(result.message()).isEqualTo("No product of the shop matches \"iphone 15\".");
        assertThat(result.data()).isNull();
    }

    @Test
    void searchWithASpellingMistakeOffersTheProductsWithASimilarName() {
        // "what is the price of tomoto?": the search finds nothing, the search box's suggestions find Tomato
        when(catalog.searchProducts(eq("tomoto"), any(), any(), anyBoolean(), anyInt())).thenReturn(new PageData<>(List.of(), 0));
        when(catalog.suggestProducts("tomoto", null, CatalogTools.SEARCH_RESULTS)).thenReturn(List.of(TestData.tomato()));

        ToolResult<List<ProductSummary>> result = tools.searchProducts("tomoto", null, null, null);

        assertThat(result.status()).isEqualTo(Status.OK);
        assertThat(result.message()).isEqualTo("No product is called \"tomoto\", but these have a similar name. Ask the "
                + "customer whether they mean one of them.");
        assertThat(result.data()).extracting(ProductSummary::name).containsExactly("Tomato");
    }

    @Test
    void theSimilarNamesKeepThePriceLimit() {
        when(catalog.searchProducts(eq("tomoto"), any(), any(), anyBoolean(), anyInt())).thenReturn(new PageData<>(List.of(), 0));
        when(catalog.suggestProducts("tomoto", null, CatalogTools.SEARCH_RESULTS)).thenReturn(List.of(TestData.tomato()));

        // Tomato costs ₹36
        assertThat(tools.searchProducts("tomoto", null, 30.0, null).status()).isEqualTo(Status.NOT_FOUND);
    }

    @Test
    void searchNamesTheCategoriesWhenTheCategoryDoesNotExist() {
        ToolResult<List<ProductSummary>> result = tools.searchProducts("milk", "Electronics", null, null);

        assertThat(result.status()).isEqualTo(Status.NOT_FOUND);
        assertThat(result.message()).contains("no category called \"Electronics\"").contains("Vegetables, Fruits, Dairy & Eggs");
    }

    @Test
    void searchChecksItsArguments() {
        assertThat(tools.searchProducts(null, null, null, null).status()).isEqualTo(Status.INVALID_INPUT);
        assertThat(tools.searchProducts("   ", " ", null, null).status()).isEqualTo(Status.INVALID_INPUT);
        assertThat(tools.searchProducts("!!!", null, null, null).status()).isEqualTo(Status.INVALID_INPUT);
        assertThat(tools.searchProducts("a".repeat(101), null, null, null).status()).isEqualTo(Status.INVALID_INPUT);
        assertThat(tools.searchProducts("milk", null, -5.0, null).status()).isEqualTo(Status.INVALID_INPUT);
        verifyNoInteractions(catalog);
    }

    @Test
    void searchSaysUnavailableWhenTheGatewayFails() {
        when(catalog.searchProducts(any(), any(), any(), anyBoolean(), anyInt()))
                .thenThrow(new ExternalServiceException("The product catalog is not available right now. Please try again later.", null));

        ToolResult<List<ProductSummary>> result = tools.searchProducts("milk", null, null, null);

        assertThat(result.status()).isEqualTo(Status.SERVICE_UNAVAILABLE);
        assertThat(result.message()).isEqualTo("The product catalog is not available right now. Please try again later.");
    }

    @Test
    void searchStillWorksWithoutStoreNames() {
        when(shopInfo.stores()).thenThrow(new ExternalServiceException("The store list is not available right now.", null));
        when(catalog.searchProducts(any(), any(), any(), anyBoolean(), anyInt())).thenReturn(new PageData<>(List.of(TestData.tomato()), 1));

        ToolResult<List<ProductSummary>> result = tools.searchProducts("tomato", null, null, null);

        assertThat(result.status()).isEqualTo(Status.OK);
        assertThat(result.data().getFirst().store()).isNull();
    }

    @Test
    void productDetailsByIdIncludeThePriceNote() {
        when(catalog.findProduct(7L)).thenReturn(Optional.of(TestData.amulButter()));

        var result = tools.getProductDetails("7");

        assertThat(result.status()).isEqualTo(Status.OK);
        assertThat(result.message()).isEqualTo("Details of Amul Butter.");
        assertThat(result.data().description()).isEqualTo("Pasteurised salted butter");
        assertThat(result.data().priceNote()).isEqualTo("Price for 100 g, including GST");
    }

    @Test
    void productDetailsByNameUseTheBestSearchMatch() {
        when(catalog.searchProducts("amul butter", null, null, false, 1))
                .thenReturn(new PageData<>(List.of(TestData.amulButter()), 3));

        var result = tools.getProductDetails(" amul butter ");

        assertThat(result.status()).isEqualTo(Status.OK);
        assertThat(result.data().name()).isEqualTo("Amul Butter");
        assertThat(result.data().stock()).isEqualTo("Only 3 left");
    }

    @Test
    void productDetailsOfAnUnknownProduct() {
        when(catalog.findProduct(999L)).thenReturn(Optional.empty());
        when(catalog.searchProducts(eq("iphone"), any(), any(), anyBoolean(), anyInt())).thenReturn(new PageData<>(List.of(), 0));

        assertThat(tools.getProductDetails("999").message()).isEqualTo("The shop has no product with id 999.");
        assertThat(tools.getProductDetails("iphone").message()).isEqualTo("No product of the shop matches \"iphone\".");
        assertThat(tools.getProductDetails("iphone").status()).isEqualTo(Status.NOT_FOUND);
        assertThat(tools.getProductDetails(" ").status()).isEqualTo(Status.INVALID_INPUT);
        assertThat(tools.getProductDetails(null).status()).isEqualTo(Status.INVALID_INPUT);
    }

    @Test
    void reviewsGiveTheRatingAndTheNewestReviews() {
        when(catalog.searchProducts("amul butter", null, null, false, 1))
                .thenReturn(new PageData<>(List.of(TestData.amulButter()), 1));
        when(catalog.latestReviews(7L, CatalogTools.LATEST_REVIEWS)).thenReturn(new PageData<>(
                List.of(new ReviewData(5, "Fresh stock, long expiry date.", Instant.parse("2026-10-01T10:00:00Z"))), 2));

        var result = tools.getProductReviews("amul butter");

        assertThat(result.status()).isEqualTo(Status.OK);
        assertThat(result.message()).isEqualTo("The newest reviews of Amul Butter, newest first.");
        assertThat(result.data().rating()).isEqualTo("4.5 out of 5 (2 reviews)");
        assertThat(result.data().latestReviews()).singleElement().satisfies(review -> {
            assertThat(review.stars()).isEqualTo(5);
            assertThat(review.date()).isEqualTo("1 Oct 2026");
        });
    }

    @Test
    void categoriesAreListedWithTheirDescriptions() {
        var result = tools.listCategories();

        assertThat(result.message()).isEqualTo("The shop has 3 categories.");
        assertThat(result.data()).extracting("name").containsExactly("Vegetables", "Fruits", "Dairy & Eggs");
        verify(catalog).categories();
    }
}
