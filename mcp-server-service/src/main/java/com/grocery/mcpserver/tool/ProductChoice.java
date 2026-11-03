package com.grocery.mcpserver.tool;

import com.grocery.mcpserver.client.CatalogClient;
import com.grocery.mcpserver.client.ShopInfoClient;
import com.grocery.mcpserver.client.dto.CartData;
import com.grocery.mcpserver.client.dto.ProductData;
import com.grocery.mcpserver.tool.result.CartChange;
import com.grocery.mcpserver.tool.result.ProductSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Which product a cart tool means, decided here in code. The model passes only the customer's words ("tomatoes",
 * "amul butter 500 g", "tomato from green basket") or a productId; it never picks a product by guessing.
 *
 * Adding a product of the catalog:
 * - a product counts only when its name is just the customer's words, singular or plural alike, and brand words may
 *   be left out: "tomatoes" is Tomato and "butter" is Amul Butter, but "milk" is not Amul Taaza Toned Milk;
 * - a pack size or a store in the words narrows it down ("amul butter 500 g", "tomato from green basket");
 * - with a cart, only the cart's store counts: a cart holds the products of one store;
 * - exactly one such product: that one. Otherwise the answer is CHOOSE with the products that have all the customer's
 *   words (at most 5): the model asks the customer, whose answer names the product so that the next call finds exactly
 *   one;
 * - nothing fits the words as they are written: the same steps again, now allowing a small spelling mistake in each
 *   word (Spelling: "tomoto" is Tomato, "orgnic carrot" is Organic Carrot), among the search box's suggestions (which
 *   find such names). Only a product of the catalog is ever chosen or offered. Without such products: NOT_FOUND.
 *
 * Changing or removing: the words are matched against the cart's own lines ("remove the butter").
 */
@Component
@RequiredArgsConstructor
class ProductChoice {

    static final int MAX_OPTIONS = 5;
    /** More than are shown: the product called exactly that is not always the best-scored search result */
    private static final int SEARCH_RESULTS = 10;
    /** The most suggestions product-read-service gives */
    private static final int SUGGESTIONS = 10;
    private static final Pattern PRODUCT_ID = Pattern.compile("[1-9][0-9]{0,11}");
    /** A pack size in a product's name or the customer's words: "500 g", "1kg", "(6 pcs)" */
    private static final Pattern SIZE = Pattern.compile("\\(?(\\d+(?:\\.\\d+)?)\\s*(\\p{L}+)\\.?\\)?");
    /** Words that are not part of a product's name */
    private static final Set<String> FILLERS = Set.of("a", "an", "the", "of", "some", "from", "my", "more", "please");
    private static final String NO_NAME = "Give the product's name as the customer said it, e.g. \"tomatoes\" or \"amul butter\".";
    private static final String ASK_WHICH =
            " Ask the customer which one they mean (name, pack size or store) and change nothing until they answer.";

    private final CatalogClient catalog;
    private final ShopInfoClient shopInfo;

    /** The product (or cart line) the customer means, or the answer to give instead when that is not clear. */
    record Choice<T>(T chosen, ToolResult<CartChange> instead) {

        static <T> Choice<T> of(T chosen) {
            return new Choice<>(chosen, null);
        }

        static <T> Choice<T> instead(ToolResult<CartChange> answer) {
            return new Choice<>(null, answer);
        }
    }

    /** For prepareMyCartAdd: the product of the catalog, or the answer instead (NOT_FOUND, CHOOSE, REFUSED, INVALID_INPUT). */
    Choice<ProductData> toAdd(String wanted, CartData cart) {
        Map<Long, String> stores = StoreNames.of(shopInfo);
        Long cartStore = cart.hasItems() ? cart.storeId() : null;
        if (PRODUCT_ID.matcher(wanted).matches()) {
            Optional<ProductData> product = catalog.findProduct(Long.parseLong(wanted));
            if (product.isEmpty()) {
                return Choice.instead(ToolResult.notFound("The shop has no product with id " + wanted + "."));
            }
            return isOfStore(product.get(), cartStore)
                    ? Choice.of(product.get())
                    : Choice.instead(otherStore(List.of(product.get()), cartStore, stores));
        }

        Request request = Request.of(wanted, stores);
        if (request.words().isEmpty()) {
            return Choice.instead(ToolResult.invalidInput(NO_NAME));
        }
        List<ProductData> found = ofTheStoreSaid(request,
                catalog.searchProducts(request.searchText(), null, null, false, SEARCH_RESULTS).content());
        Choice<ProductData> choice = pick(wanted, request, found, false, cartStore, stores);
        if (choice == null) {
            // 3. nothing fits the words as they are written: perhaps a spelling mistake ("tomoto", "orgnic carrot").
            //    The same steps among the search box's suggestions, with each word allowed a small typo.
            List<ProductData> similar = ofTheStoreSaid(request, catalog.suggestProducts(request.text(), null, SUGGESTIONS));
            choice = pick(wanted, request, similar, true, cartStore, stores);
        }
        return choice != null ? choice : Choice.instead(ToolResult.notFound(noSuchProduct(wanted)));
    }

