package com.grocery.mcpserver.tool;

import com.grocery.mcpserver.client.CatalogClient;
import com.grocery.mcpserver.client.ShopInfoClient;
import com.grocery.mcpserver.client.dto.CategoryData;
import com.grocery.mcpserver.client.dto.PageData;
import com.grocery.mcpserver.client.dto.ProductData;
import com.grocery.mcpserver.client.dto.StoreData;
import com.grocery.mcpserver.exception.ExternalServiceException;
import com.grocery.mcpserver.tool.result.CategoryInfo;
import com.grocery.mcpserver.tool.result.ProductDetails;
import com.grocery.mcpserver.tool.result.ProductSummary;
import com.grocery.mcpserver.tool.result.ReviewOverview;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The catalog tools: search products, one product's details, its reviews, the categories.
 *
 * The descriptions are written for the model: what the tool does and WHEN to use it. Every tool checks its arguments,
 * reads live data through the API gateway and answers a {@link ToolResult}. Nothing here changes any data.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogTools {

    /** Few, short results: easier for a small model, and enough to answer a question. */
    static final int SEARCH_RESULTS = 5;
    static final int LATEST_REVIEWS = 3;
    private static final double MAX_PRICE_LIMIT = 100_000;
    private static final Pattern PRODUCT_ID = Pattern.compile("[1-9][0-9]{0,11}");
    private static final String PRODUCT_PARAMETER =
            "The product's name as the customer said it, e.g. \"amul butter\", or its productId from searchProducts";
    private static final String PRODUCT_MISSING = "Give the product's name (e.g. \"amul butter\") or its productId.";

    private final CatalogClient catalog;
    private final ShopInfoClient shopInfo;
    private final ToolRunner runner;

    @McpTool(name = "searchProducts",
            description = """
                    Search the products of the Fresh Grocery shop by name, brand or kind (for example "amul butter", \
                    "tomato", "green tea"), optionally only in one category, below a price, or only products in stock. \
                    Use it for EVERY question about whether the shop sells something, and about a product's price, discount, \
                    stock or store. Returns at most 5 best matches; prices are in rupees and include GST.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ToolResult<List<ProductSummary>> searchProducts(
            @McpToolParam(description = "Words to search for: a product name, brand or kind, e.g. \"amul butter\". "
                    + "May be left out when a category is given.", required = false) String query,
            @McpToolParam(description = "Only products of this category, e.g. \"Fruits\" (see listCategories)", required = false) String category,
            @McpToolParam(description = "Only products that cost at most this many rupees, e.g. 100", required = false) Double maxPrice,
            @McpToolParam(description = "true = only products that can be bought now", required = false) Boolean inStockOnly) {
        return runner.run("searchProducts", () -> {
            String text = ToolInputs.clean(query);
            String categoryName = ToolInputs.clean(category);
            if (text == null && categoryName == null) {
                return ToolResult.invalidInput("Give a search text (query), a category, or both.");
            }
            if (text != null && !ToolInputs.isUsableText(text)) {
                return ToolResult.invalidInput("The search text must have letters or digits and at most "
                        + ToolInputs.MAX_TEXT_LENGTH + " characters.");
            }
            if (maxPrice != null && (maxPrice <= 0 || maxPrice > MAX_PRICE_LIMIT)) {
                return ToolResult.invalidInput("maxPrice must be an amount in rupees between 1 and 100000.");
            }

            Long categoryId = null;
            if (categoryName != null) {
                Optional<CategoryData> found = findCategory(categoryName);
                if (found.isEmpty()) {
                    return ToolResult.notFound("The shop has no category called \"" + categoryName + "\". Its categories are: "
                            + categoryNames() + ".");
                }
                categoryId = found.get().id();
            }

            PageData<ProductData> page = catalog.searchProducts(text, categoryId,
                    maxPrice == null ? null : BigDecimal.valueOf(maxPrice), Boolean.TRUE.equals(inStockOnly), SEARCH_RESULTS);
            if (page.content().isEmpty() && text != null) {
                // Perhaps a spelling mistake ("tomoto"): the search box's suggestions find names with a small typo
                List<ProductData> similar = catalog.suggestProducts(text, categoryId, SEARCH_RESULTS).stream()
                        .filter(product -> maxPrice == null || product.sellingPrice().doubleValue() <= maxPrice)
                        .filter(product -> !Boolean.TRUE.equals(inStockOnly) || product.inStock())
                        .toList();
                if (!similar.isEmpty()) {
                    Map<Long, String> storeNames = storeNames();
                    return ToolResult.ok("No product is called \"" + text + "\", but these have a similar name. Ask the "
                            + "customer whether they mean one of them.", similar.stream()
                            .map(product -> ProductSummary.from(product, storeNames.get(product.storeId())))
                            .toList());
                }
            }
            if (page.content().isEmpty()) {
                return ToolResult.notFound(text != null
                        ? "No product of the shop matches \"" + text + "\"" + (categoryName != null ? " in " + categoryName : "") + "."
                        : "The category " + categoryName + " has no products that match.");
            }

            Map<Long, String> storeNames = storeNames();
            List<ProductSummary> products = page.content().stream()
                    .map(product -> ProductSummary.from(product, storeNames.get(product.storeId())))
                    .toList();
            String message = page.totalElements() > products.size()
                    ? "Found " + page.totalElements() + " products; these are the " + products.size() + " best matches."
                    : "Found " + products.size() + (products.size() == 1 ? " product." : " products.");
            return ToolResult.ok(message, products);
        });
    }

    // getProductDetails and getProductReviews take a product NAME or an id: in a follow-up question ("is it in stock?",
    // "what do customers say about it?") a small model reliably repeats the name from the conversation, but it does
    // not remember ids (only the texts of earlier answers are kept) and would guess one.

    @McpTool(name = "getProductDetails",
            description = """
                    All details of ONE product: description, pack size, price, discount, stock, store and rating. \
                    Use it for questions about one product, also about a product mentioned earlier in the \
                    conversation ("is it in stock?", "how big is the pack?").""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ToolResult<ProductDetails> getProductDetails(
            @McpToolParam(description = PRODUCT_PARAMETER) String product) {
        return runner.run("getProductDetails", () -> {
            String wanted = ToolInputs.clean(product);
            if (wanted == null || !ToolInputs.isUsableText(wanted)) {
                return ToolResult.invalidInput(PRODUCT_MISSING);
            }
            return findProduct(wanted)
                    .map(found -> ToolResult.ok("Details of " + found.name() + ".",
                            ProductDetails.from(found, storeNames().get(found.storeId()))))
                    .orElseGet(() -> ToolResult.notFound(noSuchProduct(wanted)));
        });
    }

    @McpTool(name = "getProductReviews",
            description = """
                    The rating and the newest customer reviews of ONE product (what customers say about it), \
                    also of a product mentioned earlier in the conversation.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ToolResult<ReviewOverview> getProductReviews(
            @McpToolParam(description = PRODUCT_PARAMETER) String product) {
        return runner.run("getProductReviews", () -> {
            String wanted = ToolInputs.clean(product);
            if (wanted == null || !ToolInputs.isUsableText(wanted)) {
                return ToolResult.invalidInput(PRODUCT_MISSING);
            }
            Optional<ProductData> found = findProduct(wanted);
            if (found.isEmpty()) {
                return ToolResult.notFound(noSuchProduct(wanted));
            }
            var reviews = catalog.latestReviews(found.get().id(), LATEST_REVIEWS).content();
            String message = reviews.isEmpty()
                    ? found.get().name() + " has no reviews yet."
                    : "The newest reviews of " + found.get().name() + ", newest first.";
            return ToolResult.ok(message, ReviewOverview.from(found.get(), reviews));
        });
    }

    @McpTool(name = "listCategories",
            description = """
                    The product categories of the shop with a short description. Use it for "what do you sell?" \
                    and to find the right category name for searchProducts.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ToolResult<List<CategoryInfo>> listCategories() {
        return runner.run("listCategories", () -> {
            List<CategoryInfo> categories = catalog.categories().stream()
                    .map(category -> new CategoryInfo(category.name(), category.description()))
                    .toList();
            return ToolResult.ok("The shop has " + categories.size() + " categories.", categories);
        });
    }

    /** The product the model meant: a productId ("7") from searchProducts, or else the best search match for a name. */
    private Optional<ProductData> findProduct(String product) {
        if (PRODUCT_ID.matcher(product).matches()) {
            return catalog.findProduct(Long.parseLong(product));
        }
        return catalog.searchProducts(product, null, null, false, 1).content().stream().findFirst();
    }

    private static String noSuchProduct(String product) {
        return PRODUCT_ID.matcher(product).matches()
                ? "The shop has no product with id " + product + "."
                : "No product of the shop matches \"" + product + "\".";
    }

    /** The category the model meant: an exact name or slug first, then a loose match ("fruit" -> "Fruits"). */
    private Optional<CategoryData> findCategory(String name) {
        List<CategoryData> categories = catalog.categories();
        return categories.stream()
                .filter(category -> category.name().equalsIgnoreCase(name) || category.slug().equalsIgnoreCase(name))
                .findFirst()
                .or(() -> categories.stream().filter(category -> ToolInputs.looselyMatches(name, category.name())).findFirst());
    }

    private String categoryNames() {
        return catalog.categories().stream().map(CategoryData::name).collect(Collectors.joining(", "));
    }

    /** Store id -> name, for "By Fresh Mart". Without the store list the products are still shown, just without store names. */
    private Map<Long, String> storeNames() {
        try {
            return shopInfo.stores().stream().collect(Collectors.toMap(StoreData::id, StoreData::name));
        } catch (ExternalServiceException ex) {
            log.warn("Store names not available: {}", ex.getMessage());
            return Map.of();
        }
    }
}