    /**
     * The first two steps of toAdd among these products. Null when none of them fits the customer's words.
     *
     * @param typos whether each word may have a small spelling mistake (Spelling): "tomoto" for Tomato
     */
    private static Choice<ProductData> pick(String wanted, Request request, List<ProductData> found, boolean typos,
                                            Long cartStore, Map<Long, String> stores) {
        // 1. the products called just that (of the pack size, if the customer said one); with a cart, of its store
        List<ProductData> named = narrowed(
                found.stream().filter(product -> request.isNameOf(product.name(), product.brand(), typos)).toList(),
                product -> request.isSize(product.unit(), product.unitQuantity()));
        List<ProductData> namedHere = named.stream().filter(product -> isOfStore(product, cartStore)).toList();
        if (namedHere.size() == 1) {
            return Choice.of(namedHere.getFirst());
        }
        if (namedHere.size() > 1) {
            String several = typos ? "Several products have a name like \"" + wanted + "\"."
                    : "Several products are called \"" + wanted + "\".";
            return Choice.instead(choose(several, namedHere, cartStore, stores));
        }
        if (!named.isEmpty()) {
            return Choice.instead(otherStore(named, cartStore, stores));    // sold, but not by the store of the cart
        }

        // 2. no product is called that: those with all the customer's words ("milk": Amul Taaza Toned Milk ...) for the
        //    customer to pick from; a product that shares only some words ("dragon fruit": Mixed Fruit Jam) is no option
        List<ProductData> withAllWords = narrowed(
                found.stream().filter(product -> request.hasAllWords(product.name(), product.brand(), typos)).toList(),
                product -> isOfStore(product, cartStore));
        if (withAllWords.isEmpty()) {
            return null;
        }
        String message = typos ? "No product is called \"" + wanted + "\"; these have similar names."
                : "No product is called exactly \"" + wanted + "\"; these have all its words.";
        return Choice.instead(choose(message, withAllWords, cartStore, stores));
    }

    /** "tomato from green basket": only that store's products */
    private static List<ProductData> ofTheStoreSaid(Request request, List<ProductData> products) {
        return products.stream().filter(product -> request.storeId() == null || request.storeId().equals(product.storeId())).toList();
    }

    private static String noSuchProduct(String wanted) {
        return "The shop has no product called \"" + wanted + "\".";
    }

    /** For prepareMyCartQuantity and prepareMyCartRemoval: the line of the cart, or the answer instead (NOT_FOUND, CHOOSE). */
    Choice<CartData.Item> inCart(String wanted, CartData cart) {
        List<CartData.Item> lines = cart.items();
        if (PRODUCT_ID.matcher(wanted).matches()) {
            long productId = Long.parseLong(wanted);
            return lines.stream()
                    .filter(line -> line.productId() != null && line.productId() == productId)
                    .findFirst()
                    .map(Choice::of)
                    .orElseGet(() -> Choice.instead(ToolResult.notFound("The cart has no product with id " + productId + ". "
                            + contents(lines))));
        }

        Map<Long, String> stores = StoreNames.of(shopInfo);
        Request request = Request.of(wanted, stores);
        if (request.words().isEmpty()) {
            return Choice.instead(ToolResult.invalidInput(NO_NAME));
        }
        Predicate<CartData.Item> ofSize = line -> request.isSize(line.unit(), line.unitQuantity());
        String storeName = cart.storeId() == null ? null : stores.get(cart.storeId());

        // in the customer's own cart, a single line with all their words is the one they mean ("remove the butter")
        List<CartData.Item> named = narrowed(lines.stream().filter(line -> request.isNameOf(line.productName(), null, false)).toList(), ofSize);
        List<CartData.Item> matching = !named.isEmpty() ? named
                : narrowed(lines.stream().filter(line -> request.hasAllWords(line.productName(), null, false)).toList(), ofSize);
        if (matching.size() == 1) {
            return Choice.of(matching.getFirst());
        }
        if (matching.size() > 1) {
            List<ProductSummary> options = matching.stream().limit(MAX_OPTIONS).map(line -> option(line, storeName)).toList();
            return Choice.instead(ToolResult.choose("The cart has several products that match \"" + wanted + "\"." + ASK_WHICH,
                    CartChange.choices(options)));
        }
        return Choice.instead(ToolResult.notFound("The cart has no product called \"" + wanted + "\". " + contents(lines)));
    }

    /** CHOOSE: the cart's store first, at most MAX_OPTIONS */
    private static ToolResult<CartChange> choose(String message, List<ProductData> products, Long cartStore, Map<Long, String> stores) {
        List<ProductSummary> options = products.stream()
                .sorted(Comparator.comparing((ProductData product) -> !isOfStore(product, cartStore)))
                .limit(MAX_OPTIONS)
                .map(product -> ProductSummary.from(product, stores.get(product.storeId())))
                .toList();
        return ToolResult.choose(message + ASK_WHICH, CartChange.choices(options));
    }

    /** The product is sold, but not by the store of the cart: say so, with the way out. */
    private static ToolResult<CartChange> otherStore(List<ProductData> products, Long cartStore, Map<Long, String> stores) {
        String sellers = products.stream()
                .map(product -> stores.getOrDefault(product.storeId(), "another store"))
                .distinct()
                .collect(Collectors.joining(" and "));
        return ToolResult.refused("The cart has products from " + stores.getOrDefault(cartStore, "another store") + ", and "
                + products.getFirst().name() + " is sold by " + sellers + ". A cart can hold the products of one store only: "
                + "place the order for this cart first, or empty the cart.");
    }

    /** A cart line as an option to choose from */
    private static ProductSummary option(CartData.Item line, String storeName) {
        return new ProductSummary(line.productId(), line.productName(), null, Formats.packSize(line.unit(), line.unitQuantity()),
                Formats.rupees(line.sellingPrice()), null, null, null, storeName, null, null);
    }

    /** "The cart has: Amul Butter (100 g), Tomato (1 kg)." */
    private static String contents(List<CartData.Item> lines) {
        return "The cart has: " + lines.stream()
                .map(line -> line.productName() + " (" + Formats.packSize(line.unit(), line.unitQuantity()) + ")")
                .collect(Collectors.joining(", ")) + ".";
    }

    private static boolean isOfStore(ProductData product, Long cartStore) {
        return cartStore == null || cartStore.equals(product.storeId());
    }

    /** Only those that pass the test (e.g. of the pack size the customer said), unless that leaves none. */
    private static <T> List<T> narrowed(List<T> items, Predicate<T> test) {
        List<T> passed = items.stream().filter(test).toList();
        return passed.isEmpty() ? items : passed;
    }

    /**
     * The customer's words, taken apart: the words of the product's name, and the pack size and the store if they said
     * one ("amul butter 500 g", "tomato from green basket").
     */
    private record Request(List<String> words, BigDecimal size, String sizeUnit, Long storeId) {

        static Request of(String wanted, Map<Long, String> stores) {
            String text = wanted.toLowerCase(Locale.ROOT);
            BigDecimal size = null;
            String sizeUnit = null;
            Matcher sizeInText = SIZE.matcher(text);
            while (sizeInText.find()) {
                if (PackCount.isUnit(sizeInText.group(2))) {
                    size = new BigDecimal(sizeInText.group(1));
                    sizeUnit = sizeInText.group(2);
                    text = text.substring(0, sizeInText.start()) + " " + text.substring(sizeInText.end());
                    break;
                }
            }
            List<String> words = new ArrayList<>(wordsOf(text));
            Long storeId = null;
            for (Map.Entry<Long, String> store : stores.entrySet()) {
                List<String> storeWords = wordsOf(store.getValue());
                if (!storeWords.isEmpty() && words.containsAll(storeWords)) {
                    storeId = store.getKey();
                    words.removeAll(storeWords);
                    break;
                }
            }
            words.removeAll(FILLERS);
            return new Request(List.copyOf(words), size, sizeUnit, storeId);
        }

        /** The words and their singular forms; the search finds products with any of them: "tomatoes tomatoe tomato" */
        String searchText() {
            return words.stream().flatMap(word -> forms(word).stream()).distinct().collect(Collectors.joining(" "));
        }

        /** The words as the customer wrote them, for the suggestions (they find small typos themselves): "orgnic carrot" */
        String text() {
            return String.join(" ", words);
        }

        /**
         * The name is just the customer's words (brand words may be left out): "butter" is Amul Butter; with typos also
         * "tomoto" is Tomato
         */
        boolean isNameOf(String name, String brand, boolean typos) {
            List<String> nameWords = nameWords(name);
            List<String> brandWords = wordsOf(brand);
            return allIn(words, concat(nameWords, brandWords), typos) && allIn(nameWords, concat(words, brandWords), typos);
        }

        /** Every word of the customer is in the name or the brand: "milk" is in Amul Taaza Toned Milk */
        boolean hasAllWords(String name, String brand, boolean typos) {
            return allIn(words, concat(nameWords(name), wordsOf(brand)), typos);
        }

        /** True without a pack size in the words, or when it is the product's pack size */
        boolean isSize(String unit, BigDecimal unitQuantity) {
            return size == null || PackCount.isPackSize(size, sizeUnit, unit, unitQuantity);
        }
    }

    /** "Amul Butter!" -> [amul, butter]: lower case, letters and digits only */
    private static List<String> wordsOf(String text) {
        if (text == null) {
            return List.of();
        }
        return Stream.of(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")).filter(word -> !word.isEmpty()).toList();
    }

    /** A product name's words without a pack size: "Coca Cola 2 L" -> [coca, cola] */
    private static List<String> nameWords(String name) {
        String withoutSize = SIZE.matcher(name.toLowerCase(Locale.ROOT))
                .replaceAll(size -> PackCount.isUnit(size.group(2)) ? " " : Matcher.quoteReplacement(size.group()));
        return wordsOf(withoutSize);
    }

    /** Every one of the words is among the others, singular or plural alike; with typos also spelled almost alike */
    private static boolean allIn(List<String> words, List<String> others, boolean typos) {
        return words.stream().allMatch(word -> others.stream()
                .anyMatch(other -> typos ? similarWord(word, other) : sameWord(word, other)));
    }

    /** "tomato" = "tomatoes", "berry" = "berries", "cookie" = "cookies" */
    static boolean sameWord(String one, String other) {
        Set<String> formsOfOne = forms(one);
        return forms(other).stream().anyMatch(formsOfOne::contains);
    }

    /** "tomoto" ~ "tomato", "tomotoes" ~ "tomato": one of their forms is the other's with a small typo (Spelling) */
    static boolean similarWord(String one, String other) {
        Set<String> formsOfOther = forms(other);
        return forms(one).stream().anyMatch(form -> formsOfOther.stream().anyMatch(otherForm -> Spelling.isClose(form, otherForm)));
    }

    /** The word and the singular forms it may have: "tomatoes" -> tomatoes, tomatoe, tomato; "berries" -> ... berry */
    static Set<String> forms(String word) {
        Set<String> forms = new LinkedHashSet<>();
        forms.add(word);
        if (word.length() > 3 && word.endsWith("s") && !word.endsWith("ss")) {
            forms.add(word.substring(0, word.length() - 1));                 // onions -> onion, cookies -> cookie
            if (word.endsWith("es")) {
                forms.add(word.substring(0, word.length() - 2));             // tomatoes -> tomato, peaches -> peach
            }
            if (word.endsWith("ies")) {
                forms.add(word.substring(0, word.length() - 3) + "y");       // berries -> berry
            }
        }
        return forms;
    }

    private static List<String> concat(List<String> one, List<String> other) {
        return Stream.concat(one.stream(), other.stream()).toList();
    }
}
